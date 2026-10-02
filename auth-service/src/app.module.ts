import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthModule } from './auth/auth.module.js';
import { PrismaModule } from './prisma/prisma.module.js';
import { ScheduleModule } from '@nestjs/schedule';
import { ConsumerModule } from './consumer/consumer.module.js';
@Module({
    imports: [
        ScheduleModule.forRoot(),
        ConfigModule.forRoot({
            isGlobal: true,
            envFilePath: '.env',
        }),
        AuthModule,
        PrismaModule,
        ConsumerModule,
    ],
})
export class AppModule {}
