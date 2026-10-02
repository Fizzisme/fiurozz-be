import { Module } from '@nestjs/common';
import { AccountModule } from '../account/account.module.js';
import { AppRabbitMQModule } from '../app-rabbitmq/app-rabbitmq.module.js';
import { ConsumerService } from './consumer.service.js';

@Module({
    imports: [AppRabbitMQModule, AccountModule],
    providers: [ConsumerService],
})
export class ConsumerModule {}
