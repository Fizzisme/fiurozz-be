import { Module } from '@nestjs/common';
import { PrismaModule } from '../prisma/prisma.module.js';
import { ConsumerService } from './consumer.service.js';
import { AppRabbitMQModule } from '../app-rabbitmq/app-rabbitmq.module.js';

@Module({
    imports: [
        AppRabbitMQModule,
        PrismaModule,
    ],
    providers: [ConsumerService],
})
export class ConsumerModule {}
