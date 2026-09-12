import {Injectable, UnauthorizedException} from "@nestjs/common";
import {JwtService} from "@nestjs/jwt";
import {uuidv7} from "uuidv7";

@Injectable()
export class JwtTokenService {
    constructor(private readonly jwtService: JwtService) {}


    // Converts a jsonwebtoken-style duration string ("15m", "1h", "7d")
    // into whole seconds. Needed because expiresIn is only consumed by
    // the JWT library itself -- callers that need to set a cookie's
    // maxAge (which the FE does) need the plain number instead.
     parseExpiresInSeconds(value: string): number {
        const match = /^(\d+)([smhd])$/.exec(value);
        if (!match) {
            throw new Error(`Invalid duration format: "${value}". Expected e.g. "15m", "1h", "7d".`);
        }

        const [, amountStr, unit] = match;
        const amount = Number(amountStr);
        const unitToSeconds: Record<string, number> = {
            s: 1,
            m: 60,
            h: 60 * 60,
            d: 60 * 60 * 24,
        };

        return amount * unitToSeconds[unit];
    }

    // Issues an access token + refresh token pair. Refresh token gets a
    // unique jti (JWT ID), used to track/revoke it later (e.g. in
    // RefreshTokenService), independent of the token's own expiry.
    async generateTokens(user: {id: string; email: string | null; roles: string[]}) {
        const payload = {
            sub: user.id,
            email: user.email,
            roles: user.roles,
        }

        const jti = uuidv7()

        const accessExpiresIn = process.env.JWT_ACCESS_EXPIRES as `${number}${"s"|"m"|"h"|"d"}`;
        const refreshExpiresIn = process.env.JWT_REFRESH_EXPIRES as `${number}${"s"|"m"|"h"|"d"}`;

        const [accessToken, refreshToken] =
            await Promise.all([
                this.jwtService.signAsync(payload, {
                    secret: process.env.JWT_ACCESS_SECRET,
                    expiresIn: accessExpiresIn,
                    issuer: process.env.JWT_ISSUER,
                }),

                this.jwtService.signAsync(payload, {
                    secret: process.env.JWT_REFRESH_SECRET,
                    expiresIn: refreshExpiresIn,
                    issuer: process.env.JWT_ISSUER,
                    jwtid: jti,
                })
            ])

        return {
            accessToken,
            refreshToken,
            jti,
            accessTokenExpiresIn: this.parseExpiresInSeconds(accessExpiresIn),
            refreshTokenExpiresIn: this.parseExpiresInSeconds(refreshExpiresIn)
        }

    }

    // Verifies a refresh token's signature/expiry only — does not check
    // revocation status (see RefreshTokenService for that).
    async verifyRefreshToken(refreshToken: string) {
        try {
            return await this.jwtService.verifyAsync(refreshToken, {
                secret: process.env.JWT_REFRESH_SECRET,
            });
        } catch {
            throw new UnauthorizedException("Invalid or expired refresh token.");
        }
    }
}