package com.fiap.sast.web;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/** Mantém apenas respostas finais, limitadas por tempo e tamanho, na memória da API. */
@Component
public final class CompletedResultCache {
    static final Duration TTL = Duration.ofMinutes(15);
    static final long MAX_WEIGHT = 32L * 1024 * 1024;
    static final long MAX_ENTRY_WEIGHT = 2L * 1024 * 1024;

    private final LongSupplier ticker;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long weight;

    /** Cria o cache da API com relógio monotônico para controlar a validade. */
    public CompletedResultCache() {
        this(System::nanoTime);
    }

    CompletedResultCache(LongSupplier ticker) {
        this.ticker = ticker;
    }

    /**
     * Obtém uma resposta final ainda válida e pertencente ao usuário informado.
     *
     * @param userId identificador do usuário autenticado
     * @param analysisId identificador da análise solicitada
     * @return resposta em cache ou {@code null} quando ausente ou expirada
     */
    public synchronized AnalysisController.Result get(UUID userId, UUID analysisId) {
        var key = new Key(userId, analysisId);
        var entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (ticker.getAsLong() - entry.createdAt >= TTL.toNanos()) {
            remove(key);
            return null;
        }
        return entry.result;
    }

    /**
     * Armazena uma resposta concluída quando seu tamanho cabe no limite local.
     *
     * @param userId identificador do proprietário da análise
     * @param result resposta já convertida para o contrato HTTP
     */
    public synchronized void put(UUID userId, AnalysisController.Result result) {
        if (!"COMPLETED".equalsIgnoreCase(result.status())) {
            return;
        }
        long estimate = estimateWeight(result);
        if (estimate > MAX_ENTRY_WEIGHT) {
            return;
        }
        purgeExpired();
        var key = new Key(userId, result.analysisId());
        remove(key);
        while (weight + estimate > MAX_WEIGHT && !entries.isEmpty()) {
            remove(entries.keySet().iterator().next());
        }
        entries.put(key, new Entry(result, ticker.getAsLong(), estimate));
        weight += estimate;
    }

    private void purgeExpired() {
        long now = ticker.getAsLong();
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.createdAt >= TTL.toNanos()) {
                weight -= entry.weight;
                iterator.remove();
            }
        }
    }

    private void remove(Key key) {
        var removed = entries.remove(key);
        if (removed != null) {
            weight -= removed.weight;
        }
    }

    private static long estimateWeight(AnalysisController.Result result) {
        // Inclui margem para objetos e coleções sem serializar snippets ou respostas em logs.
        long total = 1024;
        for (var finding : result.findings()) {
            total += 512L + characters(finding.title(), finding.description(), finding.fileName(), finding.snippet());
            if (finding.aiAssessment() != null) {
                var assessment = finding.aiAssessment();
                total += characters(assessment.rationale(), assessment.remediation(), assessment.risk(),
                        assessment.falsePositiveReason(), assessment.limitations());
                for (var evidence : assessment.evidence()) {
                    total += characters(evidence);
                }
                for (var recommendation : assessment.recommendations()) {
                    total += characters(recommendation);
                }
            }
            if (finding.taintTrace() != null) {
                total += 128L + 64L * finding.taintTrace().steps().size();
            }
        }
        for (var suggestion : result.suggestions()) {
            total += 512L + characters(suggestion.title(), suggestion.evidence(), suggestion.rationale(),
                    suggestion.recommendation(), suggestion.limitations());
        }
        return total;
    }

    private static long characters(String... values) {
        long size = 0;
        for (var value : values) {
            if (value != null) {
                size += 2L * value.length();
            }
        }
        return size;
    }

    private record Key(UUID userId, UUID analysisId) {}
    private record Entry(AnalysisController.Result result, long createdAt, long weight) {}
}
