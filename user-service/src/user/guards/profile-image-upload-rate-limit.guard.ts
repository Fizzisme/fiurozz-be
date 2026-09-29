import { CanActivate, ExecutionContext, HttpException, HttpStatus, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Request, Response } from 'express';

@Injectable()
export class ProfileImageUploadRateLimitGuard implements CanActivate {
    private readonly hits = new Map<string, number[]>();
    private readonly limit: number;
    private readonly windowMs: number;

    constructor(config: ConfigService) {
        this.limit = this.readPositiveInteger(config.get<string>('PROFILE_IMAGE_UPLOAD_LIMIT', '5'), 5);
        const windowSeconds = this.readPositiveInteger(
            config.get<string>('PROFILE_IMAGE_UPLOAD_WINDOW_SECONDS', '60'),
            60,
        );
        this.windowMs = windowSeconds * 1000;
    }

    canActivate(context: ExecutionContext): boolean {
        const request = context.switchToHttp().getRequest<Request>();
        const response = context.switchToHttp().getResponse<Response>();
        const rawUserId = request.headers['x-user-id'];
        const userId = Array.isArray(rawUserId) ? rawUserId[0] : rawUserId;

        // Authentication remains the responsibility of @UserId. Let that
        // decorator return the established 401 response when the gateway did
        // not provide an identity.
        if (!userId) return true;

        const now = Date.now();
        const windowStart = now - this.windowMs;
        const recentHits = (this.hits.get(userId) ?? []).filter((timestamp) => timestamp > windowStart);

        response.setHeader('X-RateLimit-Limit', this.limit);

        if (recentHits.length >= this.limit) {
            const retryAfterSeconds = Math.max(1, Math.ceil((recentHits[0] + this.windowMs - now) / 1000));
            response.setHeader('X-RateLimit-Remaining', 0);
            response.setHeader('X-RateLimit-Reset', Math.ceil((recentHits[0] + this.windowMs) / 1000));
            response.setHeader('Retry-After', retryAfterSeconds);
            this.hits.set(userId, recentHits);
            throw new HttpException('Profile image upload limit exceeded.', HttpStatus.TOO_MANY_REQUESTS);
        }

        recentHits.push(now);
        this.hits.set(userId, recentHits);
        response.setHeader('X-RateLimit-Remaining', this.limit - recentHits.length);
        response.setHeader('X-RateLimit-Reset', Math.ceil((recentHits[0] + this.windowMs) / 1000));
        this.pruneIdleUsers(windowStart);
        return true;
    }

    private pruneIdleUsers(windowStart: number): void {
        if (this.hits.size < 1_000) return;

        for (const [userId, timestamps] of this.hits) {
            if (!timestamps.some((timestamp) => timestamp > windowStart)) {
                this.hits.delete(userId);
            }
        }
    }

    private readPositiveInteger(value: string | undefined, fallback: number): number {
        const parsed = Number(value);
        return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
    }
}
