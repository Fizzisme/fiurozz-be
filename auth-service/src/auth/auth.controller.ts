import {Body, Controller, Post, Res, Req, Get, UseGuards, Param, Delete} from '@nestjs/common';

import {AuthService} from "./auth.service.js";
import {RegisterDto} from "./dto/register.dto.js";
import {LoginDto}  from "./dto/login.dto.js";
import type { Request, Response } from 'express';
import {GoogleGuard} from "../oauth-account/guards/google.guard.js";
import {GithubGuard} from "../oauth-account/guards/github.guard.js";
import {FacebookGuard} from "../oauth-account/guards/facebook.guard.js";

/**
 * Handles authentication endpoints: registration, login, and
 * access-token refresh. Mounted at the service root (no controller
 * prefix), since routing/prefixing (e.g. "/auth") is handled by the
 * API Gateway upstream.
 */
@Controller()
export class AuthController {
    constructor(private readonly authService: AuthService) {}

    // Creates a new account account.
    @Post('register')
    register (@Body() dto: RegisterDto) {
        return this.authService.register(dto);
    }

    // Authenticates credentials and issues tokens. Uses passthrough
    // mode so the service can set cookies (e.g. refresh token) on
    // res directly while Nest still sends the returned body as the
    // JSON response.
    @Post('login')
    login (@Body() dto: LoginDto, @Req() req: Request, @Res({ passthrough: true }) res: Response){
        return this.authService.login(dto, req, res)
    }

    // Issues a new access token using the refresh token (read from
    // the request, likely a cookie set during login) and rotates it
    // in the response if applicable.
    @Post('refresh')
    refresh(@Req() req: Request, @Res({ passthrough: true }) res: Response){
        return this.authService.refresh(req, res)
    }

    // Redirects the user to Google's OAuth consent screen. Handler body
    // is never reached — GoogleGuard (AuthGuard('google')) intercepts and
    // issues the redirect before this runs.
    @Get('oauth/google')
    @UseGuards(GoogleGuard)
    googleLogin(){}


    // Google redirects back here with the authorization code. GoogleGuard
    // runs the full code-exchange + profile fetch, then req.user holds
    // the verified Google profile passed on from GoogleStrategy.validate().
    @Get("oauth/google/callback")
    @UseGuards(GoogleGuard)
    googleCallback(@Req() req: Request, @Res({ passthrough: true }) res:Response) {
        return this.authService.oauthLogin(req, res)
    }

    // Same OAuth pattern as Google, using GitHub's strategies/guard instead.
    @Get("oauth/github")
    @UseGuards(GithubGuard)
    githubLogin() {}

    @Get("oauth/github/callback")
    @UseGuards(GithubGuard)
    githubCallback(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
    ) {
        return this.authService.oauthLogin(req, res);
    }

    @Get("oauth/facebook")
    @UseGuards(FacebookGuard)
    facebookLogin() {}

    @Get("oauth/facebook/callback")
    @UseGuards(FacebookGuard)
    facebookCallback(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
    ) {
        return this.authService.oauthLogin(req, res);
    }

    // Server-to-server endpoint called ONLY by the Next.js BFF (never
    // directly by a browser) to trade a short-lived OAuth handoff code
    // for a real access/refresh token pair.
    //
    // Splitting this from the OAuth callback (oauthLogin) is intentional:
    // oauthLogin only verifies the OAuth profile and issues a handoff
    // code -- it does NOT create a refresh-token session. The session is
    // only created here, at exchange time, so a popup closed mid-flow or
    // a failed exchange call never leaves an orphaned session in the DB.
    //
    // req/res are passed through to authService.oauthExchange() because
    // issueToken() (called downstream) needs req's device/IP info to
    // persist the session, and res to set the refreshToken cookie -- same
    // contract as the password-login flow.
    @Post("oauth/exchange")
    oauthExchange(@Body() dto: {code: string}, @Res({ passthrough: true }) res:Response){
        return this.authService.oauthExchange(dto.code, res)
    }

    // Revokes the current session's refresh token and clears the cookie.
    // Account identity comes from X-User-Id header, set by the API
    // Gateway after verifying the access token — this service does not
    // re-verify the JWT itself.
    @Post("logout")
    logout(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
    ) {
        return this.authService.logout(req, res);
    }

    // Lists all active (non-revoked) sessions for the current account —
    // used for a "devices logged in" style UI.
    @Get("sessions")
    getSessions(
        @Req() req: Request,
    ) {
        return this.authService.getSessions(req);
    }

    // Revokes a single session by ID (e.g. "log out this device"),
    // scoped to the current account so a user can't revoke someone
    // else's session by guessing an ID.
    @Delete("sessions/:id")
    logoutSession(
        @Param("id") id: string,
        @Req() req: Request,
    ) {
        return this.authService.logoutSession(
            id,
            req,
        );
    }

    // Revokes every session for the current account (e.g. "log out
    // everywhere", or after a password change / suspected compromise).
    @Post("logout-all")
    logoutAll(
        @Req() req: Request,
    ) {
        return this.authService.logoutAll(req);
    }
}
