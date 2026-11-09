package com.fiap.sast.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Renderizador PDF paginado, com hierarquia visual e texto sanitizado do snapshot. */
final class PdfReportRenderer {
    private PdfReportRenderer() {
    }

    static byte[] render(Map<String, Object> document) {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            boolean english = "en-US".equals(text(document.get("locale")));
            var writer = new Writer(pdf);
            writer.heading(english ? "FISAST - Consolidated analysis report" : "FISAST - Relatório consolidado de análise", 22);
            writer.paragraph(english ? "Immutable snapshot for traceability. The analysis never executes received code." : "Snapshot imutável para rastreabilidade. A análise não executa o código recebido.");
            writer.paragraph((english ? "Repository: " : "Repositório: ") + text(document.get("repositoryUrl")));
            writer.paragraph((english ? "Reference: " : "Referência: ") + valueOrUnavailable(document.get("reference")));
            writer.paragraph((english ? "Status: " : "Status: ") + text(document.get("status")) + (english ? " | Coverage: " : " | Cobertura: ")
                    + text(document.get("coverageStatus")));
            writer.heading(english ? "Executive summary" : "Sumário executivo", 16);
            writer.paragraph((english ? "Deterministic findings: " : "Findings determinísticos: ") + list(document.get("findings")).size());
            writer.paragraph((english ? "Consultive AI suggestions: " : "Sugestões consultivas de IA: ") + list(document.get("suggestions")).size());
            writer.paragraph((english ? "Processed Java files: " : "Arquivos Java processados: ") + text(document.get("filesProcessed")) + "/"
                    + text(document.get("filesTotal")));
            writer.heading(english ? "Methodology and limitations" : "Metodologia e limitações", 16);
            for (var limitation : strings(document.get("limitations"))) {
                writer.bullet(limitation);
            }
            writer.heading(english ? "Deterministic findings" : "Findings determinísticos", 16);
            var findings = list(document.get("findings"));
            if (findings.isEmpty()) {
                writer.paragraph(english ? "No deterministic finding was persisted in this run." : "Nenhum finding determinístico foi persistido nesta execução.");
            }
            for (var finding : findings) {
                writer.heading(text(finding.get("title")) + " - " + text(finding.get("severity")), 12);
                writer.paragraph("Regra " + text(finding.get("ruleId")) + " | "
                        + text(finding.get("cwe")) + " | " + text(finding.get("fileName")) + ":"
                        + text(finding.get("line")));
                writer.paragraph(text(finding.get("description")));
                if (!text(finding.get("vulnerabilityCode")).isBlank()) {
                    writer.paragraph("Vulnerabilidade persistente: " + text(finding.get("vulnerabilityCode")));
                }
                writer.paragraph("Evidência sanitizada: " + valueOrUnavailable(finding.get("snippet")));
            }
            writer.heading(english ? "Consultive suggestions" : "Sugestões consultivas", 16);
            var suggestions = list(document.get("suggestions"));
            if (suggestions.isEmpty()) {
                writer.paragraph(english ? "No consultive suggestion was persisted in this run." : "Nenhuma sugestão consultiva foi persistida nesta execução.");
            }
            for (var suggestion : suggestions) {
                writer.heading(text(suggestion.get("title")), 12);
                writer.paragraph(text(suggestion.get("category")) + " | "
                        + text(suggestion.get("fileName")) + ":" + text(suggestion.get("line")));
                writer.paragraph(text(suggestion.get("recommendation")));
            }
            writer.finishFooters();
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível gerar o PDF", exception);
        }
    }

    private static String valueOrUnavailable(Object value) {
        var text = text(value);
        return text.isBlank() ? "Indisponível" : text;
    }

    private static String text(Object value) {
        return Objects.toString(value, "").replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(PdfReportRenderer::text).toList();
    }

    private static final class Writer {
        private static final float LEFT = 54;
        private static final float BOTTOM = 58;
        private static final float TOP = 770;
        private static final float CONTENT_WIDTH = 480;
        private static final Color INK = new Color(0x1B1B1B);
        private static final Color BODY = new Color(0x3F4541);
        private static final Color ACCENT = new Color(0xED145B);
        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPage page;
        private float y;

        private Writer(PDDocument document) {
            this.document = document;
            newPage();
        }

        private void heading(String value, float size) throws IOException {
            if (size >= 20 && document.getNumberOfPages() > 1) {
                newPage();
            }
            y -= size >= 20 ? 8 : 12;
            line(value, size, true, size >= 20 ? 28 : 20, size >= 16 ? ACCENT : INK);
            y -= size >= 20 ? 8 : 5;
        }

        private void paragraph(String value) throws IOException {
            for (var line : wrap(value, regular, 10, CONTENT_WIDTH)) {
                line(line, 10, false, 14, BODY);
            }
            y -= 7;
        }

        private void bullet(String value) throws IOException {
            for (var line : wrap("- " + value, regular, 10, CONTENT_WIDTH - 10)) {
                line(line, 10, false, 14, BODY);
            }
            y -= 3;
        }

        private void line(String value, float size, boolean isBold, float leading, Color color) throws IOException {
            ensureSpace(leading);
            try (var stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND,
                    true, true)) {
                stream.beginText();
                stream.setFont(isBold ? bold : regular, size);
                stream.setNonStrokingColor(color);
                stream.newLineAtOffset(LEFT, y);
                stream.showText(value.isBlank() ? " " : value);
                stream.endText();
            }
            y -= leading;
        }

        private void ensureSpace(float needed) {
            if (y - needed < BOTTOM + 18) {
                newPage();
            }
        }

        private void newPage() {
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            y = TOP;
        }

        private void finishFooters() throws IOException {
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                var current = document.getPage(index);
                try (var stream = new PDPageContentStream(document, current, PDPageContentStream.AppendMode.APPEND,
                        true, true)) {
                    stream.beginText();
                    stream.setFont(regular, 8);
                    stream.newLineAtOffset(LEFT, 30);
                    stream.showText("FISAST - Relatório sanitizado | Página " + (index + 1) + "/"
                            + document.getNumberOfPages());
                    stream.endText();
                }
            }
        }

        private static List<String> wrap(String value, PDType1Font font, float size, float width) throws IOException {
            var result = new java.util.ArrayList<String>();
            for (var paragraph : Objects.toString(value, "").split("\\R", -1)) {
                if (paragraph.isBlank()) {
                    result.add("");
                    continue;
                }
                var current = new StringBuilder();
                for (var word : paragraph.split("\\s+")) {
                    if (word.isBlank()) {
                        continue;
                    }
                    if (font.getStringWidth(word) / 1000 * size > width) {
                        if (!current.isEmpty()) {
                            result.add(current.toString());
                            current.setLength(0);
                        }
                        splitLongWord(word, font, size, width, result);
                        continue;
                    }
                    var candidate = current.isEmpty() ? word : current + " " + word;
                    if (font.getStringWidth(candidate) / 1000 * size <= width) {
                        current.setLength(0);
                        current.append(candidate);
                    } else {
                        if (!current.isEmpty()) {
                            result.add(current.toString());
                        }
                        current.setLength(0);
                        current.append(word);
                    }
                }
                if (!current.isEmpty()) {
                    result.add(current.toString());
                }
            }
            return result.isEmpty() ? List.of("") : result;
        }

        private static void splitLongWord(String word, PDType1Font font, float size, float width,
                List<String> result) throws IOException {
            var current = new StringBuilder();
            for (var character : word.toCharArray()) {
                var candidate = current.toString() + character;
                if (!current.isEmpty() && font.getStringWidth(candidate) / 1000 * size > width) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                current.append(character);
            }
            if (!current.isEmpty()) {
                result.add(current.toString());
            }
        }
    }
}
