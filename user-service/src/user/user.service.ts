import { Injectable, NotFoundException, BadRequestException, ConflictException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service.js';
import { Prisma } from '../generated/prisma/client.js';
import { Occupation } from '../generated/prisma/enums.js';
import type { UpdateProfileDto } from './dto/update-profile.dto.js';
import { MAX_LIMIT } from '../common/constants/pagination.js';
import { UUID_RE } from '../common/constants/uuid.js';
import { FollowService } from '../follow/follow.service.js';
import { ObjectStorageService, type ProfileImageKind } from '../storage/object-storage.service.js';
import { OutboxEventService } from '../outboxEvent/outbox-event.service.js';

interface GetUsersQuery {
    limit: number;
    cursor?: string;
    occupation?: string[];
    skills?: string[];
    sort?: string;
}

type SortField = 'createdAt' | 'displayName' | 'followersCount';

interface SortSpec {
    field: SortField;
    direction: 'asc' | 'desc';
}

// stats is loaded for the followers_desc cursor *and* for the counts
// returned in every public profile.
type UserWithPublicProfile = Prisma.UserGetPayload<{
    include: { profile: true; settings: true; stats: true; skills: { include: { skill: true } } };
}>;

@Injectable()
export class UserService {
    static readonly MAX_AVATAR_BYTES = 5 * 1024 * 1024;
    static readonly MAX_COVER_BYTES = 10 * 1024 * 1024;

    constructor(
        private readonly prisma: PrismaService,
        private readonly followService: FollowService,
        private readonly objectStorage: ObjectStorageService,
        private readonly outboxEvent: OutboxEventService,
    ) {}

    // userId comes from the @UserId() decorator -- see its header-trust
    // comment for the X-User-Id / Gateway contract this relies on.
    async getMe(userId: string) {
        const user = await this.prisma.user.findUnique({
            where: { id: userId },
            include: {
                profile: true,
                settings: true,
                stats: true,
                links: {
                    orderBy: { order: 'asc' },
                },
            },
        });

        if (!user || !user.profile) {
            throw new NotFoundException('User profile not found.');
        }

        return {
            data: {
                id: user.id,
                email: user.profile.email,
                displayName: user.profile.displayName,
                fullName: user.profile.fullName,
                avatarUrl: user.profile.avatarUrl,
                avatarVariants: user.profile.avatarVariants,
                coverUrl: user.profile.coverUrl,
                coverVariants: user.profile.coverVariants,
                bio: user.profile.bio,
                occupation: user.profile.occupation,
                company: user.profile.company,
                location: user.profile.location,
                birthday: user.profile.birthday,
                website: user.profile.website,
                gender: user.profile.gender,
                language: user.profile.language,
                timezone: user.profile.timezone,
                settings: user.settings,
                links: user.links,
                followersCount: user.stats?.followersCount ?? 0,
                followingCount: user.stats?.followingCount ?? 0,
                createdAt: user.createdAt,
            },
        };
    }

    // Same X-User-Id trust as getMe -- a user can only ever update their
    // own profile via this route, never someone else's.
    async updateProfile(userId: string, dto: UpdateProfileDto) {
        const { skills, ...profileFields } = dto;

        try {
            await this.prisma.$transaction(async (tx) => {
                // Always checked, even if the body only touches `skills` --
                // gives a uniform 404 instead of leaning on whichever of the
                // two writes below happens to run.
                const current = await tx.userProfile.findUniqueOrThrow({ where: { userId } });

                // Spreading is safe for PATCH semantics: Prisma skips any
                // key whose value is `undefined` (fields the client didn't
                // send), only writing the ones actually present in the body.
                // Skipped entirely when empty -- an empty `data: {}` isn't
                // guaranteed to be a safe no-op across Prisma versions.
                if (Object.keys(profileFields).length > 0) {
                    const updated = await tx.userProfile.update({
                        where: { userId },
                        data: { ...profileFields },
                    });

                    // auth-service owns a copy of displayName (unique there
                    // too), so a real change is announced via the outbox in
                    // this same transaction -- the event is never lost, and
                    // is never sent for a rolled-back update.
                    if (profileFields.displayName !== undefined && profileFields.displayName !== current.displayName) {
                        await this.outboxEvent.create(
                            userId,
                            'user.profile.updated',
                            { userId, displayName: updated.displayName },
                            tx,
                        );
                    }
                }

                if (skills !== undefined) {
                    await this.syncSkills(tx, userId, skills);
                }
            });
        } catch (err) {
            if (err instanceof Prisma.PrismaClientKnownRequestError && err.code === 'P2025') {
                throw new NotFoundException('User profile not found.');
            }
            // displayName is the only unique column this route can change.
            if (
                err instanceof Prisma.PrismaClientKnownRequestError &&
                err.code === 'P2002' &&
                dto.displayName !== undefined
            ) {
                throw new ConflictException('Display name is already taken.');
            }
            throw err;
        }

        return this.getMe(userId);
    }

    uploadAvatar(userId: string, file?: Express.Multer.File) {
        return this.uploadProfileImage(userId, 'avatar', file, UserService.MAX_AVATAR_BYTES);
    }

    replaceAvatar(userId: string, file?: Express.Multer.File) {
        return this.uploadProfileImage(userId, 'avatar', file, UserService.MAX_AVATAR_BYTES);
    }

    async deleteAvatar(userId: string) {
        return this.deleteProfileImage(userId, 'avatar');
    }

    uploadCover(userId: string, file?: Express.Multer.File) {
        return this.uploadProfileImage(userId, 'cover', file, UserService.MAX_COVER_BYTES);
    }

    replaceCover(userId: string, file?: Express.Multer.File) {
        return this.uploadProfileImage(userId, 'cover', file, UserService.MAX_COVER_BYTES);
    }

    async deleteCover(userId: string) {
        return this.deleteProfileImage(userId, 'cover');
    }

    private async uploadProfileImage(
        userId: string,
        kind: ProfileImageKind,
        file: Express.Multer.File | undefined,
        maxBytes: number,
    ) {
        const profile = await this.prisma.userProfile.findUnique({
            where: { userId },
            select: { userId: true },
        });
        if (!profile) {
            throw new NotFoundException('User profile not found.');
        }

        // Upload first and only move the database pointer after S3 confirms
        // success. Objects are content-addressed and immutable, so a database
        // failure can leave only a harmless unreferenced blob for a future
        // grace-period GC; it can never leave a profile pointing at a missing
        // object. Old objects are deliberately retained because project cards
        // may hold historical avatar URLs.
        const stored = await this.objectStorage.uploadProfileImage(kind, file, maxBytes);
        const variants = stored.variants as unknown as Prisma.InputJsonValue;

        await this.prisma.$transaction(async (tx) => {
            await tx.userProfile.update({
                where: { userId },
                data:
                    kind === 'avatar'
                        ? { avatarUrl: stored.url, avatarObjectKey: stored.key, avatarVariants: variants }
                        : { coverUrl: stored.url, coverObjectKey: stored.key, coverVariants: variants },
            });

            // Other services keep a copy of the avatar URL; announce it in
            // the same transaction so the event is never lost or sent for a
            // rolled-back update.
            if (kind === 'avatar') {
                await this.outboxEvent.create(userId, 'user.avatar.updated', { userId, avatarUrl: stored.url }, tx);
            }
        });

        return {
            data:
                kind === 'avatar'
                    ? { avatarUrl: stored.url, avatarVariants: stored.variants, sha256: stored.sha256 }
                    : { coverUrl: stored.url, coverVariants: stored.variants, sha256: stored.sha256 },
        };
    }

    private async deleteProfileImage(userId: string, kind: ProfileImageKind) {
        const result = await this.prisma.$transaction(async (tx) => {
            const updated = await tx.userProfile.updateMany({
                where: { userId },
                data:
                    kind === 'avatar'
                        ? { avatarUrl: null, avatarObjectKey: null, avatarVariants: Prisma.DbNull }
                        : { coverUrl: null, coverObjectKey: null, coverVariants: Prisma.DbNull },
            });

            if (updated.count > 0 && kind === 'avatar') {
                await this.outboxEvent.create(userId, 'user.avatar.updated', { userId, avatarUrl: null }, tx);
            }

            return updated;
        });
        if (result.count === 0) {
            throw new NotFoundException('User profile not found.');
        }

        // Do not delete the content-addressed S3 object here. The same blob
        // may still be referenced elsewhere. A separate garbage collector
        // can remove unreferenced objects after a grace period.
        return {
            data:
                kind === 'avatar' ? { avatarUrl: null, avatarVariants: null } : { coverUrl: null, coverVariants: null },
        };
    }

    // Replaces the user's full skill list with `names` (not a diff/append).
    // Skill is a shared catalog (Skill.name is unique) -- find-or-create
    // each one, then relink UserSkill to exactly this set.
    private async syncSkills(tx: Prisma.TransactionClient, userId: string, names: string[]) {
        const normalized = [...new Set(names.map((n) => n.trim().toLowerCase()).filter(Boolean))];

        const skills = await Promise.all(
            normalized.map((name) =>
                tx.skill.upsert({
                    where: { name },
                    create: { name },
                    update: {},
                }),
            ),
        );

        await tx.userSkill.deleteMany({
            where: { userId, skillId: { notIn: skills.map((s) => s.id) } },
        });

        await tx.userSkill.createMany({
            data: skills.map((s) => ({ userId, skillId: s.id })),
            skipDuplicates: true,
        });
    }

    // viewerId is optional -- this route serves anonymous visitors too, and is
    // only used to stamp isFollowing onto each card.
    async getUsers(query: GetUsersQuery, viewerId?: string) {
        const take = Math.min(Math.max(query.limit, 1), MAX_LIMIT);
        const sortSpec = this.parseSort(query.sort);

        const conditions: Prisma.UserWhereInput[] = [{ deletedAt: null }];

        if (query.occupation?.length) {
            conditions.push({ profile: { occupation: { in: this.parseOccupationFilter(query.occupation) } } });
        }
        if (query.skills?.length) {
            conditions.push({ skills: { some: { skill: { name: { in: query.skills, mode: 'insensitive' } } } } });
        }
        if (query.cursor) {
            conditions.push(this.buildCursorWhere(sortSpec, query.cursor));
        }

        const users = await this.prisma.user.findMany({
            where: { AND: conditions },
            take: take + 1,
            orderBy: this.buildOrderBy(sortSpec),
            include: {
                profile: true,
                settings: true,
                stats: true,
                skills: { include: { skill: true } },
            },
        });

        const hasMore = users.length > take;
        const items = hasMore ? users.slice(0, take) : users;

        // One lookup for the whole page -- built from `items` so the extra
        // lookahead row isn't queried for.
        const following = await this.followService.loadFollowingSet(
            viewerId,
            items.map((user) => user.id),
        );

        return {
            data: {
                items: items
                    .filter((user) => user.profile)
                    .map((user) => this.toPublicProfile(user, following.has(user.id))),
                nextCursor: hasMore ? this.encodeCursor(sortSpec, items[items.length - 1]) : null,
                hasMore: !!hasMore,
                total: items.length,
            },
        };
    }

    private parseOccupationFilter(values: string[]): Occupation[] {
        const valid = new Set<string>(Object.values(Occupation));
        const invalid = values.filter((v) => !valid.has(v));

        if (invalid.length) {
            throw new BadRequestException(`Invalid occupation value(s): ${invalid.join(', ')}`);
        }

        return values as Occupation[];
    }

    private parseSort(sort?: string): SortSpec {
        switch (sort) {
            case undefined:
            case 'joined_desc':
                return { field: 'createdAt', direction: 'desc' };
            case 'joined_asc':
                return { field: 'createdAt', direction: 'asc' };
            case 'name_asc':
                return { field: 'displayName', direction: 'asc' };
            case 'name_desc':
                return { field: 'displayName', direction: 'desc' };
            case 'followers_desc':
                return { field: 'followersCount', direction: 'desc' };
            default:
                throw new BadRequestException(`Invalid sort value: ${sort}`);
        }
    }

    private buildOrderBy(sortSpec: SortSpec): Prisma.UserOrderByWithRelationInput[] {
        // followersCount sorts through the optional `stats` relation. That's
        // only safe because every user gets a UserStats row at creation (see
        // ConsumerService#handleAccountCreated) -- a user without one would
        // sort as NULL (first under DESC) and never match the cursor filter.
        const primary: Prisma.UserOrderByWithRelationInput =
            sortSpec.field === 'createdAt'
                ? { createdAt: sortSpec.direction }
                : sortSpec.field === 'followersCount'
                  ? { stats: { followersCount: sortSpec.direction } }
                  : { profile: { displayName: sortSpec.direction } };

        // id as tie-breaker (same direction) so ties on the sort field
        // still produce a stable, resumable keyset order.
        return [primary, { id: sortSpec.direction }];
    }

    // Cursor = base64url({ v: <last item's sort field value>, id: <last item's id> }).
    // Keyset pagination: "give me rows strictly after this (v, id) pair"
    // in the same order as buildOrderBy, so it composes with any sort.
    private encodeCursor(sortSpec: SortSpec, user: UserWithPublicProfile): string {
        const value =
            sortSpec.field === 'createdAt'
                ? user.createdAt.toISOString()
                : sortSpec.field === 'followersCount'
                  ? String(user.stats?.followersCount ?? 0)
                  : user.profile!.displayName;
        return Buffer.from(JSON.stringify({ v: value, id: user.id })).toString('base64url');
    }

    private decodeCursor(cursor: string): { v: string; id: string } {
        try {
            const decoded = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8')) as {
                v?: unknown;
                id?: unknown;
            };
            if (typeof decoded.v !== 'string' || typeof decoded.id !== 'string') throw new Error();
            return decoded as { v: string; id: string };
        } catch {
            throw new BadRequestException('Invalid cursor.');
        }
    }

    private buildCursorWhere(sortSpec: SortSpec, cursor: string): Prisma.UserWhereInput {
        const { v, id } = this.decodeCursor(cursor);
        const op = sortSpec.direction === 'desc' ? 'lt' : 'gt';

        if (sortSpec.field === 'createdAt') {
            const value = new Date(v);
            return {
                OR: [{ createdAt: { [op]: value } }, { createdAt: value, id: { [op]: id } }],
            };
        }

        if (sortSpec.field === 'followersCount') {
            const value = Number(v);
            if (!Number.isInteger(value)) throw new BadRequestException('Invalid cursor.');
            return {
                OR: [
                    { stats: { followersCount: { [op]: value } } },
                    { stats: { followersCount: value }, id: { [op]: id } },
                ],
            };
        }

        return {
            OR: [{ profile: { displayName: { [op]: v } } }, { profile: { displayName: v }, id: { [op]: id } }],
        };
    }

    // Accepts either a user id (UUID) or a displayName -- both are unique
    // (displayName is unique in this schema *and* in auth-service's Account,
    // where it originates; see PR discussion), so either one identifies
    // exactly one user.
    async getUser(identifier: string, viewerId?: string) {
        const isUuid = UUID_RE.test(identifier);

        const user = await this.prisma.user.findFirst({
            where: {
                deletedAt: null,
                ...(isUuid ? { id: identifier } : { profile: { displayName: identifier } }),
            },
            include: {
                profile: true,
                settings: true,
                stats: true,
                skills: { include: { skill: true } },
            },
        });

        if (!user || !user.profile) {
            throw new NotFoundException('User not found.');
        }

        // Same batch helper as the list, just with a single id -- one code
        // path for "does the viewer follow this user".
        const following = await this.followService.loadFollowingSet(viewerId, [user.id]);

        return { data: this.toPublicProfile(user, following.has(user.id)) };
    }

    // Excludes fields that should stay private unless the owner's
    // UserSetting explicitly opts in (showEmail / showBirthday) --
    // unlike getMe, these are endpoints for viewing *other* users.
    private toPublicProfile(user: UserWithPublicProfile, isFollowing: boolean) {
        const profile = user.profile!;
        const settings = user.settings;

        return {
            id: user.id,
            displayName: profile.displayName,
            fullName: profile.fullName,
            avatarUrl: profile.avatarUrl,
            avatarVariants: profile.avatarVariants,
            coverUrl: profile.coverUrl,
            coverVariants: profile.coverVariants,
            bio: profile.bio,
            occupation: profile.occupation,
            company: profile.company,
            location: profile.location,
            website: profile.website,
            gender: profile.gender,
            skills: user.skills.map((userSkill) => userSkill.skill.name),
            followersCount: user.stats?.followersCount ?? 0,
            followingCount: user.stats?.followingCount ?? 0,
            isFollowing,
            ...(settings?.showEmail && { email: profile.email }),
            ...(settings?.showBirthday && { birthday: profile.birthday }),
            createdAt: user.createdAt,
        };
    }
}
