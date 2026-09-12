import {Injectable} from "@nestjs/common";
import {PrismaService} from "../prisma/prisma.service.js";
import {uuidv7} from "uuidv7";

// Implements the write side of the Outbox pattern: events are written
// to this table within the SAME database transaction as the business
// data they describe (see OauthAccountService), guaranteeing an event
// is never lost even if the process crashes right after committing.
// OutboxRelayService is responsible for actually publishing these
// rows to RabbitMQ afterward.
@Injectable()
export class OutboxEventService{
    constructor(
        private readonly prisma: PrismaService,
    ) {}

    // Returns an unexecuted Prisma query (not awaited here) so it can
    // be composed into a $transaction([...]) array by the caller,
    // rather than running as its own independent transaction.
    create(
        accountId: string,
        eventType: string,
        payload: any,
    ){

        const outboxEventId = uuidv7();

        return this.prisma.outboxEvent.create({
            data: {
                id: outboxEventId,
                aggregateId: accountId,
                eventType,
                payload
            }
        })
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
        })
    }
}