import { createHash } from 'node:crypto';
import { Injectable, UnsupportedMediaTypeException } from '@nestjs/common';
import { fileTypeFromBuffer } from 'file-type';
import sharp from 'sharp';
import type { ProfileImageKind } from './object-storage.service.js';

export interface ProcessedImageVariant {
    name: string;
    width: number;
    height: number;
    buffer: Buffer;
}

export interface ProcessedProfileImage {
    sha256: string;
    primaryVariantName: string;
    variants: ProcessedImageVariant[];
}

interface VariantSpec {
    name: string;
    width: number;
    height: number;
    primary?: boolean;
}

const ALLOWED_INPUT_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const MAX_INPUT_PIXELS = 40_000_000;

const VARIANT_SPECS: Record<ProfileImageKind, VariantSpec[]> = {
    avatar: [
        { name: '64x64', width: 64, height: 64 },
        { name: '256x256', width: 256, height: 256, primary: true },
    ],
    cover: [
        { name: '640x240', width: 640, height: 240 },
        { name: '1280x480', width: 1280, height: 480, primary: true },
    ],
};

@Injectable()
export class ProfileImageProcessorService {
    async process(kind: ProfileImageKind, input: Buffer): Promise<ProcessedProfileImage> {
        const detected = await fileTypeFromBuffer(input).catch(() => undefined);
        if (!detected || !ALLOWED_INPUT_MIME_TYPES.has(detected.mime)) {
            throw new UnsupportedMediaTypeException('Only JPEG, PNG, and WebP images are supported.');
        }

        try {
            const specs = VARIANT_SPECS[kind];
            const variants = await Promise.all(
                specs.map(async (spec): Promise<ProcessedImageVariant> => {
                    const { data, info } = await sharp(input, {
                        failOn: 'error',
                        limitInputPixels: MAX_INPUT_PIXELS,
                    })
                        .autoOrient()
                        .resize(spec.width, spec.height, {
                            fit: 'cover',
                            position: kind === 'avatar' ? 'attention' : 'centre',
                        })
                        // Sharp strips EXIF, GPS, XMP, and other metadata by
                        // default when writing a new image. Do not call any of
                        // the metadata-preservation methods here.
                        .webp({ quality: 82, effort: 4 })
                        .toBuffer({ resolveWithObject: true });

                    return {
                        name: spec.name,
                        width: info.width,
                        height: info.height,
                        buffer: data,
                    };
                }),
            );

            const primarySpec = specs.find((spec) => spec.primary);
            const primary = variants.find((variant) => variant.name === primarySpec?.name);
            if (!primarySpec || !primary) {
                throw new Error(`Primary ${kind} image variant is not configured.`);
            }

            return {
                sha256: createHash('sha256').update(primary.buffer).digest('hex'),
                primaryVariantName: primary.name,
                variants,
            };
        } catch (error) {
            if (error instanceof UnsupportedMediaTypeException) throw error;
            throw new UnsupportedMediaTypeException('The uploaded image is corrupt or cannot be decoded.');
        }
    }
}
