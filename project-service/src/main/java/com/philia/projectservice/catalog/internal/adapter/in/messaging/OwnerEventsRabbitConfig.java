package com.philia.projectservice.catalog.internal.adapter.in.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Topology for the user events project-service consumes. The exchanges are declared by user-service
 * as well; redeclaring them with the same settings is a no-op. Each event has its own queue so the
 * payload type follows from the queue. Messages that exhaust their retries are dead-lettered to one
 * shared failed queue for manual inspection.
 */
@Configuration
class OwnerEventsRabbitConfig {

    static final String USER_EVENTS_EXCHANGE = "user.events";
    static final String DEAD_LETTER_EXCHANGE = "user.events.dlx";

    static final String ACCOUNT_CREATED_QUEUE = "project-service.account-created";
    static final String PROFILE_UPDATED_QUEUE = "project-service.user-profile-updated";
    static final String AVATAR_UPDATED_QUEUE = "project-service.user-avatar-updated";
    static final String FAILED_QUEUE = "project-service.user-events.failed";

    private static final String FAILED_ROUTING_KEY = "project-service.failed";

    @Bean
    Declarables ownerEventsTopology() {
        var userEvents = new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
        var deadLetter = new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);

        var declarables = new ArrayList<Declarable>(List.of(userEvents, deadLetter));
        addEventQueue(declarables, ACCOUNT_CREATED_QUEUE, "account.created", userEvents);
        addEventQueue(declarables, PROFILE_UPDATED_QUEUE, "user.profile.updated", userEvents);
        addEventQueue(declarables, AVATAR_UPDATED_QUEUE, "user.avatar.updated", userEvents);

        var failed = QueueBuilder.durable(FAILED_QUEUE).build();
        declarables.add(failed);
        declarables.add(BindingBuilder.bind(failed).to(deadLetter).with(FAILED_ROUTING_KEY));
        return new Declarables(declarables);
    }

    // The messages user-service publishes carry no Java type header, so the payload type is
    // inferred from the listener method parameter.
    @Bean
    MessageConverter userEventsMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    private static void addEventQueue(List<Declarable> declarables, String queueName, String routingKey,
                                      TopicExchange exchange) {
        var queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(FAILED_ROUTING_KEY)
                .build();
        declarables.add(queue);
        declarables.add(BindingBuilder.bind(queue).to(exchange).with(routingKey));
    }
}
