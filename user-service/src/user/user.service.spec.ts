import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import { ConflictException, NotFoundException } from '@nestjs/common';
import { Prisma } from '../generated/prisma/client.js';
import { UserService } from './user.service.js';

type AsyncMock = (...args: unknown[]) => Promise<unknown>;

describe('UserService profile image uploads', () => {
    const prisma = {
        $transaction: jest.fn<AsyncMock>(),
        userProfile: {
            findUnique: jest.fn<AsyncMock>(),
            update: jest.fn<AsyncMock>(),
            updateMany: jest.fn<AsyncMock>(),
        },
    };
    const objectStorage = {
        uploadProfileImage: jest.fn<AsyncMock>(),
    };
    const outboxEvent = {
        create: jest.fn<AsyncMock>(),
    };
    const service = new UserService(prisma as never, {} as never, objectStorage as never, outboxEvent as never);

    beforeEach(() => {
        jest.clearAllMocks();
        // The transaction client is the same mock, so the assertions on
        // prisma.userProfile.* cover the calls made inside the transaction.
        prisma.$transaction.mockImplementation((callback: unknown) =>
            (callback as (t: unknown) => Promise<unknown>)(prisma),
        );
    });

    it('uploads an avatar before moving the database pointer', async () => {
        const uploadedFile = { buffer: Buffer.from('image'), size: 5 } as Express.Multer.File;
        prisma.userProfile.findUnique.mockResolvedValue({ userId: 'user-1' });
        prisma.userProfile.update.mockResolvedValue({});
        objectStorage.uploadProfileImage.mockResolvedValue({
            key: 'profile-images/avatar/hash/256x256.webp',
            url: 'http://localhost:9000/user-media/profile-images/avatar/hash/256x256.webp',
            sha256: 'hash',
            variants: {
                '64x64': {
                    key: 'profile-images/avatar/hash/64x64.webp',
                    url: 'http://localhost:9000/user-media/profile-images/avatar/hash/64x64.webp',
                    width: 64,
                    height: 64,
                    size: 100,
                },
                '256x256': {
                    key: 'profile-images/avatar/hash/256x256.webp',
                    url: 'http://localhost:9000/user-media/profile-images/avatar/hash/256x256.webp',
                    width: 256,
                    height: 256,
                    size: 500,
                },
            },
        });

        await expect(service.uploadAvatar('user-1', uploadedFile)).resolves.toEqual({
            data: {
                avatarUrl: 'http://localhost:9000/user-media/profile-images/avatar/hash/256x256.webp',
                avatarVariants: expect.objectContaining({
                    '64x64': expect.objectContaining({ width: 64, height: 64 }),
                    '256x256': expect.objectContaining({ width: 256, height: 256 }),
                }),
                sha256: 'hash',
            },
        });
        expect(objectStorage.uploadProfileImage).toHaveBeenCalledWith(
            'avatar',
            uploadedFile,
            UserService.MAX_AVATAR_BYTES,
        );
        expect(prisma.userProfile.update).toHaveBeenCalledWith({
            where: { userId: 'user-1' },
            data: {
                avatarUrl: 'http://localhost:9000/user-media/profile-images/avatar/hash/256x256.webp',
                avatarObjectKey: 'profile-images/avatar/hash/256x256.webp',
                avatarVariants: expect.objectContaining({
                    '64x64': expect.objectContaining({ width: 64, height: 64 }),
                    '256x256': expect.objectContaining({ width: 256, height: 256 }),
                }),
            },
        });
        expect(objectStorage.uploadProfileImage.mock.invocationCallOrder[0]).toBeLessThan(
            prisma.userProfile.update.mock.invocationCallOrder[0],
        );
    });

    it('does not upload when the profile does not exist', async () => {
        prisma.userProfile.findUnique.mockResolvedValue(null);

        await expect(service.uploadCover('missing-user', undefined)).rejects.toBeInstanceOf(NotFoundException);
        expect(objectStorage.uploadProfileImage).not.toHaveBeenCalled();
        expect(prisma.userProfile.update).not.toHaveBeenCalled();
    });

    it('removes only the avatar pointer and keeps the immutable S3 object for garbage collection', async () => {
        prisma.userProfile.updateMany.mockResolvedValue({ count: 1 });

        await expect(service.deleteAvatar('user-1')).resolves.toEqual({
            data: { avatarUrl: null, avatarVariants: null },
        });
        expect(prisma.userProfile.updateMany).toHaveBeenCalledWith({
            where: { userId: 'user-1' },
            data: { avatarUrl: null, avatarObjectKey: null, avatarVariants: expect.anything() },
        });
        expect(objectStorage.uploadProfileImage).not.toHaveBeenCalled();
    });

    it('returns not found when deleting an avatar for a missing profile', async () => {
        prisma.userProfile.updateMany.mockResolvedValue({ count: 0 });

        await expect(service.deleteAvatar('missing-user')).rejects.toBeInstanceOf(NotFoundException);
    });

    it('removes only the cover pointer and keeps the immutable S3 object for garbage collection', async () => {
        prisma.userProfile.updateMany.mockResolvedValue({ count: 1 });

        await expect(service.deleteCover('user-1')).resolves.toEqual({
            data: { coverUrl: null, coverVariants: null },
        });
        expect(prisma.userProfile.updateMany).toHaveBeenCalledWith({
            where: { userId: 'user-1' },
            data: { coverUrl: null, coverObjectKey: null, coverVariants: expect.anything() },
        });
        expect(objectStorage.uploadProfileImage).not.toHaveBeenCalled();
        expect(outboxEvent.create).not.toHaveBeenCalled();
    });

    it('writes a user.avatar.updated outbox event in the same transaction when an avatar is uploaded', async () => {
        const uploadedFile = { buffer: Buffer.from('image'), size: 5 } as Express.Multer.File;
        prisma.userProfile.findUnique.mockResolvedValue({ userId: 'user-1' });
        prisma.userProfile.update.mockResolvedValue({});
        objectStorage.uploadProfileImage.mockResolvedValue({
            key: 'profile-images/avatar/hash/256x256.webp',
            url: 'http://localhost:9000/user-media/profile-images/avatar/hash/256x256.webp',
            sha256: 'hash',
            variants: {},
        });

        await service.uploadAvatar('user-1', uploadedFile);

        expect(outboxEvent.create).toHaveBeenCalledWith(
            'user-1',
            'user.avatar.updated',
            {
                userId: 'user-1',
                avatarUrl: 'http://localhost:9000/user-media/profile-images/avatar/hash/256x256.webp',
            },
            prisma,
        );
    });

    it('does not write an outbox event when a cover is uploaded', async () => {
        const uploadedFile = { buffer: Buffer.from('image'), size: 5 } as Express.Multer.File;
        prisma.userProfile.findUnique.mockResolvedValue({ userId: 'user-1' });
        prisma.userProfile.update.mockResolvedValue({});
        objectStorage.uploadProfileImage.mockResolvedValue({
            key: 'profile-images/cover/hash/1200x400.webp',
            url: 'http://localhost:9000/user-media/profile-images/cover/hash/1200x400.webp',
            sha256: 'hash',
            variants: {},
        });

        await service.uploadCover('user-1', uploadedFile);

        expect(outboxEvent.create).not.toHaveBeenCalled();
    });

    it('writes a user.avatar.updated outbox event with a null url when an avatar is deleted', async () => {
        prisma.userProfile.updateMany.mockResolvedValue({ count: 1 });

        await service.deleteAvatar('user-1');

        expect(outboxEvent.create).toHaveBeenCalledWith(
            'user-1',
            'user.avatar.updated',
            { userId: 'user-1', avatarUrl: null },
            prisma,
        );
    });

    it('does not write an outbox event when deleting an avatar for a missing profile', async () => {
        prisma.userProfile.updateMany.mockResolvedValue({ count: 0 });

        await expect(service.deleteAvatar('missing-user')).rejects.toBeInstanceOf(NotFoundException);
        expect(outboxEvent.create).not.toHaveBeenCalled();
    });
});

