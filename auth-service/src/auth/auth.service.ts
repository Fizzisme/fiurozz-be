import {BadRequestException, Injectable, NotFoundException, UnauthorizedException} from '@nestjs/common';
import {RegisterDto} from "./dto/register.dto.js";
import {PrismaService} from "../prisma/prisma.service.js";
import {LoginDto} from "./dto/login.dto.js";
import {PasswordService} from "./password/password.service.js";
import {RefreshTokenService} from "./token/refresh-token.service.js";
import {JwtTokenService} from "./jwt/jwt-token.service.js";
import type { Request, Response } from 'express';
import {uuidv7} from 'uuidv7';
import {TokenService} from "./token/token.service.js";
import {IAccount} from "../account/interfaces/account.interface.js";
import {AccountService} from "../account/account.service.js";
import {OauthAccountService} from "../oauth-account/oauth-account.service.js";
import {IOAuthUser} from "../oauth-account/interfaces/oauth-user.interface.js";
import {OutboxEventService} from "../outboxEvent/outbox-event.service.js";
import {DeviceInfo} from "./interfaces/device-info.interface.js";

@Injectable()
export class AuthService {
    constructor(
        private readonly accountService: AccountService,
        private readonly prismaService: PrismaService,
        private readonly passwordService: PasswordService,
        private readonly refreshTokenService: RefreshTokenService,
        private readonly jwtTokenService: JwtTokenService,
        private readonly oauthService: OauthAccountService,
        private readonly tokenService: TokenService,
        private readonly outboxEventService: OutboxEventService
    ) {}
    async register (data: RegisterDto) {

        const account : IAccount | null = await this.accountService.findAccountByEmail(data.email);

        if (account) {
            throw new BadRequestException('Email already exists.');
        }

        const passwordHash = await this.passwordService.hashPassword(data.password);
        const accountId = uuidv7();

        await this.prismaService.$transaction([
            this.accountService.createAccount({
                id: accountId,
                email: data.email,
                fullName: data.fullName,
                displayName: data.displayName,
                passwordHash: passwordHash,
                emailVerified: false
            }),
            this.outboxEventService.create(
                accountId,
                "account.created",
                {
                    id: accountId,
                    email: data.email,
                    fullName: data.fullName,
                    displayName: data.displayName,
                    country: data.country,
                    birthday: data.birthday,
                    gender: data.gender,
                }
            ),
        ]);

         return {
             message: 'Register successfully.',
         };
    }

    async login(data: LoginDto, req: Request, res: Response) {

        const account : IAccount | null = await this.accountService.findAccountByEmail(data.email);

        if (!account) {
            throw new UnauthorizedException('Invalid email or password.');
        }

        if(!account.passwordHash){
            throw new UnauthorizedException('This account uses Google/GitHub login.');
        }

        if(!await this.passwordService.comparePassword(data.password,account.passwordHash)){
            throw new UnauthorizedException('Invalid email or password.');
        }

        return this.tokenService.issueToken(account,{
                deviceName: req.headers['x-device-name'] as string | undefined,
                userAgent: req.headers['user-agent'],
                ipAddress: req.ip,
            },
            res)
    }

    async refresh(req: Request, res: Response) {
        const refreshToken = req.cookies.refreshToken;

        if (!refreshToken) {
            throw new UnauthorizedException();
        }

        const payload = await this.jwtTokenService.verifyRefreshToken(refreshToken);

        const token = await this.refreshTokenService.findByJti(payload.jti);

        if (!token || token.revoked || token.expiresAt < new Date()) {
            throw new UnauthorizedException();
        }

        const isMatch = await this.passwordService.comparePassword(refreshToken, token.tokenHash);
        if (!isMatch) {
            throw new UnauthorizedException();
        }

        const account: IAccount | null = await this.accountService.findAccountById(payload.sub);
        if (!account) {
            throw new UnauthorizedException();
        }

        const tokens = await this.jwtTokenService.generateTokens(account);
        const newTokenHash = await this.passwordService.hashPassword(tokens.refreshToken);
        const refreshId = uuidv7();

        await this.prismaService.$transaction([
            this.prismaService.refreshToken.delete({ where: { id: token.id } }),
            this.prismaService.refreshToken.create({
                data: {
                    id: refreshId,
                    accountId: account.id,
                    jti: tokens.jti,
                    tokenHash: newTokenHash,
                    deviceName: token.deviceName,
                    userAgent: token.userAgent,
                    ipAddress: token.ipAddress,
                    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
                },
            }),
        ]);

        res.cookie('refreshToken', tokens.refreshToken, {
            httpOnly: true,
            secure: process.env.NODE_ENV === 'production',
            sameSite: 'strict',
            path: '/api/auth',
            maxAge: 7 * 24 * 60 * 60 * 1000,
        });

        return {
            data: {
                accessTokenExpiresIn: this.jwtTokenService.parseExpiresInSeconds(process.env.JWT_ACCESS_EXPIRES as `${number}${"s"|"m"|"h"|"d"}`),
                refreshTokenExpiresIn: this.jwtTokenService.parseExpiresInSeconds(process.env.JWT_REFRESH_EXPIRES as `${number}${"s"|"m"|"h"|"d"}`),
                accessToken: tokens.accessToken
            }
        };
    }

