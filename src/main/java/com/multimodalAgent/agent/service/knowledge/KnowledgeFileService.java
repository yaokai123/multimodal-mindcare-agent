package com.multimodalAgent.agent.service.knowledge;

import com.multimodalAgent.agent.dto.KnowledgeIngestResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeFileService {

    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final KnowledgeService knowledgeService;

    public KnowledgeFileService(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public KnowledgeIngestResponse ingest(String filename, byte[] bytes) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Uploaded file must not exceed 10MB.");
        }
        String source = sanitizeSource(filename);
        String text = extractText(source, bytes);
        if (text.isBlank()) {
            throw new IllegalArgumentException("No usable text was parsed from the uploaded file.");
        }
        String category = categorize(source, text);
        int chunks = knowledgeService.ingest(source, text, category);
        return new KnowledgeIngestResponse(source, category, chunks);
    }

    private String extractText(String filename, byte[] bytes) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Only PDF, Markdown, and txt files are supported.");
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse PDF text: " + exception.getMessage());
        }
    }

    private String sanitizeSource(String filename) {
        String source = filename == null || filename.isBlank() ? "uploaded-knowledge" : filename.trim();
        source = source.replaceAll("[\\\\/]+", "-");
        return source.length() > 180 ? source.substring(source.length() - 180) : source;
    }

    private String categorize(String source, String content) {
        String text = ((source == null ? "" : source) + " " + (content == null ? "" : content)).toLowerCase(Locale.ROOT);
        if (containsAny(text, "risk", "crisis", "suicide", "self harm", "自杀", "自伤", "危机", "风险")) {
            return "CRISIS_POLICY";
        }
        if (containsAny(text, "sleep", "insomnia", "睡眠", "失眠")) {
            return "SLEEP_SUPPORT";
        }
        if (containsAny(text, "exam", "study", "考试", "复习", "学习", "压力")) {
            return "STUDY_STRESS";
        }
        if (containsAny(text, "campus", "counselor", "心理中心", "辅导员", "校内")) {
            return "CAMPUS_SUPPORT";
        }
        return "GENERAL";
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
