package com.multimodalAgent.agent.dto;

import java.time.Instant;
import java.util.List;

public record VoiceSessionResponse(
        boolean enabled,
        boolean configured,
        String status,
        String roomName,
        String participantName,
        String livekitUrl,
        String livekitToken,
        String asrProvider,
        String asrModel,
        String llmProvider,
        String llmModel,
        String ttsProvider,
        String ttsModel,
        String ttsVoice,
        boolean interruptEnabled,
        String knowledgeScope,
        String supportMode,
        String ttsTone,
        String recommendedTtsVoice,
        String speakingPace,
        String adviceDensity,
        boolean crisisMode,
        String sessionInstruction,
        List<String> pipeline,
        Instant expiresAt
) {
}
