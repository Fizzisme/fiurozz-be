import {Injectable, NotFoundException, BadRequestException} from '@nestjs/common';
import {PrismaService} from "../prisma/prisma.service.js";
import {Prisma} from "../generated/prisma/client.js";
import {MAX_LIMIT} from "../common/constants/pagination.js";
import {UUID_RE} from "../common/constants/uuid.js";

interface ListEdgesQuery {
    limit: number;
    cursor?: string;
}

type FollowSummaryUser = Prisma.UserGetPayload<{ include: { profile: true } }>;

@Injectable()
export class FollowService {

    constructor(private readonly prisma: PrismaService) {
    }

    // follower_id/followee_id are @db.Uuid columns -- reject a malformed id
    // here with a clean 400 instead of letting Postgres throw a cast error.
    private assertValidId(id: string) {
        if (!UUID_RE.test(id)) {
            throw new BadRequestException('Invalid user id.');
        }
    }

    private async assertUserExists(id: string) {
        const user = await this.prisma.user.findFirst({
            where: { id, deletedAt: null },
            select: { id: true },
        });

        if (!user) {
            throw new NotFoundException('User not found.');
        }
    }

    // Idempotent: following someone twice is a no-op, not an error.
    // createMany + skipDuplicates relies on the Follow PK to detect the
    // duplicate via ON CONFLICT DO NOTHING, so it never throws and never
    // leaves the transaction in Postgres's aborted-transaction state --
    // unlike catching a unique-constraint error from a plain `create`.
    async follow(followerId: string, targetId: string) {
        this.assertValidId(targetId);

        if (followerId === targetId) {
            throw new BadRequestException('You cannot follow yourself.');
        }

        await this.assertUserExists(targetId);

        const stats = await this.prisma.$transaction(async (tx) => {
            const created = await tx.follow.createMany({
                data: [{ followerId, followeeId: targetId }],
                skipDuplicates: true,
            });

            // Already following -- stats already reflect the existing edge,
            // so just read them back rather than counting the edge twice.
            if (created.count === 0) {
                return tx.userStats.findUnique({ where: { userId: targetId } });
            }

            // upsert, not update: this may be the first follow either side
            // has ever been part of, so the stats row might not exist yet.
            await tx.userStats.upsert({
                where: { userId: followerId },
                create: { userId: followerId, followingCount: 1 },
                update: { followingCount: { increment: 1 } },
            });
            // Returned so the response carries the target's post-write
            // counts, letting the client reconcile an optimistic update
            // without a follow-up GET.
            return tx.userStats.upsert({
                where: { userId: targetId },
                create: { userId: targetId, followersCount: 1 },
                update: { followersCount: { increment: 1 } },
            });
        });

        return { data: { isFollowing: true, ...this.toCounts(stats) } };
    }

    // Idempotent: unfollowing someone you don't follow is a no-op, not a 404.
    async unfollow(followerId: string, targetId: string) {
        this.assertValidId(targetId);

        const stats = await this.prisma.$transaction(async (tx) => {
            const deleted = await tx.follow.deleteMany({
                where: { followerId, followeeId: targetId },
            });

            // Wasn't following -- counts unchanged, just read them back.
            if (deleted.count === 0) {
                return tx.userStats.findUnique({ where: { userId: targetId } });
            }

            // update, not upsert: the edge we just deleted only exists if
            // follow() ran before, which guarantees both stats rows exist.
            await tx.userStats.update({
                where: { userId: followerId },
                data: { followingCount: { decrement: 1 } },
            });
            return tx.userStats.update({
                where: { userId: targetId },
                data: { followersCount: { decrement: 1 } },
            });
        });

        return { data: { isFollowing: false, ...this.toCounts(stats) } };
    }

    // Target has no stats row only when it was never created (no follow has
    // ever touched it) -- that state is exactly zero on both counters.
    private toCounts(stats: { followersCount: number; followingCount: number } | null) {
        return {
            followersCount: stats?.followersCount ?? 0,
            followingCount: stats?.followingCount ?? 0,
        };
    }

    // "Which of these users does the viewer already follow?" -- one batch
    // lookup for a whole page of cards, instead of a query per card. Hits the
    // (follower_id, followee_id) PK prefix, so it needs no extra index.
    // Anonymous viewers short-circuit without touching the database.
    async loadFollowingSet(viewerId: string | undefined, targetIds: string[]): Promise<Set<string>> {
        if (!viewerId || targetIds.length === 0) {
            return new Set();
        }

        const edges = await this.prisma.follow.findMany({
            where: { followerId: viewerId, followeeId: { in: targetIds } },
            select: { followeeId: true },
        });

        return new Set(edges.map((edge) => edge.followeeId));
    }

