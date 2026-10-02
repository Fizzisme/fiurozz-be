import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service.js';
import { uuidv7 } from 'uuidv7';
import { Prisma } from '../generated/prisma/client.js';

// Implements the write side of the Outbox pattern: events are written
// to this table within the SAME database transaction as the business
// data they describe (see UserService.updateProfile), guaranteeing an
// event is never lost even if the process crashes right after committing.
// OutboxRelayService is responsible for actually publishing these
// rows to RabbitMQ afterward.
@Injectable()
export class OutboxEventService {
    constructor(private readonly prisma: PrismaService) {}

    // Pass the `tx` of an interactive $transaction as `client` so the
    // event commits or rolls back together with the business write; the
    // default (this.prisma) runs as its own independent query, which
    // loses that guarantee unless the caller batches it in
    // $transaction([...]).
    create(
        userId: string,
        eventType: string,
        payload: Prisma.InputJsonValue,
        client: Prisma.TransactionClient = this.prisma,
    ) {
        const outboxEventId = uuidv7();

        return client.outboxEvent.create({
            data: {
                id: outboxEventId,
                aggregateId: userId,
                eventType,
                payload,
            },
        });
    }

    // Fetches the next batch of publishable events: still pending AND
    // under the retry limit, oldest first. Filtering "status" and
    // "attempts" here (not in the caller) keeps events that already
    // exhausted retries from clogging every batch — otherwise they'd
    // keep getting fetched (processedAt is still null) and crowd out
    // genuinely new events behind them in the queue.
    async findPublishable() {
        return this.prisma.outboxEvent.findMany({
            where: {
                status: 'pending',
                processedAt: null,
            },
            orderBy: { createdAt: 'asc' },
            take: 50,
        });
    }
}
