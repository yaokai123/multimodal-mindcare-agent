package com.multimodalAgent.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceControlEvent(
        String type,
        String roomName,
        String sessionId,
        String command,
        String phase,
        String text,
        String token,
        Boolean finalTranscript,
        Map<String, Long> metrics,
        Long sequence,
        Instant at
) {
    public static VoiceControlEvent status(String roomName, String sessionId, String text, long sequence) {
        return new VoiceControlEvent("status", roomName, sessionId, null, null, text, null, null, null, sequence, Instant.now());
    }

    public static VoiceControlEvent command(String roomName, String sessionId, String command, String text, long sequence) {
        return new VoiceControlEvent("command", roomName, sessionId, command, null, text, null, null, null, sequence, Instant.now());
    }

    public static VoiceControlEvent transcript(String roomName, String sessionId, String text, boolean finalTranscript, long sequence) {
        return new VoiceControlEvent("transcript", roomName, sessionId, null, "asr", text, null, finalTranscript, null, sequence, Instant.now());
    }

    public static VoiceControlEvent assistantToken(String roomName, String sessionId, String token, long sequence) {
        return new VoiceControlEvent("assistant_token", roomName, sessionId, null, "llm", null, token, null, null, sequence, Instant.now());
    }

    public static VoiceControlEvent phase(String roomName, String sessionId, String phase, long sequence) {
        return new VoiceControlEvent("phase", roomName, sessionId, null, phase, null, null, null, null, sequence, Instant.now());
    }

    public static VoiceControlEvent done(String roomName, String sessionId, String text, long sequence) {
        return new VoiceControlEvent("done", roomName, sessionId, null, "done", text, null, null, null, sequence, Instant.now());
    }

    public static VoiceControlEvent error(String roomName, String sessionId, String text, long sequence) {
        return new VoiceControlEvent("error", roomName, sessionId, null, null, text, null, null, null, sequence, Instant.now());
    }

    public static VoiceControlEvent latency(String roomName, String sessionId, Map<String, Long> metrics, long sequence) {
        return new VoiceControlEvent("latency", roomName, sessionId, null, "latency", null, null, null, metrics, sequence, Instant.now());
    }
}
