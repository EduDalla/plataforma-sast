package com.fiap.sast.messaging;

import com.fiap.sast.persistence.AnalysisOutboxEvent;
import com.fiap.sast.persistence.AnalysisOutboxRepository;
import com.fiap.sast.persistence.AnalysisRepository;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("worker")
public class AnalysisLeaseReconciler {
    private final AnalysisRepository analyses; private final AnalysisOutboxRepository events;
    public AnalysisLeaseReconciler(AnalysisRepository analyses, AnalysisOutboxRepository events) { this.analyses = analyses; this.events = events; }
    @Scheduled(fixedDelayString = "${sast.rabbit.reconciler-delay-ms:10000}") @Transactional
    public void reconcile() {
        var now = Instant.now();
        for (var analysis : analyses.findExpiredLeases(now)) {
            if (analysis.nextAttemptAt != null && analysis.nextAttemptAt.isAfter(now)) continue;
            if (!events.existsByAnalysisIdAndPublishedAtIsNull(analysis.id)) { var e = new AnalysisOutboxEvent(); e.analysisId = analysis.id; events.save(e); }
            analysis.leaseOwner = null; analysis.leaseUntil = null; analyses.save(analysis);
        }
    }
}
