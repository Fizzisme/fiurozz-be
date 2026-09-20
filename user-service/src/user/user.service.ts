import {Injectable, UnauthorizedException, NotFoundException, BadRequestException} from '@nestjs/common';
import type {Request} from 'express';
import {PrismaService} from "../prisma/prisma.service.js";
import {Prisma} from "../generated/prisma/client.js";
import {Occupation} from "../generated/prisma/enums.js";

const MAX_LIMIT = 100;

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

interface GetUsersQuery {
    limit: number;
    cursor?: string;
    occupation?: string[];
    skills?: string[];
    sort?: string;
}

type SortField = 'createdAt' | 'displayName';

interface SortSpec {
    field: SortField;
    direction: 'asc' | 'desc';
}

type UserWithPublicProfile = Prisma.UserGetPayload<{
    include: { profile: true; settings: true; skills: { include: { skill: true } } };
}>;

@Injectable()
export class UserService {

    constructor(private readonly prisma: PrismaService) {
    }

    // Reads the caller's identity from X-User-Id, a header the API
    // Gateway attaches only after it has already verified the JWT.
    // This service trusts that header completely and never re-verifies
    // the token itself -- see network policy: this service only
    // accepts traffic originating from the Gateway.
    async getMe(req: Request) {
        const userId = req.headers['x-user-id'] as string | undefined;

        if (!userId) {
            // Should be unreachable in practice -- the Gateway's
            // JWTAuth middleware guarantees this header is present
            // for any authenticated route. Guarding anyway in case
            // this endpoint is ever misconfigured as AuthOptional.
            throw new UnauthorizedException('User not found.');
        }

        const user = await this.prisma.user.findUnique({
            where: { id: userId },
            include: {
                profile: true,
                settings: true,
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
                coverUrl: user.profile.coverUrl,
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
                createdAt: user.createdAt
            },


        };
    }


    async getUsers(query: GetUsersQuery){
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
                skills: { include: { skill: true } },
            },
        });

        const hasMore = users.length > take;
        const items = hasMore ? users.slice(0, take) : users;

        return {
            data: {
                items: items.filter((user) => user.profile).map((user) => this.toPublicProfile(user)),
                nextCursor: hasMore ? this.encodeCursor(sortSpec, items[items.length - 1]) : null,
                hasMore: !!hasMore,
                total: items.length,
            }
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
            default:
                throw new BadRequestException(`Invalid sort value: ${sort}`);
        }
    }

    private buildOrderBy(sortSpec: SortSpec): Prisma.UserOrderByWithRelationInput[] {
        const primary: Prisma.UserOrderByWithRelationInput =
            sortSpec.field === 'createdAt'
                ? { createdAt: sortSpec.direction }
                : { profile: { displayName: sortSpec.direction } };

        // id as tie-breaker (same direction) so ties on the sort field
        // still produce a stable, resumable keyset order.
        return [primary, { id: sortSpec.direction }];
    }

    // Cursor = base64url({ v: <last item's sort field value>, id: <last item's id> }).
    // Keyset pagination: "give me rows strictly after this (v, id) pair"
    // in the same order as buildOrderBy, so it composes with any sort.
    private encodeCursor(sortSpec: SortSpec, user: UserWithPublicProfile): string {
        const value = sortSpec.field === 'createdAt' ? user.createdAt.toISOString() : user.profile!.displayName;
        return Buffer.from(JSON.stringify({ v: value, id: user.id })).toString('base64url');
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

    private buildCursorWhere(sortSpec: SortSpec, cursor: string): Prisma.UserWhereInput {
        const { v, id } = this.decodeCursor(cursor);
        const op = sortSpec.direction === 'desc' ? 'lt' : 'gt';

        if (sortSpec.field === 'createdAt') {
            const value = new Date(v);
            return {
                OR: [
                    { createdAt: { [op]: value } },
                    { createdAt: value, id: { [op]: id } },
                ],
            };
        }

        return {
            OR: [
                { profile: { displayName: { [op]: v } } },
                { profile: { displayName: v }, id: { [op]: id } },
            ],
        };
    }

    // Accepts either a user id (UUID) or a displayName -- both are unique
    // (displayName is unique in this schema *and* in auth-service's Account,
    // where it originates; see PR discussion), so either one identifies
    // exactly one user.
    async getUser(identifier: string){
        const isUuid = UUID_RE.test(identifier);

        const user = await this.prisma.user.findFirst({
            where: {
                deletedAt: null,
                ...(isUuid ? { id: identifier } : { profile: { displayName: identifier } }),
            },
            include: {
                profile: true,
                settings: true,
                skills: { include: { skill: true } },
            },
        });

        if (!user || !user.profile) {
            throw new NotFoundException('User not found.');
        }

        return { data: this.toPublicProfile(user) };
    }

    // Excludes fields that should stay private unless the owner's
    // UserSetting explicitly opts in (showEmail / showBirthday) --
    // unlike getMe, these are endpoints for viewing *other* users.
    private toPublicProfile(user: UserWithPublicProfile) {
        const profile = user.profile!;
        const settings = user.settings;

        return {
            id: user.id,
            displayName: profile.displayName,
            fullName: profile.fullName,
            avatarUrl: profile.avatarUrl,
            coverUrl: profile.coverUrl,
            bio: profile.bio,
            occupation: profile.occupation,
            company: profile.company,
            location: profile.location,
            website: profile.website,
            gender: profile.gender,
            skills: user.skills.map((userSkill) => userSkill.skill.name),
            ...(settings?.showEmail && { email: profile.email }),
            ...(settings?.showBirthday && { birthday: profile.birthday }),
            createdAt: user.createdAt,
        };
    }
}

