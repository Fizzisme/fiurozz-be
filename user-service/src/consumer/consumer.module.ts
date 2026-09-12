import { Module } from '@nestjs/common';
import { RabbitMQModule } from '@golevelup/nestjs-rabbitmq';
import {PrismaModule} from "../prisma/prisma.module.js";
import {ConsumerService} from "./consumer.service.js";

@Module({
    imports: [
        PrismaModule,

        // Configures the RabbitMQ connection, exchanges, and queues for
        // this service. Implements a retry + dead-letter architecture:
        // failed messages are parked on a retry queue with a TTL, then
        // automatically routed back to the main exchange for reprocessing.
        RabbitMQModule.forRoot({

            // Exchanges this service publishes to / binds queues on.
            // All are "topic" type, allowing routing keys with wildcards
            // (e.g. "account.*") if needed later.
            exchanges: [

                // Main exchange: where events are normally published and
                // consumed from (e.g. "account.created").
                { name: 'user.events', type: 'topic' },

                // Retry exchange: messages that failed processing are
                // republished here (typically by your consumer's error
                // handler) instead of the main exchange, so they land in
                // the dedicated retry queue below instead of being
                // reprocessed immediately.
                { name: 'user.events.retry', type: 'topic' },

                // Dead-letter exchange: intended destination for messages
                // that exhaust all retry attempts and should no longer be
                // reprocessed automatically (kept for now for visibility /
                // manual inspection, no queue is bound to it below yet).
                { name: 'user.events.dlx', type: 'topic' },
            ],
            queues: [
                {
                    // Retry queue for the "account.created" event. Messages
                    // land here after a failed processing attempt, wait out
                    // the TTL below, then are automatically routed back to
                    // the main exchange for another processing attempt.
                    name: 'user-service.account-created.retry',

                    // Bound to the retry exchange (not the main one) with
                    // the same routing key, so only retried messages for
                    // this event type end up here.
                    exchange: 'user.events.retry',
                    routingKey: 'account.created',
                    createQueueIfNotExists: true,
                    options: {
                        durable: true,
                        arguments: {
                            // Delay before retry: messages sit here for 5s
                            // (dead but not discarded) before RabbitMQ
                            // automatically expires and dead-letters them —
                            // this TTL is what implements the retry backoff,
                            // not application-level setTimeout/sleep.
                            'x-message-ttl': 5000,
                            'x-dead-letter-exchange': 'user.events',
                            'x-dead-letter-routing-key': 'account.created',
                        },
                    },
                },
            ],
            uri: process.env.RABBITMQ_URL ?? 'amqp://guest:guest@localhost:5672',
            connectionInitOptions: { wait: true },
        }),
    ],
    providers: [ConsumerService],
})
export class ConsumerModule {}