package com.fiap.sast.report;

import com.fiap.sast.persistence.AiAssessmentEntity;
import com.fiap.sast.persistence.AiSuggestionEntity;
import com.fiap.sast.persistence.Analysis;
import com.fiap.sast.persistence.AnalysisRepository;
import com.fiap.sast.persistence.Finding;
import com.fiap.sast.persistence.ReportSnapshot;
import com.fiap.sast.persistence.ReportSnapshotRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Cria e consulta snapshots sanitizados, sem substituir findings ou análises originais. */
@Service
public class ReportSnapshotService {
    public static final String SCHEMA_VERSION = "1.1";
    private final boolean english;

    private final AnalysisRepository analyses;
    private final ReportSnapshotRepository snapshots;
    private final ObjectMapper mapper;

    /**
     * Inicializa o serviço de snapshots e exportações versionadas.
     *
     * @param analyses repositório das análises proprietárias
     * @param snapshots repositório dos snapshots
     * @param mapper serializador JSON da aplicação
     */
    public ReportSnapshotService(AnalysisRepository analyses, ReportSnapshotRepository snapshots,
            ObjectMapper mapper, @org.springframework.beans.factory.annotation.Value("${sast.is-english:false}") boolean english) {
        this.analyses = analyses;
        this.snapshots = snapshots;
        this.mapper = mapper;
        this.english = english;
    }

    /**
     * Gera automaticamente a primeira versão de uma análise encerrada.
     *
     * @param analysisId análise concluída ou falha final
     * @return snapshot criado ou a versão existente
     */
    @Transactional
    public Optional<ReportSnapshot> createAutomatic(UUID analysisId) {
        var analysis = analyses.findById(analysisId).orElseThrow(NoSuchElementException::new);
        if (!isFinal(analysis)) {
            return Optional.empty();
        }
        return snapshots.findTopByAnalysisIdAndUserIdOrderByVersionDesc(analysis.id, analysis.userId)
                .map(Optional::of)
                .orElseGet(() -> Optional.of(create(analysis, 1)));
    }

    /**
     * Cria uma nova versão a partir da análise persistida, sem baixar ou reanalisar o repositório.
     *
     * @param analysisId análise de origem
     * @param userId proprietário autenticado
     * @return nova versão imutável
     */
    @Transactional
    public ReportSnapshot createManual(UUID analysisId, UUID userId) {
        var analysis = analyses.findByIdAndUserId(analysisId, userId).orElseThrow(NoSuchElementException::new);
        if (!isFinal(analysis)) {
            throw new IllegalArgumentException("O relatório só pode ser gerado após o encerramento da análise");
        }
        int next = snapshots.findTopByAnalysisIdAndUserIdOrderByVersionDesc(analysisId, userId)
                .map(snapshot -> snapshot.version + 1).orElse(1);
        return create(analysis, next);
    }

    /**
     * Lista versões de uma análise com isolamento por proprietário.
     *
     * @param analysisId análise de origem
     * @param userId proprietário autenticado
     * @return versões autorizadas
     */
    @Transactional(readOnly = true)
    public List<ReportSnapshot> list(UUID analysisId, UUID userId) {
        if (analyses.findByIdAndUserId(analysisId, userId).isEmpty()) {
            throw new NoSuchElementException();
        }
        return snapshots.findByAnalysisIdAndUserIdOrderByVersionDesc(analysisId, userId);
    }

    /**
     * Consulta uma versão de relatório com isolamento por proprietário.
     *
     * @param analysisId análise de origem
     * @param userId proprietário autenticado
     * @param version versão solicitada
     * @return snapshot autorizado
     */
    @Transactional(readOnly = true)
    public ReportSnapshot get(UUID analysisId, UUID userId, int version) {
        return snapshots.findByAnalysisIdAndUserIdAndVersion(analysisId, userId, version)
                .orElseThrow(NoSuchElementException::new);
    }

    /**
     * Monta o JSON SARIF 2.1.0 somente com findings determinísticos.
     *
     * @param snapshot snapshot imutável de origem
     * @return conteúdo SARIF UTF-8
     */
    public byte[] sarif(ReportSnapshot snapshot) {
        try {
            var document = document(snapshot);
            var sarif = SarifReportBuilder.build(document);
            return mapper.writeValueAsString(sarif).getBytes(StandardCharsets.UTF_8);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Não foi possível gerar o SARIF", exception);
        }
    }

    /**
     * Monta um PDF legível a partir do snapshot, sem nova análise.
     *
     * @param snapshot snapshot imutável de origem
     * @return PDF pronto para download
     */
    public byte[] pdf(ReportSnapshot snapshot) {
        return PdfReportRenderer.render(document(snapshot));
    }

