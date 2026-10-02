import { Module } from '@nestjs/common';
import { RabbitMQModule } from '@golevelup/nestjs-rabbitmq';

// The single RabbitMQ connection for this service, shared by the outbox
// relay (publisher) and the consumer. RabbitMQModule.forRoot must only be
// called once -- a second call would open a second connection and redeclare
// the topology.
@Module({
    imports: [
        RabbitMQModule.forRoot({
            // All "topic" type, same as user-service, which declares them too
            // (redeclaring with identical settings is a no-op).
            exchanges: [
                // Where events are published and consumed.
                { name: 'user.events', type: 'topic' },

                // Failed messages are parked on a retry queue behind this
                // exchange before being routed back to user.events.
                { name: 'user.events.retry', type: 'topic' },

                // Messages that exhausted all retries end up here.
                { name: 'user.events.dlx', type: 'topic' },
            ],
            queues: [
                {
                    // Retry queue for user.profile.updated: messages wait out
                    // the TTL (the retry backoff), then dead-letter back to
                    // user.events for another attempt.
                    name: 'auth-service.user-profile-updated.retry',
                    exchange: 'user.events.retry',
                    routingKey: 'user.profile.updated',
                    createQueueIfNotExists: true,
                    options: {
                        durable: true,
                        arguments: {
                            'x-message-ttl': 5000,
                            'x-dead-letter-exchange': 'user.events',
                            'x-dead-letter-routing-key': 'user.profile.updated',
                        },
                    },
                },
            ],
            uri: process.env.RABBIT_MQ_URI ?? 'amqp://guest:guest@localhost:5672',
            connectionInitOptions: { wait: true },
        }),
    ],
    exports: [RabbitMQModule],
})
export class AppRabbitMQModule {}
