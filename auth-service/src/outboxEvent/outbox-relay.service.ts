import { Injectable, Logger } from '@nestjs/common';
import { Interval } from '@nestjs/schedule';
import { PrismaService } from '../prisma/prisma.service.js';
import { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import {OutboxEventService} from "./outbox-event.service.js";

const MAX_ATTEMPTS = 3

// Implements the relay (publish) side of the Outbox pattern: polls
// the outbox table on a fixed interval and publishes any pending
// events to RabbitMQ, marking each as processed on success.
@Injectable()
export class OutboxRelayService {
    private readonly logger = new Logger(OutboxRelayService.name);

    private isRunning = false;

    constructor(
        private readonly prisma: PrismaService,
        private readonly amqpConnection: AmqpConnection,
        private readonly outboxEvent: OutboxEventService
    ) {}

    @Interval(2000)
    async relay() {

        if (this.isRunning) return;
        this.isRunning = true;

        try {
            const events = await this.outboxEvent.findPublishable();

            for (const event of events) {

                try {
                    await this.amqpConnection.publish(
                        'user.events',
                        event.eventType,
                        event.payload,
                        { persistent: true },
                    );

                    await this.prisma.outboxEvent.update({
                        where: { id: event.id },
                        data: { processedAt: new Date(), status: 'success' },
                    });

                    this.logger.log(`Published event ${event.id} (${event.eventType})`);
                } catch (err) {

                    const newAttempts = event.attempts + 1;
                    const exhausted = newAttempts >= MAX_ATTEMPTS;


                    await this.prisma.outboxEvent.update({
                        where: { id: event.id },
                        data: { attempts: { increment: 1 }, status: exhausted ? 'failed' : 'pending', },
                    });

                    if (exhausted) {
                        this.logger.error(
                            `Event ${event.id} exceeded ${MAX_ATTEMPTS} attempts, marked as failed — needs manual review`,
                        );
                        // TODO later: persist to a dedicated view/table for
                        // inspection, and send an alert (Slack/email), same
                        // as the TODO in ConsumerService.handleFailedAccountCreated.
                    } else {
                        this.logger.error(`Failed to publish event ${event.id}: ${err.message}`);
                    }
                }
            }
        } finally {
            // Runs once, after the ENTIRE batch has been processed —
            // this is what actually prevents overlapping ticks, not a
            // per-event reset.
            this.isRunning = false;
        }
    }
}