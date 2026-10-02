import { Module } from '@nestjs/common';
import { UserModule } from './user/user.module.js';
import { PrismaModule } from './prisma/prisma.module.js';
import { ConfigModule } from '@nestjs/config';
import { ConsumerModule } from './consumer/consumer.module.js';
import { FollowModule } from './follow/follow.module.js';
import { ScheduleModule } from '@nestjs/schedule';
import {OutboxEventModule} from "./outboxEvent/outbox-event.module.js";
@Module({
    imports: [
        ConfigModule.forRoot({
            isGlobal: true,
            envFilePath: '.env',
        }),
        ScheduleModule.forRoot(),
        UserModule,
        PrismaModule,
        ConsumerModule,
        FollowModule,
        OutboxEventModule,
    ],
})
export class AppModule {}
