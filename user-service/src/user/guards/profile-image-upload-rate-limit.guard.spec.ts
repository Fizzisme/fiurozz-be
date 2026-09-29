import { afterEach, describe, expect, it, jest } from '@jest/globals';
import { ExecutionContext, HttpException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ProfileImageUploadRateLimitGuard } from './profile-image-upload-rate-limit.guard.js';

describe('ProfileImageUploadRateLimitGuard', () => {
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('limits profile image uploads per user and returns retry headers', () => {
        const config = {
            get: jest.fn((key: string, fallback: string) => {
                const values: Record<string, string> = {
                    PROFILE_IMAGE_UPLOAD_LIMIT: '2',
                    PROFILE_IMAGE_UPLOAD_WINDOW_SECONDS: '60',
                };
                return values[key] ?? fallback;
            }),
        } as unknown as ConfigService;
        const guard = new ProfileImageUploadRateLimitGuard(config);
        const setHeader = jest.fn();
        const context = {
            switchToHttp: () => ({
                getRequest: () => ({ headers: { 'x-user-id': 'user-1' } }),
                getResponse: () => ({ setHeader }),
            }),
        } as unknown as ExecutionContext;
        jest.spyOn(Date, 'now').mockReturnValue(1_000_000);

        expect(guard.canActivate(context)).toBe(true);
        expect(guard.canActivate(context)).toBe(true);
        try {
            guard.canActivate(context);
            throw new Error('Expected the third upload to be rate limited.');
        } catch (error) {
            expect(error).toBeInstanceOf(HttpException);
            expect((error as HttpException).getStatus()).toBe(429);
        }
        expect(setHeader).toHaveBeenCalledWith('Retry-After', 60);
        expect(setHeader).toHaveBeenCalledWith('X-RateLimit-Remaining', 0);
    });
});
