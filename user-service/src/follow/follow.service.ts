import {Injectable, NotFoundException, BadRequestException} from '@nestjs/common';
import {PrismaService} from "../prisma/prisma.service.js";
import {Prisma} from "../generated/prisma/client.js";

const MAX_LIMIT = 100;

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

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

        await this.prisma.$transaction(async (tx) => {
            const created = await tx.follow.createMany({
                data: [{ followerId, followeeId: targetId }],
                skipDuplicates: true,
            });

            // Already following -- stats already reflect the existing edge.
            if (created.count === 0) {
                return;
            }

            // upsert, not update: this may be the first follow either side
            // has ever been part of, so the stats row might not exist yet.
            await tx.userStats.upsert({
                where: { userId: followerId },
                create: { userId: followerId, followingCount: 1 },
                update: { followingCount: { increment: 1 } },
            });
            await tx.userStats.upsert({
                where: { userId: targetId },
                create: { userId: targetId, followersCount: 1 },
                update: { followersCount: { increment: 1 } },
            });
        });

        return { data: { following: true } };
    }

    // Idempotent: unfollowing someone you don't follow is a no-op, not a 404.
    async unfollow(followerId: string, targetId: string) {
        this.assertValidId(targetId);

        await this.prisma.$transaction(async (tx) => {
            const deleted = await tx.follow.deleteMany({
                where: { followerId, followeeId: targetId },
            });

            // Wasn't following -- nothing to reconcile.
            if (deleted.count === 0) {
                return;
            }

            // update, not upsert: the edge we just deleted only exists if
            // follow() ran before, which guarantees both stats rows exist.
            await tx.userStats.update({
                where: { userId: followerId },
                data: { followingCount: { decrement: 1 } },
            });
            await tx.userStats.update({
                where: { userId: targetId },
                data: { followersCount: { decrement: 1 } },
            });
        });

        return { data: { following: false } };
    }

    // Users following `targetId`.
    async getFollowers(targetId: string, query: ListEdgesQuery) {
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

        return {
            data: {
                items: items.filter((e) => e.follower.profile).map((e) => this.toSummary(e.follower)),
                nextCursor: hasMore ? this.encodeCursor(last.createdAt, last.followerId) : null,
                hasMore,
            },
        };
    }

    // Users `targetId` is following.
    async getFollowing(targetId: string, query: ListEdgesQuery) {
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

        return {
            data: {
                items: items.filter((e) => e.followee.profile).map((e) => this.toSummary(e.followee)),
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
    private toSummary(user: FollowSummaryUser) {
        const profile = user.profile!;

        return {
            id: user.id,
            displayName: profile.displayName,
            fullName: profile.fullName,
            avatarUrl: profile.avatarUrl,
            bio: profile.bio,
            occupation: profile.occupation,
        };
    }
}