describe('UserService updateProfile displayName', () => {
    const tx = {
        userProfile: {
            findUniqueOrThrow: jest.fn<AsyncMock>(),
            update: jest.fn<AsyncMock>(),
        },
    };
    const prisma = {
        $transaction: jest.fn<AsyncMock>(),
    };
    const outboxEvent = {
        create: jest.fn<AsyncMock>(),
    };
    const service = new UserService(prisma as never, {} as never, {} as never, outboxEvent as never);

    beforeEach(() => {
        jest.clearAllMocks();
        prisma.$transaction.mockImplementation((callback: unknown) => (callback as (t: unknown) => Promise<unknown>)(tx));
        tx.userProfile.findUniqueOrThrow.mockResolvedValue({ userId: 'user-1', displayName: 'old-name' });
        tx.userProfile.update.mockResolvedValue({ userId: 'user-1', displayName: 'new-name', avatarUrl: null });
        jest.spyOn(service, 'getMe').mockResolvedValue({} as never);
    });

    it('writes a user.profile.updated outbox event in the same transaction when displayName changes', async () => {
        await service.updateProfile('user-1', { displayName: 'new-name' });

        expect(outboxEvent.create).toHaveBeenCalledWith(
            'user-1',
            'user.profile.updated',
            { userId: 'user-1', displayName: 'new-name' },
            tx,
        );
    });

    it('does not write an outbox event when displayName is unchanged or not sent', async () => {
        await service.updateProfile('user-1', { displayName: 'old-name' });
        await service.updateProfile('user-1', { bio: 'hello' });

        expect(outboxEvent.create).not.toHaveBeenCalled();
    });

    it('maps a unique violation on displayName to a conflict', async () => {
        tx.userProfile.update.mockRejectedValue(
            new Prisma.PrismaClientKnownRequestError('Unique constraint failed', {
                code: 'P2002',
                clientVersion: 'test',
            }),
        );

        await expect(service.updateProfile('user-1', { displayName: 'taken' })).rejects.toBeInstanceOf(
            ConflictException,
        );
        expect(outboxEvent.create).not.toHaveBeenCalled();
    });
});
