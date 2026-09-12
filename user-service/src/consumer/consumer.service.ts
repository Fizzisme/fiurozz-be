import { RabbitSubscribe, AmqpConnection, Nack } from '@golevelup/nestjs-rabbitmq';
import { PrismaService } from "../prisma/prisma.service.js";
import { Injectable, Logger } from "@nestjs/common";
import {Gender} from "../generated/prisma/enums.js";

interface AccountCreatedPayload {
    id: string;
    email: string;
    fullName: string;
    displayName: string;
    gender: Gender;
    birthday: Date;
    country: string;
    avatarUrl: string;
}

const MAX_RETRIES = 3;

@Injectable()
export class ConsumerService {
    private readonly logger = new Logger(ConsumerService.name);

    constructor(
        private readonly prisma: PrismaService,
        private readonly amqpConnection: AmqpConnection,
    ) {}

    // Main consumer for "account.created" events. Queue is bound to the
    // main exchange, and dead-letters to the retry exchange on Nack —
    // i.e. failed messages don't retry in-place, they get parked on the
    // retry queue (with its TTL, see RabbitMQModule config) before being
    // routed back here automatically.
    @RabbitSubscribe({
        exchange: 'user.events',
        routingKey: 'account.created',
        queue: 'user-service.account-created',
        queueOptions: {
            durable: true,
            arguments: {
                'x-dead-letter-exchange': 'user.events.retry',
                'x-dead-letter-routing-key': 'account.created',
            },
        },
    })
    async handleAccountCreated(payload: AccountCreatedPayload, amqpMsg: any) {
        try {

            // Idempotency check: this event may be redelivered (retry,
            // consumer crash before ack, etc.), so skip silently if the
            // user record already exists instead of erroring on a
            // duplicate primary key.
            const existing = await this.prisma.user.findUnique({
                where: { id: payload.id },
            });

            if (existing) {
                this.logger.log(`User ${payload.id} already exists, skipping.`);
                return;
            }

            await this.prisma.user.create({
                data: {
                    id: payload.id,
                    profile: {
                        create: {
                            email: payload.email,
                            displayName: payload.displayName,
                            fullName: payload.fullName,
                            location: payload.country,
                            gender: payload.gender,
                            birthday: payload.birthday,
                            avatarUrl: payload.avatarUrl,
                        },
                    },
                    settings: { create: {} },
                },
            });

            this.logger.log(`Created user for account ${payload.id}`);

            // Returning void here causes @golevelup/nestjs-rabbitmq to
            // auto-ack the message — no explicit ack call needed.
        } catch (err) {

            // Count only dead-letter hops that came from THIS queue
            // (the main queue). x-death accumulates one entry per queue
            // a message has been dead-lettered from, so once this message
            // starts bouncing between main <-> retry, there will be a
            // separate x-death entry for the retry queue too — that one
            // must NOT be counted here, or retries would be under-counted.
            const deaths = amqpMsg.properties.headers?.['x-death'] ?? [];
            const mainQueueDeath = deaths.find(
                (d: any) => d.queue === 'user-service.account-created',
            );
            const retryCount = mainQueueDeath?.count ?? 0;

            this.logger.error(
                `Processing attempt ${retryCount + 1} failed for account ${payload.id}: ${err.message}`,
            );

            if (retryCount >= MAX_RETRIES) {
                this.logger.error(
                    `Exceeded ${MAX_RETRIES} retries, routing to DLX: ${payload.id}`,
                );

                // Publish directly to the dead-letter exchange with a
                // distinct routing key, so it lands in the dedicated
                // "failed" queue below instead of re-entering the normal
                // processing flow.
                await this.amqpConnection.publish(
                    'user.events.dlx',
                    'account.created.failed',
                    payload,
                );
                return;
            }

            // Nack without requeue: RabbitMQ routes this to the queue's
            // configured dead-letter exchange (user.events.retry above),
            // not back into this same queue — this is what drives the
            // retry cycle, not an application-level retry loop.
            return new Nack(false);
        }
    }

    // Terminal handler for messages that exhausted all retries. This is
    // intentionally NOT wired back into the retry cycle — no dead-letter
    // config on this queue, and the handler never throws — so a message
    // that lands here is acked and processing stops for good.
    @RabbitSubscribe({
        exchange: 'user.events.dlx',
        routingKey: 'account.created.failed',
        queue: 'user-service.account-created.failed',
        queueOptions: { durable: true },
    })
    async handleFailedAccountCreated(payload: AccountCreatedPayload) {
        // Do not throw here — throwing would leave this message stuck
        // retrying within this same queue (no DLX configured to escape
        // to), unlike the main handler above which has somewhere to go.
        this.logger.error(
            `Message permanently failed, needs manual intervention: ${JSON.stringify(payload)}`,
        );
        // TODO later: persist to a dedicated table for inspection, and
        // send an alert (Slack/email) so this isn't only visible in logs.
    }
}