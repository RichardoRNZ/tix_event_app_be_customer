package com.tixevents.app.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration 
public class RabbitConfig {
    public static final String EXCHANGE = "ticketing.events";
    public static final String DLX = "ticketing.dlx";

    @Bean TopicExchange eventsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean
    Declarables paymentQueues(TopicExchange eventsExchange, DirectExchange deadLetterExchange) {
        return consumerQueue(eventsExchange, deadLetterExchange,
            "payment.booking-created", "booking.created");
    }

    @Bean
    Declarables bookingQueues(TopicExchange eventsExchange, DirectExchange deadLetterExchange) {
        return consumerQueue(eventsExchange, deadLetterExchange,
            "booking.payment-result", "payment.completed", "payment.failed");
    }

    @Bean
    Declarables notificationQueues(TopicExchange eventsExchange, DirectExchange deadLetterExchange) {
        return consumerQueue(eventsExchange, deadLetterExchange,
            "notification.events", "payment.completed", "otp.requested");
    }

    private Declarables consumerQueue(TopicExchange ex, DirectExchange dlx,
                                      String name, String... routingKeys) {
        Queue queue = QueueBuilder.durable(name)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(name + ".dlq")
            .build();
        Queue dlq = QueueBuilder.durable(name + ".dlq").build();

        List<Declarable> all = new ArrayList<>();
        all.add(queue);
        all.add(dlq);
        all.add(BindingBuilder.bind(dlq).to(dlx).with(name + ".dlq"));
        for (String key : routingKeys) {
            all.add(BindingBuilder.bind(queue).to(ex).with(key));
        }
        return new Declarables(all);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
