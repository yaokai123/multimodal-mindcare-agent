package com.multimodalAgent.agent.dto;

public record VoiceAsrResponse(
        String provider,
        String model,
        String text,
        double confidence,
        boolean finalTranscript
) {
}