    async oauthLogin(req: Request, res: Response) {

        const profile = req.user as IOAuthUser;

        if (profile.provider === "github" && !profile.email) {
            // throw new UnauthorizedException(
            //     "A verified email is required to sign in with GitHub.",
            // );


            // Instead of throwing (which renders raw JSON in the popup and
            // leaves the main tab waiting forever with no postMessage),
            // redirect into the same popup-callback page with a failure
            // signal so the popup can close itself cleanly either way.
            return res.redirect(
                `${process.env.FE_URL}/api/auth/oauth/popup-callback?error=github_email_required`
            );
        }

        const account: IAccount = await this.oauthService.loginWithOauth(profile)

        const deviceInfo: DeviceInfo = {
            deviceName: req.headers['x-device-name'] as string | undefined,
            userAgent: req.headers['user-agent'],
            ipAddress: req.ip,
        }

        const handoffCode = await this.tokenService.createOAuthHandoffCode(
            account.id,
            deviceInfo
        );

        return res.redirect(`${process.env.FE_URL}/api/auth/oauth/popup-callback?code=${handoffCode}`);
    }

    async oauthExchange(code: string, res: Response) {
        const {accountId, deviceInfo} = await this.tokenService.verifyOAuthHandoffCode(code);

        const account = await this.accountService.findAccountById(accountId);

        if (!account) {
            throw new UnauthorizedException("Invalid account.");
        }

        return this.tokenService.issueToken(account,{
                deviceName: deviceInfo?.deviceName,
                userAgent: deviceInfo?.userAgent,
                ipAddress: deviceInfo?.ipAddress,
            },
            res)
    }

    async logout(
        req: Request,
        res: Response,
    ) {

        const refreshToken =
            req.cookies.refreshToken;

        if (!refreshToken) {
            return {
                message: "Logout successfully.",
            };
        }

        try {

            const payload =
                await this.jwtTokenService.verifyRefreshToken(
                    refreshToken,
                );

            await this.refreshTokenService.deleteByJti(
                payload.jti,
            );

        } catch {
            // Ignore invalid/expired refresh token.
        }

        res.clearCookie("refreshToken", {
            httpOnly: true,
            secure: process.env.NODE_ENV === "production",
            sameSite: "strict",
            path: "/api/auth",
        });

        return {
            message: "Logout successfully.",
        };
    }

    async getSessions(req: Request) {

        const userId = req.get("x-user-id");

        if (!userId) {
            throw new UnauthorizedException();
        }

        const sessions = await this.refreshTokenService.getSessions(
            userId,
        );

        return {
            data: sessions,
        }
    }

    async logoutSession(
        sessionId: string,
        req: Request,
    ) {

        const userId = req.get("x-user-id");

        if (!userId) {
            throw new UnauthorizedException();
        }

        const result = await this.refreshTokenService.deleteSession(
            sessionId,
            userId,
        );

        if (result.count === 0) {
            throw new NotFoundException(
                "Session not found.",
            );
        }

        return {
            message: "Session removed successfully.",
        };
    }

    async logoutAll(req: Request) {

        const userId = req.get("x-user-id");

        if (!userId) {
            throw new UnauthorizedException();
        }

        await this.refreshTokenService.deleteAllSessions(
            userId,
        );

        return {
            message: "Logged out from all devices.",
        };
    }
}
