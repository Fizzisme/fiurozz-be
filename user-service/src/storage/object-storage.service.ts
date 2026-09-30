import { BadRequestException, Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
    CreateBucketCommand,
    DeleteObjectsCommand,
    HeadBucketCommand,
    ListObjectsV2Command,
    PutBucketPolicyCommand,
    PutObjectCommand,
    S3Client,
} from '@aws-sdk/client-s3';
import { ProfileImageProcessorService } from './profile-image-processor.service.js';
import { S3_CLIENT } from './storage.constants.js';

export type ProfileImageKind = 'avatar' | 'cover';

export interface StoredImageVariant {
    key: string;
    url: string;
    width: number;
    height: number;
    size: number;
}

export interface StoredProfileImage {
    key: string;
    url: string;
    sha256: string;
    contentType: string;
    size: number;
    variants: Record<string, StoredImageVariant>;
}

export interface ProfileImageGarbageCollectionResult {
    scanned: number;
    deleted: number;
}

@Injectable()
export class ObjectStorageService {
    private readonly bucket: string;
    private readonly publicBaseUrl: string;
    private readonly autoCreateBucket: boolean;
    private readonly publicRead: boolean;
    private bucketReady?: Promise<void>;
    private mutationTail: Promise<void> = Promise.resolve();

    constructor(
        @Inject(S3_CLIENT) private readonly client: S3Client,
        private readonly imageProcessor: ProfileImageProcessorService,
        config: ConfigService,
    ) {
        this.bucket = config.get<string>('S3_BUCKET', 'user-media');
        this.publicBaseUrl = config.get<string>('S3_PUBLIC_BASE_URL', 'http://localhost:9000').replace(/\/$/, '');
        this.autoCreateBucket = config.get<string>('S3_AUTO_CREATE_BUCKET', 'true') === 'true';
        this.publicRead = config.get<string>('S3_PUBLIC_READ', 'true') === 'true';
    }

    async uploadProfileImage(
        kind: ProfileImageKind,
        file: Express.Multer.File | undefined,
        maxBytes: number,
    ): Promise<StoredProfileImage> {
        if (!file?.buffer?.length) {
            throw new BadRequestException('Image file is required in the "file" form field.');
        }
        if (file.buffer.length > maxBytes) {
            throw new BadRequestException(`Image must not exceed ${Math.floor(maxBytes / 1024 / 1024)} MB.`);
        }

        const processed = await this.imageProcessor.process(kind, file.buffer);

        const variants: Record<string, StoredImageVariant> = {};
        await this.withMutationLock(async () => {
            await this.ensureBucket();
            for (const variant of processed.variants) {
                const key = `profile-images/${kind}/${processed.sha256}/${variant.name}.webp`;

                // Re-put deterministic keys even when they already exist. S3
                // still stores only one object per key, while LastModified is
                // refreshed so a concurrent grace-period GC cannot treat an
                // old, deduplicated blob as abandoned during this upload.
                await this.client.send(
                    new PutObjectCommand({
                        Bucket: this.bucket,
                        Key: key,
                        Body: variant.buffer,
                        ContentLength: variant.buffer.length,
                        ContentType: 'image/webp',
                        ContentDisposition: 'inline',
                        CacheControl: 'public, max-age=31536000, immutable',
                        Metadata: {
                            sha256: processed.sha256,
                            imagekind: kind,
                            variant: variant.name,
                        },
                    }),
                );

                variants[variant.name] = {
                    key,
                    url: this.toPublicUrl(key),
                    width: variant.width,
                    height: variant.height,
                    size: variant.buffer.length,
                };
            }
        });

        const primary = variants[processed.primaryVariantName];
        if (!primary) throw new Error(`Processed ${kind} image has no primary variant.`);

        return {
            key: primary.key,
            url: primary.url,
            sha256: processed.sha256,
            contentType: 'image/webp',
            size: primary.size,
            variants,
        };
    }

