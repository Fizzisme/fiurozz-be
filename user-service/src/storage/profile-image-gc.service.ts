import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Cron, CronExpression } from '@nestjs/schedule';
import { PrismaService } from '../prisma/prisma.service.js';
import { ObjectStorageService } from './object-storage.service.js';

export interface ProfileImageGcResult {
    scanned: number;
    deleted: number;
}

@Injectable()
export class ProfileImageGcService {
    private readonly logger = new Logger(ProfileImageGcService.name);
    private readonly enabled: boolean;
    private readonly gracePeriodMs: number;

    constructor(
        private readonly prisma: PrismaService,
        private readonly objectStorage: ObjectStorageService,
        config: ConfigService,
    ) {
        this.enabled = config.get<string>('PROFILE_IMAGE_GC_ENABLED', 'true') === 'true';
        const configuredHours = Number(config.get<string>('PROFILE_IMAGE_GC_GRACE_HOURS', '24'));
        const graceHours = Number.isFinite(configuredHours) && configuredHours >= 1 ? configuredHours : 24;
        this.gracePeriodMs = graceHours * 60 * 60 * 1000;
    }

    @Cron(CronExpression.EVERY_DAY_AT_3AM, {
        name: 'profile-image-garbage-collection',
        timeZone: 'UTC',
        waitForCompletion: true,
    })
    async runScheduledCleanup(): Promise<void> {
        if (!this.enabled) return;

        try {
            const result = await this.collectGarbage();
            this.logger.log(`Profile image GC scanned ${result.scanned} objects and deleted ${result.deleted}.`);
        } catch (error) {
            const message = error instanceof Error ? error.stack : String(error);
            this.logger.error('Profile image garbage collection failed.', message);
        }
    }

    async collectGarbage(now = new Date()): Promise<ProfileImageGcResult> {
        if (!this.enabled) return { scanned: 0, deleted: 0 };

        const profiles = await this.prisma.userProfile.findMany({
            select: { avatarObjectKey: true, coverObjectKey: true },
        });
        const referencedKeys = profiles.flatMap(({ avatarObjectKey, coverObjectKey }) =>
            [avatarObjectKey, coverObjectKey].filter((key): key is string => Boolean(key)),
        );
        const cutoff = new Date(now.getTime() - this.gracePeriodMs);

        return this.objectStorage.deleteUnreferencedProfileImages(referencedKeys, cutoff);
    }
}
