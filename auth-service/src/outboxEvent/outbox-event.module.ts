import {Module} from "@nestjs/common";
import {OutboxEventService} from "./outbox-event.service.js";
import {OutboxRelayService} from "./outbox-relay.service.js";
import {RabbitMQModule} from "@golevelup/nestjs-rabbitmq";

@Module({
    imports: [
        RabbitMQModule.forRoot({
            exchanges: [{
                name: 'user.events',
                type: 'topic'
            }],
            uri: process.env.RABBIT_MQ_URI ?? 'amqp://guest:guest@localhost:5672',
            connectionInitOptions: {wait: true}
        }),
    ],
    providers: [OutboxEventService, OutboxRelayService],
    exports: [OutboxEventService],
})
export class OutboxEventModule{}