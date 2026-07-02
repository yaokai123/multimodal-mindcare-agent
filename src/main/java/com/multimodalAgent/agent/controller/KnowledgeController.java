package com.multimodalAgent.agent.controller;

import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.dto.KnowledgeCitationFeedbackRequest;
import com.multimodalAgent.agent.dto.KnowledgeCitationFeedbackResponse;
import com.multimodalAgent.agent.dto.KnowledgeChunkResponse;
import com.multimodalAgent.agent.dto.KnowledgeIngestRequest;
import com.multimodalAgent.agent.dto.KnowledgeIngestResponse;
import com.multimodalAgent.agent.dto.KnowledgeSearchRequest;
import com.multimodalAgent.agent.dto.KnowledgeSourceResponse;
import com.multimodalAgent.agent.dto.KnowledgeUpdateRequest;
import com.multimodalAgent.agent.security.CurrentUser;
import com.multimodalAgent.agent.service.AuditService;
import com.multimodalAgent.agent.service.knowledge.KnowledgeFileService;
import com.multimodalAgent.agent.service.knowledge.KnowledgeService;
import com.multimodalAgent.agent.service.knowledge.SearchResult;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeFileService knowledgeFileService;
    private final multimodalAgentProperties properties;
    private final AuditService auditService;

    public KnowledgeController(
            KnowledgeService knowledgeService,
            KnowledgeFileService knowledgeFileService,
            multimodalAgentProperties properties,
            AuditService auditService
    ) {
        this.knowledgeService = knowledgeService;
        this.knowledgeFileService = knowledgeFileService;
        this.properties = properties;
        this.auditService = auditService;
    }

    @GetMapping
    public List<KnowledgeSourceResponse> sources() {
        return knowledgeService.sources();
    }

    @GetMapping("/{source}/chunks")
    public List<KnowledgeChunkResponse> chunks(@PathVariable String source) {
        return knowledgeService.sourceChunks(source).stream()
                .map(KnowledgeChunkResponse::from)
                .toList();
    }

    @PatchMapping("/{source}")
    public List<KnowledgeChunkResponse> updateSource(
            @PathVariable String source,
            @Valid @RequestBody KnowledgeUpdateRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        auditService.log(actor(currentUser), "KNOWLEDGE_UPDATE", "KNOWLEDGE_SOURCE", source,
                "category=" + request.category() + ", active=" + request.active()
                        + ", audience=" + request.audience() + ", risk=" + request.riskLevel()
                        + ", versionStatus=" + request.versionStatus());
        return knowledgeService.updateSource(
                        source,
                        request.category(),
                        request.tags(),
                        request.audience(),
                        request.riskLevel(),
                        request.active(),
                        request.versionStatus(),
                        request.versionNote())
                .stream()
                .map(KnowledgeChunkResponse::from)
                .toList();
    }

    @PostMapping("/search-test")
    public List<SearchResult> searchTest(
            @Valid @RequestBody KnowledgeSearchRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        auditService.log(actor(currentUser), "KNOWLEDGE_SEARCH_TEST", "KNOWLEDGE", "query", request.query());
        return knowledgeService.searchTest(request.query(), request.scope(), request.minScore());
    }

    @PostMapping("/citation-feedback")
    public KnowledgeCitationFeedbackResponse citationFeedback(
            @Valid @RequestBody KnowledgeCitationFeedbackRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        KnowledgeCitationFeedbackResponse response = knowledgeService.recordFeedback(request, actor(currentUser));
        auditService.log(actor(currentUser), "KNOWLEDGE_CITATION_FEEDBACK", "KNOWLEDGE_SOURCE", request.source(),
                "chunkId=" + request.chunkId() + ", reason=" + request.reason());
        return response;
    }

    @PostMapping
    public KnowledgeIngestResponse ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
        String category = request.category() == null || request.category().isBlank()
                ? "GENERAL"
                : request.category();
        int chunks = knowledgeService.ingest(request.source(), request.content(), category);
        auditService.log("admin", "KNOWLEDGE_INGEST", "KNOWLEDGE_SOURCE", request.source(), category + " / " + chunks + " chunks");
        return new KnowledgeIngestResponse(request.source(), category, chunks);
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<KnowledgeIngestResponse> ingestFile(@RequestPart("file") FilePart file) {
        validateKnowledgeFile(file.filename());
        return DataBufferUtils.join(file.content(), checkedMaxBytes(properties.getUpload().getKnowledgeMaxBytes()))
                .map(dataBuffer -> {
                    byte[] bytes = readBytes(dataBuffer);
                    KnowledgeIngestResponse response = knowledgeFileService.ingest(file.filename(), bytes);
                    auditService.log("admin", "KNOWLEDGE_FILE_INGEST", "KNOWLEDGE_SOURCE", response.source(), response.category());
                    return response;
                });
    }

    private void validateKnowledgeFile(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!(normalized.endsWith(".pdf")
                || normalized.endsWith(".md")
                || normalized.endsWith(".markdown")
                || normalized.endsWith(".txt"))) {
            throw new IllegalArgumentException("Unsupported knowledge file type. Allowed: pdf, md, markdown, txt.");
        }
    }

    private int checkedMaxBytes(long value) {
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid upload byte limit: " + value);
        }
        return (int) value;
    }

    private byte[] readBytes(DataBuffer dataBuffer) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(dataBuffer.readableByteCount());
            dataBuffer.asInputStream().transferTo(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + exception.getMessage());
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private String actor(CurrentUser currentUser) {
        return currentUser == null || currentUser.getDisplayName() == null || currentUser.getDisplayName().isBlank()
                ? "admin"
                : currentUser.getDisplayName();
    }
}
