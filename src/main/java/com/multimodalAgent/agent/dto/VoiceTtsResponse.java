package com.multimodalAgent.agent.dto;

public record VoiceTtsResponse(
        String provider,
        String model,
        String voice,
        String format,
        String audioBase64,
        int byteLength
) {
}
