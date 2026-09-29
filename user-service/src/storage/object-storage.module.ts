import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { S3Client } from '@aws-sdk/client-s3';
import { ObjectStorageService } from './object-storage.service.js';
import { ProfileImageGcService } from './profile-image-gc.service.js';
import { ProfileImageProcessorService } from './profile-image-processor.service.js';
import { S3_CLIENT } from './storage.constants.js';

@Module({
    providers: [
        {
            provide: S3_CLIENT,
            inject: [ConfigService],
            useFactory: (config: ConfigService) =>
                new S3Client({
                    endpoint: config.get<string>('S3_ENDPOINT', 'http://localhost:9000'),
                    region: config.get<string>('S3_REGION', 'us-east-1'),
                    forcePathStyle: config.get<string>('S3_FORCE_PATH_STYLE', 'true') === 'true',
                    credentials: {
                        accessKeyId: config.get<string>('S3_ACCESS_KEY', 'minioadmin'),
                        secretAccessKey: config.get<string>('S3_SECRET_KEY', 'minioadmin'),
                    },
                }),
        },
        ProfileImageProcessorService,
        ObjectStorageService,
        ProfileImageGcService,
    ],
    exports: [ObjectStorageService],
})
export class ObjectStorageModule {}
