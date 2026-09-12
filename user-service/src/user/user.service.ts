import {Injectable, UnauthorizedException, NotFoundException} from '@nestjs/common';
import type {Request} from 'express';
import {PrismaService} from "../prisma/prisma.service.js";

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
}

