import { AmqpConnection, Nack, RabbitSubscribe } from '@golevelup/nestjs-rabbitmq';
import { Injectable, Logger } from '@nestjs/common';
import { Prisma } from '../generated/prisma/client.js';
import { AccountService } from '../account/account.service.js';

// Published by user-service when a user changes their display name. Only the
// fields this service needs; the shape is not shared code, so keep it in step
// with user-service's UserService.updateProfile by hand.
interface UserProfileUpdatedPayload {
    userId: string;
    displayName: string;
}

// Only the parts of the raw AMQP message this service reads.
interface AmqpMessage {
    properties: { headers?: { 'x-death'?: { queue: string; count: number }[] } };
}

const MAIN_QUEUE = 'auth-service.user-profile-updated';
const MAX_RETRIES = 3;

@Injectable()
export class ConsumerService {
    private readonly logger = new Logger(ConsumerService.name);

    constructor(
        private readonly accountService: AccountService,
        private readonly amqpConnection: AmqpConnection,
    ) {}

    // Keeps Account.displayName (unique here too) in step with user-service,
    // which owns the profile. Failed messages dead-letter to the retry queue
    // declared in AppRabbitMQModule and come back after its TTL.
    @RabbitSubscribe({
        exchange: 'user.events',
        routingKey: 'user.profile.updated',
        queue: MAIN_QUEUE,
        queueOptions: {
            durable: true,
            arguments: {
                'x-dead-letter-exchange': 'user.events.retry',
                'x-dead-letter-routing-key': 'user.profile.updated',
            },
        },
    })
    async handleUserProfileUpdated(payload: UserProfileUpdatedPayload, amqpMsg: AmqpMessage) {
        // A malformed message can never succeed, so drop it instead of retrying.
        const displayName = payload?.displayName?.trim();
        if (!payload?.userId || !displayName) {
            this.logger.warn(`Dropping malformed user.profile.updated: ${JSON.stringify(payload)}`);
            return;
        }

        try {
            // Idempotent: setting the same name again is a no-op, so a
            // redelivered message is safe.
            const updated = await this.accountService.updateDisplayName(payload.userId, displayName);

            if (updated === 0) {
                this.logger.warn(`Account ${payload.userId} not found, skipping display name update.`);
                return;
            }

            this.logger.log(`Updated display name for account ${payload.userId}`);
        } catch (err) {
            // The name is already taken by another account here. Retrying
            // cannot fix that, so log it for manual follow-up and ack.
            if (err instanceof Prisma.PrismaClientKnownRequestError && err.code === 'P2002') {
                this.logger.error(
                    `Display name "${displayName}" is already used by another account, cannot update ${payload.userId}.`,
                );
                return;
            }

            // Count only dead-letter hops from THIS queue: x-death also gets an
            // entry for the retry queue once a message starts bouncing.
            const deaths = amqpMsg.properties.headers?.['x-death'] ?? [];
            const retryCount = deaths.find((d) => d.queue === MAIN_QUEUE)?.count ?? 0;

            this.logger.error(
                `Attempt ${retryCount + 1} failed for account ${payload.userId}: ${(err as Error).message}`,
            );

            if (retryCount >= MAX_RETRIES) {
                this.logger.error(`Exceeded ${MAX_RETRIES} retries, routing to DLX: ${payload.userId}`);
                await this.amqpConnection.publish('user.events.dlx', 'user.profile.updated.failed', payload);
                return;
            }

            // Nack without requeue: dead-letters to user.events.retry, which
            // is what drives the retry cycle.
            return new Nack(false);
        }
    }

    // Terminal handler for messages that exhausted all retries. It never
    // throws and has no dead-letter config, so the message is acked and
    // processing stops for good.
    @RabbitSubscribe({
        exchange: 'user.events.dlx',
        routingKey: 'user.profile.updated.failed',
        queue: 'auth-service.user-profile-updated.failed',
        queueOptions: { durable: true },
    })
    handleFailedUserProfileUpdated(payload: UserProfileUpdatedPayload) {
        this.logger.error(`Message permanently failed, needs manual intervention: ${JSON.stringify(payload)}`);
        // TODO later: persist for inspection and alert, same as user-service.
    }
}
