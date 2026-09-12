import { Injectable } from '@nestjs/common';
import {hash} from "bcrypt";
import { PrismaService } from '../../prisma/prisma.service.js';
import {uuidv7} from "uuidv7";

@Injectable()
export class RefreshTokenService {

    constructor(
        private readonly prisma: PrismaService,
    ) {}

    // Persists a new refresh token session. Stores a bcrypt hash of the
    // raw token (never the token itself), so a leaked DB dump alone
    // can't be used to forge a valid session.
    async save(
        accountId: string,
        jti: string,
        refreshToken: string,
        deviceName?: string,
        userAgent?: string,
        ipAddress?: string,
    ) {

        const id = uuidv7();

        const tokenHash = await hash(refreshToken, 10);

        return this.prisma.refreshToken.create({

            data: {
                id,

                accountId,

                tokenHash,

                jti,

                expiresAt: new Date(
                    Date.now() +
                    7 * 24 * 60 * 60 * 1000,
                ),

                deviceName,
                userAgent,
                ipAddress,

            },

        });

    }

    // Looked up on every /refresh call, so jti should be indexed
    // (@unique in the Prisma schema) to keep this fast as the table grows.
    async findByJti(jti: string) {
        return this.prisma.refreshToken.findUnique({where: { jti }});
    }

    async deleteByJti(jti: string) {
        return this.prisma.refreshToken.delete({where: { jti }});
    }

    // Lists active (non-revoked) sessions for a "devices logged in"
    // style UI. Excludes tokenHash/jti from the response by design.
    async getSessions(accountId: string) {
        return this.prisma.refreshToken.findMany({
            where: {
                accountId,
                revoked: false,
            },
            orderBy: {
                lastUsedAt: "desc",
            },
            select: {
                id: true,
                deviceName: true,
                userAgent: true,
                ipAddress: true,
                createdAt: true,
                lastUsedAt: true,
                expiresAt: true,
            },
        });
    }

    // Revokes one specific session (e.g. "log out this device"). Scoped
    // by accountId as well as sessionId so a user can't delete another
    // account's session by guessing/brute-forcing a sessionId.
    async deleteSession(
        sessionId: string,
        accountId: string,
    ) {
        return this.prisma.refreshToken.deleteMany({
            where: {
                id: sessionId,
                accountId,
            },
        });
    }

    // Revokes all sessions for an account (e.g. "log out everywhere",
    // or on password change / suspected compromise).
    async deleteAllSessions(accountId: string) {
        return this.prisma.refreshToken.deleteMany({
            where: {
                accountId,
            },
        });
    }
}