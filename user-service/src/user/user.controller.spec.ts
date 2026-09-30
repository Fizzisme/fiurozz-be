import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import type { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import type { App as SupertestApp } from 'supertest/types.js';
import { UserController } from './user.controller.js';
import { ProfileImageUploadRateLimitGuard } from './guards/profile-image-upload-rate-limit.guard.js';
import { UserService } from './user.service.js';

const ONE_PIXEL_PNG = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
);

describe('UserController profile image uploads', () => {
    let app: INestApplication;
    const uploadAvatar = jest.fn<(userId: string, file?: Express.Multer.File) => Promise<object>>();
    const replaceAvatar = jest.fn<(userId: string, file?: Express.Multer.File) => Promise<object>>();
    const deleteAvatar = jest.fn<(userId: string) => Promise<object>>();
    const replaceCover = jest.fn<(userId: string, file?: Express.Multer.File) => Promise<object>>();
    const deleteCover = jest.fn<(userId: string) => Promise<object>>();

    beforeAll(async () => {
        uploadAvatar.mockResolvedValue({
            data: { avatarUrl: 'http://localhost:9000/user-media/profile-images/hash.png' },
        });
        replaceAvatar.mockResolvedValue({
            data: { avatarUrl: 'http://localhost:9000/user-media/profile-images/replacement.png' },
        });
        deleteAvatar.mockResolvedValue({ data: { avatarUrl: null } });
        replaceCover.mockResolvedValue({
            data: { coverUrl: 'http://localhost:9000/user-media/profile-images/replacement-cover.png' },
        });
        deleteCover.mockResolvedValue({ data: { coverUrl: null } });
        const moduleRef = await Test.createTestingModule({
            controllers: [UserController],
            providers: [
                {
                    provide: UserService,
                    useValue: {
                        uploadAvatar,
                        replaceAvatar,
                        deleteAvatar,
                        uploadCover: jest.fn(),
                        replaceCover,
                        deleteCover,
                    },
                },
            ],
        })
            .overrideGuard(ProfileImageUploadRateLimitGuard)
            .useValue({ canActivate: () => true })
            .compile();

        app = moduleRef.createNestApplication();
        await app.init();
    });

    afterAll(async () => {
        await app.close();
    });

    it('accepts multipart field "file" and forwards the gateway user id', async () => {
        const httpServer = app.getHttpServer() as SupertestApp;

        await request(httpServer)
            .post('/me/avatar')
            .set('X-User-Id', '11111111-1111-4111-8111-111111111111')
            .attach('file', ONE_PIXEL_PNG, { filename: 'avatar.png', contentType: 'image/png' })
            .expect(201);

        expect(uploadAvatar).toHaveBeenCalledTimes(1);
        expect(uploadAvatar.mock.calls[0][0]).toBe('11111111-1111-4111-8111-111111111111');
        expect(uploadAvatar.mock.calls[0][1]).toMatchObject({
            fieldname: 'file',
            originalname: 'avatar.png',
            mimetype: 'image/png',
        });
    });

    it('replaces an avatar through PUT using the same multipart contract', async () => {
        const httpServer = app.getHttpServer() as SupertestApp;

        await request(httpServer)
            .put('/me/avatar')
            .set('X-User-Id', '11111111-1111-4111-8111-111111111111')
            .attach('file', ONE_PIXEL_PNG, { filename: 'replacement.png', contentType: 'image/png' })
            .expect(200);

        expect(replaceAvatar).toHaveBeenCalledWith(
            '11111111-1111-4111-8111-111111111111',
            expect.objectContaining({ originalname: 'replacement.png' }),
        );
    });

    it('removes the avatar association through DELETE', async () => {
        const httpServer = app.getHttpServer() as SupertestApp;

        await request(httpServer)
            .delete('/me/avatar')
            .set('X-User-Id', '11111111-1111-4111-8111-111111111111')
            .expect(200);

        expect(deleteAvatar).toHaveBeenCalledWith('11111111-1111-4111-8111-111111111111');
    });

    it('replaces a cover image through PUT', async () => {
        const httpServer = app.getHttpServer() as SupertestApp;

        await request(httpServer)
            .put('/me/cover')
            .set('X-User-Id', '11111111-1111-4111-8111-111111111111')
            .attach('file', ONE_PIXEL_PNG, { filename: 'cover.png', contentType: 'image/png' })
            .expect(200);

        expect(replaceCover).toHaveBeenCalledWith(
            '11111111-1111-4111-8111-111111111111',
            expect.objectContaining({ originalname: 'cover.png' }),
        );
    });

    it('removes the cover association through DELETE', async () => {
        const httpServer = app.getHttpServer() as SupertestApp;

        await request(httpServer)
            .delete('/me/cover')
            .set('X-User-Id', '11111111-1111-4111-8111-111111111111')
            .expect(200);

        expect(deleteCover).toHaveBeenCalledWith('11111111-1111-4111-8111-111111111111');
    });
});
