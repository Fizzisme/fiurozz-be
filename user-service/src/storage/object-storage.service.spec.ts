import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import { UnsupportedMediaTypeException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DeleteObjectsCommand, ListObjectsV2Command, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { ObjectStorageService } from './object-storage.service.js';

function file(buffer: Buffer): Express.Multer.File {
    return {
        fieldname: 'file',
        originalname: 'profile.png',
        encoding: '7bit',
        mimetype: 'image/png',
        size: buffer.length,
        buffer,
        destination: '',
        filename: '',
        path: '',
        stream: undefined as never,
    };
}

type AsyncMock = (...args: unknown[]) => Promise<unknown>;

describe('ObjectStorageService', () => {
    const send = jest.fn<AsyncMock>();
    const client = { send } as unknown as S3Client;
    const imageProcessor = { process: jest.fn<AsyncMock>() };
    const config = {
        get: jest.fn((key: string, fallback: string) => {
            const values: Record<string, string> = {
                S3_BUCKET: 'user-media',
                S3_PUBLIC_BASE_URL: 'http://localhost:9000/',
                S3_AUTO_CREATE_BUCKET: 'true',
                S3_PUBLIC_READ: 'true',
            };
            return values[key] ?? fallback;
        }),
    } as unknown as ConfigService;

    beforeEach(() => {
        const hash = 'a'.repeat(64);
        imageProcessor.process.mockReset().mockImplementation((kind: 'avatar' | 'cover') =>
            Promise.resolve({
                sha256: hash,
                primaryVariantName: kind === 'avatar' ? '256x256' : '1280x480',
                variants:
                    kind === 'avatar'
                        ? [
                              { name: '64x64', width: 64, height: 64, buffer: Buffer.from('small') },
                              { name: '256x256', width: 256, height: 256, buffer: Buffer.from('large') },
                          ]
                        : [
                              { name: '640x240', width: 640, height: 240, buffer: Buffer.from('small-cover') },
                              { name: '1280x480', width: 1280, height: 480, buffer: Buffer.from('large-cover') },
                          ],
            }),
        );
        send.mockReset().mockResolvedValue({});
    });

    it('stores normalized variants under one immutable SHA-256 group', async () => {
        const service = new ObjectStorageService(client, imageProcessor as never, config);

        const result = await service.uploadProfileImage('avatar', file(Buffer.from('source')), 5 * 1024 * 1024);

        expect(result.key).toMatch(/^profile-images\/avatar\/[a-f0-9]{64}\/256x256\.webp$/);
        expect(result.url).toBe(`http://localhost:9000/user-media/${result.key}`);
        expect(result.variants).toEqual({
            '64x64': expect.objectContaining({ width: 64, height: 64 }),
            '256x256': expect.objectContaining({ width: 256, height: 256, key: result.key }),
        });
        const puts = send.mock.calls
            .map(([command]) => command)
            .filter((command) => command instanceof PutObjectCommand);
        expect(puts).toHaveLength(2);
        expect(puts[0].input).toMatchObject({
            Bucket: 'user-media',
            ContentType: 'image/webp',
            ContentDisposition: 'inline',
            CacheControl: 'public, max-age=31536000, immutable',
        });
    });

    it('overwrites the same deterministic keys to refresh their GC grace period', async () => {
        const service = new ObjectStorageService(client, imageProcessor as never, config);

        const first = await service.uploadProfileImage('cover', file(Buffer.from('source')), 10 * 1024 * 1024);
        const second = await service.uploadProfileImage('cover', file(Buffer.from('source')), 10 * 1024 * 1024);

        expect(first.key).toBe(second.key);
        expect(
            send.mock.calls.map(([command]) => command).filter((command) => command instanceof PutObjectCommand),
        ).toHaveLength(4);
    });

    it('propagates image decoding failures without touching S3', async () => {
        imageProcessor.process.mockRejectedValue(new UnsupportedMediaTypeException('Invalid image'));
        const service = new ObjectStorageService(client, imageProcessor as never, config);

        await expect(
            service.uploadProfileImage('avatar', file(Buffer.from('not-an-image')), 1024),
        ).rejects.toBeInstanceOf(UnsupportedMediaTypeException);
        expect(send).not.toHaveBeenCalled();
    });

    it('deletes only old unreferenced image groups and retains every variant in a referenced group', async () => {
        const hashA = 'a'.repeat(64);
        const hashB = 'b'.repeat(64);
        const old = new Date('2026-09-01T00:00:00.000Z');
        const recent = new Date('2026-09-29T23:30:00.000Z');
        send.mockImplementation((command) => {
            if (command instanceof ListObjectsV2Command) {
                return Promise.resolve({
                    Contents: [
                        { Key: `profile-images/avatar/${hashA}/64x64.webp`, LastModified: old },
                        { Key: `profile-images/avatar/${hashA}/256x256.webp`, LastModified: old },
                        { Key: `profile-images/avatar/${hashB}/64x64.webp`, LastModified: old },
                        { Key: `profile-images/avatar/${hashB}/256x256.webp`, LastModified: old },
                        { Key: 'profile-images/recent.webp', LastModified: recent },
                    ],
                    IsTruncated: false,
                });
            }
            if (command instanceof DeleteObjectsCommand) {
                return Promise.resolve({ Deleted: command.input.Delete?.Objects });
            }
            return Promise.resolve({});
        });
        const service = new ObjectStorageService(client, imageProcessor as never, config);

        await expect(
            service.deleteUnreferencedProfileImages(
                [`profile-images/avatar/${hashA}/256x256.webp`],
                new Date('2026-09-29T00:00:00.000Z'),
            ),
        ).resolves.toEqual({ scanned: 5, deleted: 2 });

        const deletion = send.mock.calls
            .map(([command]) => command)
            .find((command) => command instanceof DeleteObjectsCommand) as DeleteObjectsCommand;
        expect(deletion.input.Delete?.Objects).toEqual([
            { Key: `profile-images/avatar/${hashB}/64x64.webp` },
            { Key: `profile-images/avatar/${hashB}/256x256.webp` },
        ]);
    });
});
