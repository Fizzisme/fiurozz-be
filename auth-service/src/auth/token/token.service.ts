import {Injectable, UnauthorizedException} from "@nestjs/common";
import {JwtTokenService} from "../jwt/jwt-token.service.js";
import {RefreshTokenService} from "./refresh-token.service.js";
import type {Response} from "express";
import {DeviceInfo} from "../interfaces/device-info.interface.js";
import {IAccount} from "../../account/interfaces/account.interface.js";
import {JwtService} from "@nestjs/jwt";

// Shared token-issuance logic used by every login flow (password login,
// Google OAuth callback, etc.) so they all end up with identical
// token generation, session persistence, and cookie behavior.
@Injectable()
export class TokenService{
    constructor(
        private readonly jwtTokenService: JwtTokenService,
        private readonly refreshTokenService: RefreshTokenService,
        private readonly jwtService: JwtService
    ){}
    async issueToken(account: IAccount, device: DeviceInfo, res: Response){
        const tokens = await this.jwtTokenService.generateTokens(account);

        // Persist the refresh token as a revocable session (see
        // RefreshTokenService), tied to this device/IP for the
        // "active sessions" list and later revocation.
        await this.refreshTokenService.save(
            account.id,
            tokens.jti,
            tokens.refreshToken,
            device.deviceName,
            device.userAgent,
            device.ipAddress,
        );

        // NOTE: this cookie is set on the GATEWAY's own domain
        // (e.g. localhost:8080), which the browser never talks to
        // directly in production -- all real traffic goes through the
        // Next.js BFF, which maintains its OWN refreshToken cookie on
        // its own domain (see actions/authAction.ts). This cookie exists
        // purely so the endpoint can be exercised directly via
        // Postman/Swagger during development, without needing the full
        // Next.js proxy chain running. It plays no role in the actual
        // browser-facing login flow.
        res.cookie("refreshToken", tokens.refreshToken, {
            httpOnly: true,
            secure: process.env.NODE_ENV === "production",
            sameSite: "strict",
            path: "/api/auth",
            maxAge: 7 * 24 * 60 * 60 * 1000,
        })

        return {
            message: 'Login successfully.',
            data: {
                ...tokens,
            }
        };
    }



    // Wraps the real tokens in a short-lived, single-purpose JWT so
    // they can safely cross the Gateway -> Next.js domain boundary via
    // a redirect URL. Signed with a SEPARATE secret from access/refresh
    // tokens, so even if this secret leaked, it couldn't be used to
    // forge a real access token.
    async createOAuthHandoffCode(
        accountId: string,
        deviceInfo: DeviceInfo,
    ) {
        return this.jwtService.signAsync(
            { accountId, deviceInfo },
            {
                secret: process.env.OAUTH_HANDOFF_SECRET ?? 'OAUTH_HANDOFF_SECRET',
                expiresIn: '30s',
            },
        );
    }

    async verifyOAuthHandoffCode(code: string) {
        try {
            const payload = await this.jwtService.verifyAsync(code, {
                secret: process.env.OAUTH_HANDOFF_SECRET ?? 'OAUTH_HANDOFF_SECRET',
            });
            return {
                accountId: payload.accountId,
                deviceInfo: payload.deviceInfo,
            };
        } catch {
            throw new UnauthorizedException('OAuth handoff code is invalid or expired');
        }
    }
}