    // Users following `targetId`.
    async getFollowers(targetId: string, query: ListEdgesQuery, viewerId?: string) {
        this.assertValidId(targetId);
        await this.assertUserExists(targetId);

        const take = Math.min(Math.max(query.limit, 1), MAX_LIMIT);
        const conditions: Prisma.FollowWhereInput[] = [
            { followeeId: targetId, follower: { deletedAt: null } },
        ];
        if (query.cursor) {
            conditions.push(this.buildCursorWhere(query.cursor, 'followerId'));
        }

        const edges = await this.prisma.follow.findMany({
            where: { AND: conditions },
            take: take + 1,
            orderBy: [{ createdAt: 'desc' }, { followerId: 'desc' }],
            include: { follower: { include: { profile: true } } },
        });

        const hasMore = edges.length > take;
        const items = hasMore ? edges.slice(0, take) : edges;
        const last = items[items.length - 1];

        // Built from `items`, not `edges` -- the extra lookahead row isn't
        // returned, so it shouldn't be queried for either.
        const following = await this.loadFollowingSet(viewerId, items.map((e) => e.followerId));

        return {
            data: {
                items: items
                    .filter((e) => e.follower.profile)
                    .map((e) => this.toSummary(e.follower, following.has(e.followerId))),
                nextCursor: hasMore ? this.encodeCursor(last.createdAt, last.followerId) : null,
                hasMore,
            },
        };
    }

    // Users `targetId` is following.
    async getFollowing(targetId: string, query: ListEdgesQuery, viewerId?: string) {
        this.assertValidId(targetId);
        await this.assertUserExists(targetId);

        const take = Math.min(Math.max(query.limit, 1), MAX_LIMIT);
        const conditions: Prisma.FollowWhereInput[] = [
            { followerId: targetId, followee: { deletedAt: null } },
        ];
        if (query.cursor) {
            conditions.push(this.buildCursorWhere(query.cursor, 'followeeId'));
        }

        const edges = await this.prisma.follow.findMany({
            where: { AND: conditions },
            take: take + 1,
            orderBy: [{ createdAt: 'desc' }, { followeeId: 'desc' }],
            include: { followee: { include: { profile: true } } },
        });

        const hasMore = edges.length > take;
        const items = hasMore ? edges.slice(0, take) : edges;
        const last = items[items.length - 1];

        const following = await this.loadFollowingSet(viewerId, items.map((e) => e.followeeId));

        return {
            data: {
                items: items
                    .filter((e) => e.followee.profile)
                    .map((e) => this.toSummary(e.followee, following.has(e.followeeId))),
                nextCursor: hasMore ? this.encodeCursor(last.createdAt, last.followeeId) : null,
                hasMore,
            },
        };
    }

    // Cursor = base64url({ v: <last edge's createdAt>, id: <last edge's
    // neighbor id> }). Always newest-first -- unlike getUsers, follow lists
    // don't take a `sort` param, so there's only one direction to encode.
    private encodeCursor(createdAt: Date, id: string): string {
        return Buffer.from(JSON.stringify({ v: createdAt.toISOString(), id })).toString('base64url');
    }

    private decodeCursor(cursor: string): { v: string; id: string } {
        try {
            const decoded = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8'));
            if (typeof decoded.v !== 'string' || typeof decoded.id !== 'string') throw new Error();
            return decoded;
        } catch {
            throw new BadRequestException('Invalid cursor.');
        }
    }

    private buildCursorWhere(cursor: string, idField: 'followerId' | 'followeeId'): Prisma.FollowWhereInput {
        const { v, id } = this.decodeCursor(cursor);
        const createdAt = new Date(v);

        if (idField === 'followerId') {
            return {
                OR: [
                    { createdAt: { lt: createdAt } },
                    { createdAt, followerId: { lt: id } },
                ],
            };
        }

        return {
            OR: [
                { createdAt: { lt: createdAt } },
                { createdAt, followeeId: { lt: id } },
            ],
        };
    }

    // Deliberately lighter than UserService#toPublicProfile (no skills,
    // no email/birthday) -- a follow list is a list of cards, not profiles.
    private toSummary(user: FollowSummaryUser, isFollowing: boolean) {
        const profile = user.profile!;

        return {
            id: user.id,
            displayName: profile.displayName,
            fullName: profile.fullName,
            avatarUrl: profile.avatarUrl,
            bio: profile.bio,
            occupation: profile.occupation,
            isFollowing,
        };
    }
}