    private ReportSnapshot create(Analysis analysis, int version) {
        var payload = payload(analysis);
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Não foi possível criar o snapshot do relatório", exception);
        }
        var snapshot = new ReportSnapshot();
        snapshot.analysisId = analysis.id;
        snapshot.userId = analysis.userId;
        snapshot.version = version;
        snapshot.generatedAt = now();
        snapshot.analysisStatus = analysis.status;
        snapshot.coverageStatus = coverageStatus(analysis);
        snapshot.schemaVersion = SCHEMA_VERSION;
        snapshot.locale = english ? "en-US" : "pt-BR";
        snapshot.snapshotHash = sha256(json);
        snapshot.payload = json;
        return snapshots.save(snapshot);
    }

    private Map<String, Object> payload(Analysis analysis) {
        var root = new LinkedHashMap<String, Object>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("locale", english ? "en-US" : "pt-BR");
        root.put("analysisId", analysis.id.toString());
        root.put("repositoryUrl", text(analysis.repositoryUrl, 500));
        root.put("repositoryOwner", text(analysis.repositoryOwner, 100));
        root.put("repositoryName", text(analysis.repositoryName, 100));
        root.put("reference", text(analysis.reference, 255));
        root.put("commitSha", text(analysis.commitSha, 40));
        root.put("language", "java");
        root.put("createdAt", analysis.createdAt);
        root.put("status", text(analysis.status, 32));
        root.put("stage", text(analysis.stage, 32));
        root.put("coverageStatus", coverageStatus(analysis));
        root.put("filesAnalyzed", analysis.filesAnalyzed);
        root.put("filesProcessed", analysis.filesProcessed);
        root.put("filesTotal", analysis.filesTotal);
        root.put("failureStage", text(analysis.failureStage, 64));
        root.put("failureMessage", text(analysis.failureMessage, 500));
        root.put("semanticStatus", text(analysis.semanticStatus, 32));
        root.put("suggestionStatus", text(analysis.suggestionStatus, 32));
        root.put("findings", analysis.findings.stream().map(this::finding).toList());
        root.put("suggestions", analysis.suggestions.stream().map(this::suggestion).toList());
        root.put("limitations", limitations(analysis));
        return root;
    }

    private Map<String, Object> finding(Finding finding) {
        var value = new LinkedHashMap<String, Object>();
        value.put("findingId", finding.id.toString());
        value.put("vulnerabilityCode", finding.vulnerability == null ? null : finding.vulnerability.publicCode);
        value.put("ruleId", text(finding.ruleId, 64));
        value.put("title", text(finding.title, 200));
        value.put("severity", text(finding.severity, 16));
        value.put("cwe", text(finding.cwe, 32));
        value.put("description", text(finding.description, 500));
        value.put("fileName", text(finding.fileName, 1000));
        value.put("line", finding.line);
        value.put("column", finding.column);
        value.put("snippet", text(finding.snippet, 500));
        value.put("contextSignature", text(finding.contextSignature, 500));
        value.put("expressionHash", text(finding.expressionHash, 64));
        value.put("identityOrdinal", finding.identityOrdinal);
        value.put("taintTrace", text(finding.taintTrace, 6000));
        value.put("aiAssessment", finding.aiAssessment == null ? null : assessment(finding.aiAssessment));
        return value;
    }

    private Map<String, Object> assessment(AiAssessmentEntity entity) {
        var value = new LinkedHashMap<String, Object>();
        value.put("model", text(entity.model, 100));
        value.put("promptVersion", text(entity.promptVersion, 32));
        value.put("confidence", entity.confidence);
        value.put("suggestedSeverity", text(entity.suggestedSeverity, 16));
        value.put("likelyFalsePositive", entity.likelyFalsePositive);
        value.put("rationale", text(entity.rationale, 500));
        value.put("remediation", text(entity.remediation, 1000));
        value.put("risk", text(entity.risk, 1200));
        value.put("evidence", text(entity.evidence, 1600));
        value.put("limitations", text(entity.limitations, 800));
        return value;
    }

    private Map<String, Object> suggestion(AiSuggestionEntity suggestion) {
        var value = new LinkedHashMap<String, Object>();
        value.put("category", text(suggestion.category, 32));
        value.put("severity", text(suggestion.severity, 16));
        value.put("title", text(suggestion.title, 160));
        value.put("fileName", text(suggestion.fileName, 1000));
        value.put("line", suggestion.lineNumber);
        value.put("evidence", text(suggestion.evidence, 300));
        value.put("rationale", text(suggestion.rationale, 600));
        value.put("confidence", suggestion.confidence);
        value.put("recommendation", text(suggestion.recommendation, 800));
        value.put("limitations", text(suggestion.limitations, 500));
        return value;
    }

    private List<String> limitations(Analysis analysis) {
        var values = new ArrayList<String>();
        values.add("A análise cobre somente arquivos Java elegíveis e regras determinísticas registradas.");
        values.add("A ausência de findings não comprova segurança total nem explorabilidade inexistente.");
        if (!"CONFIRMED".equals(coverageStatus(analysis))) {
            values.add("A cobertura determinística está parcial ou falhou; os resultados não representam "
                    + "o repositório inteiro.");
        }
        if (!"COMPLETED".equalsIgnoreCase(analysis.semanticStatus)) {
            values.add("A avaliação consultiva de IA não foi concluída integralmente.");
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> document(ReportSnapshot snapshot) {
        try {
            return mapper.readValue(snapshot.payload, Map.class);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Snapshot de relatório inválido", exception);
        }
    }

    private static boolean isFinal(Analysis analysis) {
        return "COMPLETED".equalsIgnoreCase(analysis.status) || "FAILED".equalsIgnoreCase(analysis.status);
    }

    private static String coverageStatus(Analysis analysis) {
        if ("FAILED".equalsIgnoreCase(analysis.status)) {
            return "FAILED";
        }
        if (!"COMPLETED".equalsIgnoreCase(analysis.status)
                || analysis.filesTotal <= 0 || analysis.filesProcessed != analysis.filesTotal) {
            return "PARTIAL";
        }
        return "CONFIRMED";
    }

    private static String text(String value, int limit) {
        if (value == null) {
            return null;
        }
        var clean = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return clean.length() <= limit ? clean : clean.substring(0, limit);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
