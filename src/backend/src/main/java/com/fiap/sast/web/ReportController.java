package com.fiap.sast.web;

import com.fiap.sast.auth.AppUser;
import com.fiap.sast.auth.UserRepository;
import com.fiap.sast.persistence.ReportSnapshot;
import com.fiap.sast.report.ReportSnapshotService;
import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas autenticadas para snapshots e exportações de uma análise. */
@RestController
@RequestMapping("/api/analyses/{analysisId}/reports")
public class ReportController {
    private final ReportSnapshotService reports;
    private final UserRepository users;

    /**
     * Cria o controlador de relatórios.
     *
     * @param reports serviço de snapshots e exportações
     * @param users repositório das contas autenticadas
     */
    public ReportController(ReportSnapshotService reports, UserRepository users) {
        this.reports = reports;
        this.users = users;
    }

    public record ReportResponse(int version, String generatedAt, String status, String coverageStatus,
            String schemaVersion, String snapshotHash) {}

    /**
     * Lista as versões de relatório autorizadas para a análise.
     *
     * @param analysisId análise de origem
     * @param principal conta autenticada
     * @return versões mais recentes primeiro
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ReportResponse> list(@PathVariable UUID analysisId, Principal principal) {
        return reports.list(analysisId, owner(principal).id).stream().map(this::view).toList();
    }

    /**
     * Cria uma nova versão a partir dos dados persistidos, sem reexecutar a análise.
     *
     * @param analysisId análise de origem
     * @param principal conta autenticada
     * @return versão criada
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ReportResponse> create(@PathVariable UUID analysisId, Principal principal) {
        var snapshot = reports.createManual(analysisId, owner(principal).id);
        return ResponseEntity.status(201).body(view(snapshot));
    }

    /**
     * Obtém os metadados de uma versão sem transferir o arquivo.
     *
     * @param analysisId análise de origem
     * @param version versão solicitada
     * @param principal conta autenticada
     * @return metadados do snapshot
     */
    @GetMapping("/{version}")
    @Transactional(readOnly = true)
    public ReportResponse get(@PathVariable UUID analysisId, @PathVariable int version, Principal principal) {
        return view(reports.get(analysisId, owner(principal).id, version));
    }

    /**
     * Baixa a versão SARIF preservando o snapshot original.
     *
     * @param analysisId análise de origem
     * @param version versão solicitada
     * @param principal conta autenticada
     * @return documento SARIF sem cache
     */
    @GetMapping(value = "/{version}/sarif", produces = "application/sarif+json")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> sarif(@PathVariable UUID analysisId, @PathVariable int version,
            Principal principal) {
        var snapshot = reports.get(analysisId, owner(principal).id, version);
        return download(reports.sarif(snapshot), "application/sarif+json", snapshot, "sarif");
    }

    /**
     * Baixa a versão PDF preservando o snapshot original.
     *
     * @param analysisId análise de origem
     * @param version versão solicitada
     * @param principal conta autenticada
     * @return documento PDF sem cache
     */
    @GetMapping(value = "/{version}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID analysisId, @PathVariable int version,
            Principal principal) {
        var snapshot = reports.get(analysisId, owner(principal).id, version);
        return download(reports.pdf(snapshot), MediaType.APPLICATION_PDF_VALUE, snapshot, "pdf");
    }

    private ResponseEntity<byte[]> download(byte[] content, String mediaType, ReportSnapshot snapshot,
            String extension) {
        var filename = "analysis-" + snapshot.analysisId + "-report-v" + snapshot.version + "." + extension;
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    private ReportResponse view(ReportSnapshot snapshot) {
        return new ReportResponse(snapshot.version, snapshot.generatedAt.toString(), snapshot.analysisStatus,
                snapshot.coverageStatus, snapshot.schemaVersion, snapshot.snapshotHash);
    }

    private AppUser owner(Principal principal) {
        return users.findByEmail(principal.getName()).orElseThrow(NoSuchElementException::new);
    }
}
