package com.multimodalAgent.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.multimodalAgent.agent.domain.IntentType;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.service.multimodal.MultimodalSignal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * SSE 流式聊天事件。
 *
 * <p>meta 先返回会话 id，token 持续返回模型片段，done 表示本轮结束。</p>
 */
public record ChatStreamEvent(
        String type,
        String sessionId,
        String content,
        String phase,
        IntentType intent,
        RiskLevel riskLevel,
        List<KnowledgeCitation> citations,
        Boolean grounded,
        String review,
        String answerBasis,
        List<MultimodalSignal> multimodalSignals
) {
    public static ChatStreamEvent meta(String sessionId) {
        return new ChatStreamEvent("meta", sessionId, "", null, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent token(String sessionId, String content) {
        return new ChatStreamEvent("token", sessionId, content, null, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent phase(String sessionId, String phase) {
        return new ChatStreamEvent("phase", sessionId, "", phase, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent citations(String sessionId, List<KnowledgeCitation> citations, boolean grounded, String review) {
        String answerBasis = grounded && citations != null && citations.stream().anyMatch(KnowledgeCitation::shown)
                ? "KNOWLEDGE_BASE"
                : citations == null || citations.isEmpty()
                ? "NO_SUFFICIENT_EVIDENCE"
                : "GENERAL_SUPPORT_LOW_RELEVANCE";
        return new ChatStreamEvent("citations", sessionId, "", null, null, null, citations, grounded, review, answerBasis, null);
    }

    public static ChatStreamEvent multimodal(String sessionId, List<MultimodalSignal> signals) {
        return new ChatStreamEvent("multimodal", sessionId, "", null, null, null, null, null, null, null, signals);
    }

    public static ChatStreamEvent done(String sessionId) {
        return new ChatStreamEvent("done", sessionId, "", null, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent error(String sessionId, String content) {
        return new ChatStreamEvent("error", sessionId, content, null, null, null, null, null, null, null, null);
    }
}
