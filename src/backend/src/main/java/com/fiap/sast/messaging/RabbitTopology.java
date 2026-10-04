package com.fiap.sast.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitTopology {
    public static final String EXCHANGE = "sast.analysis";
    public static final String ROUTING_KEY = "analysis.requested";
    public static final String QUEUE = "sast.analysis.requested";
    public static final String DLQ = "sast.analysis.requested.dlq";
    @Bean DirectExchange analysisExchange() { return new DirectExchange(EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(EXCHANGE + ".dlx", true, false); }
    @Bean Queue analysisQueue() { return QueueBuilder.durable(QUEUE).deadLetterExchange(EXCHANGE + ".dlx").deadLetterRoutingKey(DLQ).build(); }
    @Bean Queue analysisDlq() { return QueueBuilder.durable(DLQ).build(); }
    @Bean Binding analysisBinding(Queue analysisQueue, @Qualifier("analysisExchange") DirectExchange analysisExchange) { return BindingBuilder.bind(analysisQueue).to(analysisExchange).with(ROUTING_KEY); }
    @Bean Binding deadLetterBinding(Queue analysisDlq, @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) { return BindingBuilder.bind(analysisDlq).to(deadLetterExchange).with(DLQ); }
    @Bean RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        var template = new RabbitTemplate(connectionFactory); template.setMessageConverter(new SimpleMessageConverter()); template.setMandatory(true); return template;
    }
}
