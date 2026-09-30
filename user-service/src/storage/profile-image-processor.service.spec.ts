import { describe, expect, it } from '@jest/globals';
import { UnsupportedMediaTypeException } from '@nestjs/common';
import sharp from 'sharp';
import { ProfileImageProcessorService } from './profile-image-processor.service.js';

describe('ProfileImageProcessorService', () => {
    const service = new ProfileImageProcessorService();

    it('creates metadata-free WebP avatar variants at standard dimensions', async () => {
        const input = await sharp({
            create: { width: 400, height: 300, channels: 3, background: '#336699' },
        })
            .jpeg()
            .withMetadata({ orientation: 6 })
            .toBuffer();

        const result = await service.process('avatar', input);

        expect(result.primaryVariantName).toBe('256x256');
        expect(result.sha256).toMatch(/^[a-f0-9]{64}$/);
        expect(result.variants.map(({ name, width, height }) => ({ name, width, height }))).toEqual([
            { name: '64x64', width: 64, height: 64 },
            { name: '256x256', width: 256, height: 256 },
        ]);
        for (const variant of result.variants) {
            const metadata = await sharp(variant.buffer).metadata();
            expect(metadata.format).toBe('webp');
            expect(metadata.exif).toBeUndefined();
            expect(metadata.xmp).toBeUndefined();
        }
    });

    it('creates cover variants at the configured wide aspect ratio', async () => {
        const input = await sharp({
            create: { width: 1200, height: 800, channels: 3, background: '#663399' },
        })
            .png()
            .toBuffer();

        const result = await service.process('cover', input);

        expect(result.variants.map(({ name, width, height }) => ({ name, width, height }))).toEqual([
            { name: '640x240', width: 640, height: 240 },
            { name: '1280x480', width: 1280, height: 480 },
        ]);
    });

    it('rejects bytes that are not a supported decodable image', async () => {
        await expect(service.process('avatar', Buffer.from('not-an-image'))).rejects.toBeInstanceOf(
            UnsupportedMediaTypeException,
        );
    });
});
