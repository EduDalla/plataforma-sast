package com.fiap.sast.messaging;

import com.fiap.sast.analysis.AnalysisJobService;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("worker")
public class AnalysisWorkerListener {
    private final AnalysisJobService jobs; private final String workerId = "worker-" + UUID.randomUUID();
    public AnalysisWorkerListener(AnalysisJobService jobs) { this.jobs = jobs; }
    @RabbitListener(queues = RabbitTopology.QUEUE, ackMode = "MANUAL", concurrency = "2")
    public void consume(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag(); UUID id;
        try { id = UUID.fromString(new String(message.getBody(), StandardCharsets.UTF_8)); }
        catch (RuntimeException invalid) { channel.basicReject(tag, false); return; }
        if (!jobs.claim(id, workerId)) { channel.basicAck(tag, false); return; }
        channel.basicAck(tag, false); jobs.processClaimed(id, workerId);
    }
}
