package com.fiap.sast.messaging;

import com.fiap.sast.persistence.AnalysisOutboxEvent;
import com.fiap.sast.persistence.AnalysisOutboxRepository;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("!worker")
public class AnalysisOutboxPublisher {
    private final AnalysisOutboxRepository events; private final RabbitTemplate rabbit;
    public AnalysisOutboxPublisher(AnalysisOutboxRepository events, RabbitTemplate rabbit) { this.events = events; this.rabbit = rabbit; }
    @Scheduled(fixedDelayString = "${sast.rabbit.publisher-delay-ms:1000}") @Transactional
    public void publishReady() { var ready = events.findReady(Instant.now()); ready.forEach(this::publish); }
    private void publish(AnalysisOutboxEvent event) {
        try {
            var confirmation = new CorrelationData(event.id.toString());
            rabbit.convertAndSend(RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY, event.analysisId.toString(), confirmation);
            var result = confirmation.getFuture().get(10, TimeUnit.SECONDS);
            if (!result.isAck()) throw new IllegalStateException("RabbitMQ rejeitou a publicação");
            events.markPublished(event.id, Instant.now());
        } catch (Exception failure) {
            event.attempts++; event.lastError = "RabbitMQ indisponível ou publicação não confirmada";
            event.availableAt = Instant.now().plusSeconds(Math.min(60, 5L * event.attempts)); events.save(event);
        }
    }
}
