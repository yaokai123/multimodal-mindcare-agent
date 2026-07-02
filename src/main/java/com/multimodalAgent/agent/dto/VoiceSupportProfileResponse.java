package com.multimodalAgent.agent.dto;

import com.multimodalAgent.agent.domain.RiskLevel;

public record VoiceSupportProfileResponse(
        RiskLevel riskLevel,
        String supportMode,
        String ttsTone,
        String ttsVoice,
        String speakingPace,
        String adviceDensity,
        String journalPrompt,
        String sessionInstruction,
        boolean crisis,
        String safetyMessage,
        String suggestedNextAction
) {
}
