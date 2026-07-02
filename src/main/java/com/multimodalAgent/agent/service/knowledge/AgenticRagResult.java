package com.multimodalAgent.agent.service.knowledge;

import com.multimodalAgent.agent.dto.KnowledgeCitation;
import java.util.List;

public record AgenticRagResult(
        String plan,
        List<String> queries,
        List<SearchResult> evidence,
        String review,
        boolean sufficient
) {
    public static AgenticRagResult empty() {
        return new AgenticRagResult("RAG not triggered", List.of(), List.of(), "none", false);
    }

    public String contextBlock() {
        if (evidence.isEmpty()) {
            return """
                    Agentic RAG plan: %s
                    Answer basis: NO_SUFFICIENT_EVIDENCE
                    Agentic RAG review: no sufficient knowledge was retrieved. The answer must say the knowledge base is limited and provide safe general support.
                    """.formatted(plan);
        }
        String evidenceText = String.join("\n\n", evidence.stream()
                .map(result -> "- [%s | %s | score %.3f] %s"
                        .formatted(result.source(), result.category(), result.score(), result.content()))
                .toList());
        return """
                Agentic RAG plan: %s
                Agentic RAG queries: %s
                Answer basis: %s
                Agentic RAG review: %s
                Retrieved knowledge:
                %s
                """.formatted(plan, String.join("; ", queries), sufficient ? "KNOWLEDGE_BASE" : "GENERAL_SUPPORT_WITH_LIMITED_KNOWLEDGE", review, evidenceText);
    }

    public List<KnowledgeCitation> citations() {
        return evidence.stream()
                .limit(4)
                .map(result -> new KnowledgeCitation(
                        result.chunkId(),
                        result.source(),
                        result.category(),
                        result.tags(),
                        result.audience(),
                        result.riskLevel(),
                        result.score(),
                        excerpt(result.content()),
                        result.shown(),
                        result.basis()))
                .toList();
    }

    private String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
    }
}