    async deleteUnreferencedProfileImages(
        referencedKeys: readonly string[],
        olderThan: Date,
    ): Promise<ProfileImageGarbageCollectionResult> {
        return this.withMutationLock(async () => {
            const referencedGroups = new Set(referencedKeys.map((key) => this.profileImageGroupKey(key)));
            const objects = await this.listProfileImageObjects();
            const keysToDelete = objects
                .filter(
                    ({ key, lastModified }) =>
                        lastModified.getTime() < olderThan.getTime() &&
                        !referencedGroups.has(this.profileImageGroupKey(key)),
                )
                .map(({ key }) => key);

            let deleted = 0;
            for (let index = 0; index < keysToDelete.length; index += 1_000) {
                const batch = keysToDelete.slice(index, index + 1_000);
                const result = await this.client.send(
                    new DeleteObjectsCommand({
                        Bucket: this.bucket,
                        Delete: { Objects: batch.map((Key) => ({ Key })) },
                    }),
                );
                if (result.Errors?.length) {
                    throw new Error(`S3 failed to delete ${result.Errors.length} profile image objects.`);
                }
                deleted += result.Deleted?.length ?? batch.length;
            }

            return { scanned: objects.length, deleted };
        });
    }

    private async listProfileImageObjects(): Promise<Array<{ key: string; lastModified: Date }>> {
        const objects: Array<{ key: string; lastModified: Date }> = [];
        let continuationToken: string | undefined;

        try {
            do {
                const page = await this.client.send(
                    new ListObjectsV2Command({
                        Bucket: this.bucket,
                        Prefix: 'profile-images/',
                        ContinuationToken: continuationToken,
                    }),
                );
                for (const object of page.Contents ?? []) {
                    if (object.Key && object.LastModified) {
                        objects.push({ key: object.Key, lastModified: object.LastModified });
                    }
                }
                continuationToken = page.IsTruncated ? page.NextContinuationToken : undefined;
            } while (continuationToken);
        } catch (error) {
            if (this.isMissingBucket(error)) return [];
            throw error;
        }

        return objects;
    }

    private profileImageGroupKey(key: string): string {
        const match = /^(profile-images\/(?:avatar|cover)\/[a-f0-9]{64})\//.exec(key);
        return match?.[1] ?? key;
    }

    private async ensureBucket(): Promise<void> {
        if (!this.autoCreateBucket) return;

        this.bucketReady ??= this.createBucketIfMissing();
        try {
            await this.bucketReady;
        } catch (error) {
            this.bucketReady = undefined;
            throw error;
        }
    }

    private async createBucketIfMissing(): Promise<void> {
        try {
            await this.client.send(new HeadBucketCommand({ Bucket: this.bucket }));
        } catch (error) {
            if (!this.isMissingBucket(error)) throw error;
            try {
                await this.client.send(new CreateBucketCommand({ Bucket: this.bucket }));
            } catch (createError) {
                const candidate = createError as { name?: string };
                if (candidate.name !== 'BucketAlreadyExists' && candidate.name !== 'BucketAlreadyOwnedByYou') {
                    throw createError;
                }
            }
        }

        if (this.publicRead) {
            await this.client.send(
                new PutBucketPolicyCommand({
                    Bucket: this.bucket,
                    Policy: JSON.stringify({
                        Version: '2012-10-17',
                        Statement: [
                            {
                                Sid: 'PublicProfileImageRead',
                                Effect: 'Allow',
                                Principal: '*',
                                Action: ['s3:GetObject'],
                                Resource: [`arn:aws:s3:::${this.bucket}/profile-images/*`],
                            },
                        ],
                    }),
                }),
            );
        }
    }

    private isMissingBucket(error: unknown): boolean {
        const candidate = error as { name?: string; $metadata?: { httpStatusCode?: number } };
        return (
            candidate.name === 'NotFound' ||
            candidate.name === 'NoSuchBucket' ||
            candidate.$metadata?.httpStatusCode === 404
        );
    }

    private async withMutationLock<T>(operation: () => Promise<T>): Promise<T> {
        const previous = this.mutationTail;
        let release!: () => void;
        this.mutationTail = new Promise<void>((resolve) => {
            release = resolve;
        });

        await previous;
        try {
            return await operation();
        } finally {
            release();
        }
    }

    private toPublicUrl(key: string): string {
        const encodedKey = key
            .split('/')
            .map((part) => encodeURIComponent(part))
            .join('/');
        return `${this.publicBaseUrl}/${encodeURIComponent(this.bucket)}/${encodedKey}`;
    }
}
