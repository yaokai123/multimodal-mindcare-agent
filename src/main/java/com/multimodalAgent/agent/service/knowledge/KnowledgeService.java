package com.multimodalAgent.agent.service.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.domain.KnowledgeCitationFeedback;
import com.multimodalAgent.agent.domain.KnowledgeChunk;
import com.multimodalAgent.agent.dto.KnowledgeCitationFeedbackRequest;
import com.multimodalAgent.agent.dto.KnowledgeCitationFeedbackResponse;
import com.multimodalAgent.agent.dto.KnowledgeSourceResponse;
import com.multimodalAgent.agent.repository.KnowledgeChunkRepository;
import com.multimodalAgent.agent.repository.KnowledgeCitationFeedbackRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {

    private static final double MIN_REFERENCE_SCORE = 0.18;

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final KnowledgeCitationFeedbackRepository feedbackRepository;
    private final multimodalAgentProperties properties;
    private final ChromaGateway chromaGateway;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final KnowledgeChunker chunker = new KnowledgeChunker();
    private final TokenVectorizer vectorizer = new TokenVectorizer();

    public KnowledgeService(
            KnowledgeChunkRepository knowledgeChunkRepository,
            KnowledgeCitationFeedbackRepository feedbackRepository,
            multimodalAgentProperties properties,
            ChromaGateway chromaGateway,
            EmbeddingClient embeddingClient,
            ObjectMapper objectMapper
    ) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.feedbackRepository = feedbackRepository;
        this.properties = properties;
        this.chromaGateway = chromaGateway;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int ingest(String source, String content) {
        return ingest(source, content, categorize(source, content));
    }

    @Transactional
    public int ingest(String source, String content, String category) {
        List<String> chunks = chunker.chunk(
                content,
                properties.getKnowledge().getChunkSize(),
                properties.getKnowledge().getChunkOverlap());
        String normalizedCategory = normalizeCategory(category);
        List<KnowledgeChunk> previous = knowledgeChunkRepository.findBySource(source);
        int nextVersion = previous.stream()
                .mapToInt(KnowledgeChunk::getVersion)
                .max()
                .orElse(0) + 1;
        for (KnowledgeChunk old : previous) {
            old.setActive(false);
            old.setVersionStatus("SUPERSEDED");
            old.setVersionNote("Superseded by version " + nextVersion);
            knowledgeChunkRepository.save(old);
        }
        chromaGateway.deleteSource(source);
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setSource(source);
            chunk.setCategory(normalizedCategory);
            chunk.setTags(defaultTags(normalizedCategory, content));
            chunk.setAudience(defaultAudience(normalizedCategory));
            chunk.setRiskLevel(defaultRiskLevel(normalizedCategory));
            chunk.setVersion(nextVersion);
            chunk.setVersionStatus("ENABLED");
            chunk.setVersionNote("Imported version " + nextVersion);
            chunk.setSourceIndex(index);
            chunk.setContent(chunks.get(index));
            chunk.setEmbeddingJson(serializeEmbedding(safeEmbedding(chunks.get(index))));
            KnowledgeChunk saved = knowledgeChunkRepository.save(chunk);
            chromaGateway.mirror(saved);
        }
        return chunks.size();
    }

    @Transactional(readOnly = true)
    public List<SearchResult> retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    @Transactional(readOnly = true)
    public List<SearchResult> retrieve(String query, int topK, String scope) {
        return trusted(retrieveRaw(query, topK, scope));
    }

    private List<SearchResult> retrieveRaw(String query, int topK, String scope) {
        String normalizedScope = normalizeScope(scope);
        List<SearchResult> chromaResults = hasScope(normalizedScope) ? List.of() : chromaGateway.query(query, topK);
        if (!chromaResults.isEmpty()) {
            return expandBestContext(chromaResults, topK);
        }
        List<SearchResult> embeddingResults = retrieveByEmbedding(query, topK, normalizedScope);
        if (!embeddingResults.isEmpty()) {
            return expandBestContext(embeddingResults, topK);
        }
        List<SearchResult> ranked = knowledgeChunkRepository.findAll().stream()
                .filter(KnowledgeChunk::isActive)
                .filter(chunk -> matchesScope(chunk, normalizedScope))
                .map(chunk -> new SearchResult(
                        chunk.getId(),
                        chunk.getSource(),
                        categoryOf(chunk),
                        textOf(chunk.getTags()),
                        textOf(chunk.getAudience(), "ALL_STUDENTS"),
                        textOf(chunk.getRiskLevel(), "LOW"),
                        chunk.getContent(),
                        hybridScore(query, chunk.getContent()),
                        true,
                        "KNOWLEDGE_BASE"))
                .filter(result -> result.score() > 0.0)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .toList();
        return expandBestContext(ranked, topK);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSourceResponse> sources() {
        Map<String, List<KnowledgeChunk>> bySource = knowledgeChunkRepository.findAll().stream()
                .collect(Collectors.groupingBy(KnowledgeChunk::getSource));
        return bySource.entrySet().stream()
                .map(entry -> {
                    List<KnowledgeChunk> chunks = entry.getValue();
                    KnowledgeChunk latest = chunks.stream()
                            .max(Comparator.comparing(KnowledgeChunk::getCreatedAt))
                            .orElseThrow();
                    return new KnowledgeSourceResponse(
                            entry.getKey(),
                            categoryOf(latest),
                            textOf(latest.getTags()),
                            textOf(latest.getAudience(), "ALL_STUDENTS"),
                            textOf(latest.getRiskLevel(), "LOW"),
                            chunks.size(),
                            latest.getCreatedAt(),
                            chunks.stream().anyMatch(KnowledgeChunk::isActive),
                            latest.getVersion(),
                            textOf(latest.getVersionStatus(), "ENABLED"),
                            chunks.stream()
                                    .map(KnowledgeChunk::getId)
                                    .filter(id -> id != null)
                                    .mapToLong(feedbackRepository::countByChunkId)
                                    .sum());
                })
                .sorted(Comparator.comparing(KnowledgeSourceResponse::latestCreatedAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeChunk> sourceChunks(String source) {
        return knowledgeChunkRepository.findTop20BySourceOrderByCreatedAtDesc(source);
    }

    @Transactional
    public List<KnowledgeChunk> updateSource(
            String source,
            String category,
            String tags,
            String audience,
            String riskLevel,
            Boolean active,
            String versionStatus,
            String versionNote
    ) {
        List<KnowledgeChunk> chunks = knowledgeChunkRepository.findBySource(source);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Knowledge source not found: " + source);
        }
        if (active != null && !active) {
            chromaGateway.deleteSource(source);
        }
        for (KnowledgeChunk chunk : chunks) {
            if (category != null && !category.isBlank()) {
                chunk.setCategory(normalizeCategory(category));
            }
            if (tags != null) {
                chunk.setTags(normalizeText(tags, 240));
            }
            if (audience != null && !audience.isBlank()) {
                chunk.setAudience(normalizeText(audience, 120));
            }
            if (riskLevel != null && !riskLevel.isBlank()) {
                chunk.setRiskLevel(normalizeRiskLevel(riskLevel));
            }
            if (active != null) {
                chunk.setActive(active);
                chunk.setVersionStatus(active ? "ENABLED" : "DISABLED");
            }
            if (versionStatus != null && !versionStatus.isBlank()) {
                chunk.setVersionStatus(normalizeVersionStatus(versionStatus));
                chunk.setActive("ENABLED".equalsIgnoreCase(chunk.getVersionStatus()));
            }
            if (versionNote != null) {
                chunk.setVersionNote(normalizeText(versionNote, 500));
            }
            chunk.setVersion(chunk.getVersion() + 1);
            KnowledgeChunk saved = knowledgeChunkRepository.save(chunk);
            if (saved.isActive()) {
                chromaGateway.mirror(saved);
            }
        }
        return chunks;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> searchTest(String query) {
        return retrieve(query, Math.max(3, properties.getKnowledge().getTopK()));
    }

    @Transactional(readOnly = true)
    public List<SearchResult> searchTest(String query, String scope, Double minScore) {
        double threshold = minScore == null ? MIN_REFERENCE_SCORE : Math.max(0.0, minScore);
        return retrieveRaw(query, Math.max(6, properties.getKnowledge().getTopK()), scope).stream()
                .map(result -> withVisibility(result, threshold))
                .toList();
    }

    @Transactional
    public KnowledgeCitationFeedbackResponse recordFeedback(KnowledgeCitationFeedbackRequest request, String actor) {
        KnowledgeCitationFeedback feedback = new KnowledgeCitationFeedback();
        feedback.setChunkId(request.chunkId());
        feedback.setSource(normalizeText(request.source(), 180));
        feedback.setCategory(normalizeCategory(request.category()));
        feedback.setActor(normalizeText(actor == null || actor.isBlank() ? "admin" : actor, 120));
        feedback.setReason(normalizeText(request.reason() == null || request.reason().isBlank() ? "引用不合适" : request.reason(), 80));
        feedback.setNote(normalizeText(request.note(), 1000));
        return KnowledgeCitationFeedbackResponse.from(feedbackRepository.save(feedback));
    }

    private List<SearchResult> trusted(List<SearchResult> results) {
        return results.stream()
                .filter(result -> result.score() >= MIN_REFERENCE_SCORE)
                .map(result -> withVisibility(result, MIN_REFERENCE_SCORE))
                .toList();
    }

    private List<SearchResult> retrieveByEmbedding(String query, int topK) {
        return retrieveByEmbedding(query, topK, null);
    }

    private List<SearchResult> retrieveByEmbedding(String query, int topK, String scope) {
        List<Double> queryEmbedding = safeEmbedding(query);
        if (queryEmbedding.isEmpty()) {
            return List.of();
        }
        return knowledgeChunkRepository.findAll().stream()
                .filter(KnowledgeChunk::isActive)
                .filter(chunk -> matchesScope(chunk, scope))
                .map(chunk -> new SearchResult(
                        chunk.getId(),
                        chunk.getSource(),
                        categoryOf(chunk),
                        textOf(chunk.getTags()),
                        textOf(chunk.getAudience(), "ALL_STUDENTS"),
                        textOf(chunk.getRiskLevel(), "LOW"),
                        chunk.getContent(),
                        cosine(queryEmbedding, parseEmbedding(chunk.getEmbeddingJson())),
                        true,
                        "KNOWLEDGE_BASE"))
                .filter(result -> result.score() > 0.0)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    private List<SearchResult> expandBestContext(List<SearchResult> ranked, int topK) {
        if (ranked.isEmpty()) {
            return ranked;
        }
        SearchResult best = ranked.get(0);
        SearchResult expanded = expand(best);
        List<SearchResult> results = new ArrayList<>();
        results.add(expanded);
        ranked.stream()
                .skip(1)
                .filter(result -> !sameChunk(result, expanded))
                .limit(Math.max(0, topK - 1))
                .forEach(results::add);
        return results;
    }

    private SearchResult expand(SearchResult result) {
        if (result.chunkId() == null) {
            return result;
        }
        return knowledgeChunkRepository.findById(result.chunkId())
                .filter(KnowledgeChunk::isActive)
                .map(chunk -> {
                    List<KnowledgeChunk> neighbors = knowledgeChunkRepository
                            .findBySourceAndSourceIndexBetweenOrderBySourceIndexAsc(
                                    chunk.getSource(),
                                    Math.max(0, chunk.getSourceIndex() - 1),
                                    chunk.getSourceIndex() + 1);
                    String expandedContent = String.join("\n\n", neighbors.stream()
                            .map(KnowledgeChunk::getContent)
                            .toList());
                    return new SearchResult(
                            chunk.getId(),
                            chunk.getSource(),
                            categoryOf(chunk),
                            textOf(chunk.getTags()),
                            textOf(chunk.getAudience(), "ALL_STUDENTS"),
                            textOf(chunk.getRiskLevel(), "LOW"),
                            expandedContent,
                            result.score(),
                            result.shown(),
                            result.basis());
                })
                .orElse(result);
    }

    private boolean sameChunk(SearchResult result, SearchResult expanded) {
        return result.chunkId() != null && result.chunkId().equals(expanded.chunkId());
    }

    private String categoryOf(KnowledgeChunk chunk) {
        return chunk.getCategory() == null || chunk.getCategory().isBlank() ? "GENERAL" : chunk.getCategory();
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

    private String defaultTags(String category, String content) {
        String normalized = category == null ? "GENERAL" : category;
        if ("CRISIS_POLICY".equals(normalized)) {
            return "crisis,safety,intervention";
        }
        if ("SLEEP_SUPPORT".equals(normalized)) {
            return "sleep,relaxation,self-care";
        }
        if ("STUDY_STRESS".equals(normalized)) {
            return "study,stress,coping";
        }
        if ("CAMPUS_SUPPORT".equals(normalized)) {
            return "campus,counselor,referral";
        }
        return containsAny(content == null ? "" : content.toLowerCase(Locale.ROOT), "危机", "自伤", "suicide")
                ? "safety"
                : "general";
    }

    private String defaultAudience(String category) {
        return "CRISIS_POLICY".equals(category) ? "COUNSELOR_AND_STUDENT" : "ALL_STUDENTS";
    }

    private String defaultRiskLevel(String category) {
        if ("CRISIS_POLICY".equals(category)) {
            return "HIGH";
        }
        if ("SLEEP_SUPPORT".equals(category) || "STUDY_STRESS".equals(category)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "GENERAL";
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        if ("HIGH".equals(normalized) || "MEDIUM".equals(normalized) || "LOW".equals(normalized)) {
            return normalized;
        }
        return "LOW";
    }

    private String normalizeVersionStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        if ("ENABLED".equals(normalized) || "DISABLED".equals(normalized) || "SUPERSEDED".equals(normalized) || "ROLLED_BACK".equals(normalized)) {
            return normalized;
        }
        return "ENABLED";
    }

    private String normalizeText(String value, int limit) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > limit ? normalized.substring(0, limit) : normalized;
    }

    private String textOf(String value) {
        return textOf(value, "");
    }

    private String textOf(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private SearchResult withVisibility(SearchResult result, double minScore) {
        boolean shown = result.score() >= minScore;
        String basis = shown ? "KNOWLEDGE_BASE" : "GENERAL_SUPPORT_LOW_RELEVANCE";
        return new SearchResult(
                result.chunkId(),
                result.source(),
                result.category(),
                result.tags(),
                result.audience(),
                result.riskLevel(),
                result.content(),
                result.score(),
                shown,
                basis);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        String normalized = scope.trim();
        if ("ALL".equalsIgnoreCase(normalized)
                || "support".equalsIgnoreCase(normalized)
                || "student-support".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private boolean hasScope(String scope) {
        return scope != null && !scope.isBlank();
    }

    private boolean matchesScope(KnowledgeChunk chunk, String scope) {
        if (!hasScope(scope)) {
            return true;
        }
        String source = chunk.getSource() == null ? "" : chunk.getSource().toLowerCase(Locale.ROOT);
        String category = categoryOf(chunk).toLowerCase(Locale.ROOT);
        String normalizedCategory = category.replace('_', '-');
        return source.equals(scope)
                || source.contains(scope)
                || category.equals(scope)
                || normalizedCategory.equals(scope)
                || normalizedCategory.contains(scope);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private double hybridScore(String query, String content) {
        double semantic = vectorizer.cosine(query, content);
        double keyword = keywordScore(query, content);
        return semantic * 0.75 + keyword * 0.25;
    }

    private List<Double> safeEmbedding(String text) {
        try {
            return embeddingClient.embed(text);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String serializeEmbedding(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Double> parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double keywordScore(String query, String content) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        List<String> terms = List.of(normalizedQuery.split("[\\s，。！？、；,.!?;:]+"));
        long matched = terms.stream()
                .filter(term -> term.length() >= 2)
                .filter(normalizedContent::contains)
                .count();
        return terms.isEmpty() ? 0.0 : Math.min(1.0, matched / (double) terms.size());
    }
}
