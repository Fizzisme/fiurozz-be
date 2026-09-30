import { describe, expect, it, jest } from '@jest/globals';
import { ConfigService } from '@nestjs/config';
import { ProfileImageGcService } from './profile-image-gc.service.js';

describe('ProfileImageGcService', () => {
    it('passes referenced keys and a 24-hour cutoff to object storage', async () => {
        const prisma = {
            userProfile: {
                findMany: jest.fn().mockResolvedValue([
                    { avatarObjectKey: 'profile-images/avatar/hash/256x256.webp', coverObjectKey: null },
                    { avatarObjectKey: null, coverObjectKey: 'profile-images/cover/hash/1280x480.webp' },
                ]),
            },
        };
        const objectStorage = {
            deleteUnreferencedProfileImages: jest.fn().mockResolvedValue({ scanned: 4, deleted: 2 }),
        };
        const config = {
            get: jest.fn((key: string, fallback: string) => {
                const values: Record<string, string> = {
                    PROFILE_IMAGE_GC_ENABLED: 'true',
                    PROFILE_IMAGE_GC_GRACE_HOURS: '24',
                };
                return values[key] ?? fallback;
            }),
        } as unknown as ConfigService;
        const service = new ProfileImageGcService(prisma as never, objectStorage as never, config);

        await expect(service.collectGarbage(new Date('2026-09-30T03:00:00.000Z'))).resolves.toEqual({
            scanned: 4,
            deleted: 2,
        });
        expect(objectStorage.deleteUnreferencedProfileImages).toHaveBeenCalledWith(
            ['profile-images/avatar/hash/256x256.webp', 'profile-images/cover/hash/1280x480.webp'],
            new Date('2026-09-29T03:00:00.000Z'),
        );
    });

    it('does nothing when garbage collection is disabled', async () => {
        const prisma = { userProfile: { findMany: jest.fn() } };
        const objectStorage = { deleteUnreferencedProfileImages: jest.fn() };
        const config = { get: jest.fn(() => 'false') } as unknown as ConfigService;
        const service = new ProfileImageGcService(prisma as never, objectStorage as never, config);

        await expect(service.collectGarbage()).resolves.toEqual({ scanned: 0, deleted: 0 });
        expect(prisma.userProfile.findMany).not.toHaveBeenCalled();
        expect(objectStorage.deleteUnreferencedProfileImages).not.toHaveBeenCalled();
    });
});
