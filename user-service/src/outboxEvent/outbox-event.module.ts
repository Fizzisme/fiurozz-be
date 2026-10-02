import { Module } from '@nestjs/common';
import { OutboxEventService } from './outbox-event.service.js';
import { OutboxRelayService } from './outbox-relay.service.js';
import { AppRabbitMQModule } from '../app-rabbitmq/app-rabbitmq.module.js';

@Module({
    imports: [
        AppRabbitMQModule,
    ],
    providers: [OutboxEventService, OutboxRelayService],
    exports: [OutboxEventService],
})
export class OutboxEventModule {}
