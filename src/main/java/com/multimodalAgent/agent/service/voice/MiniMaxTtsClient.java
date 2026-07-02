package com.multimodalAgent.agent.service.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.dto.VoiceTtsRequest;
import com.multimodalAgent.agent.dto.VoiceTtsResponse;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class MiniMaxTtsClient {

    private final multimodalAgentProperties properties;
    private final WebClient.Builder webClientBuilder;

    public MiniMaxTtsClient(multimodalAgentProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<VoiceTtsResponse> synthesize(VoiceTtsRequest request) {
        multimodalAgentProperties.RealtimeTts tts = properties.getVoice().getTts();
        ensureConfigured(tts);
        String voice = hasText(request.voice()) ? request.voice() : tts.getVoice();
        String format = hasText(request.format()) ? request.format() : tts.getAudioFormat();

        Map<String, Object> voiceSetting = new LinkedHashMap<>();
        voiceSetting.put("voice_id", voice);
        voiceSetting.put("speed", 1.0);
        voiceSetting.put("vol", 1.0);
        voiceSetting.put("pitch", 0);

        Map<String, Object> audioSetting = new LinkedHashMap<>();
        audioSetting.put("sample_rate", tts.getSampleRate());
        audioSetting.put("bitrate", 128000);
        audioSetting.put("format", format);
        audioSetting.put("channel", 1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", tts.getModel());
        payload.put("text", request.text());
        payload.put("stream", false);
        payload.put("voice_setting", voiceSetting);
        payload.put("audio_setting", audioSetting);

        return webClientBuilder.build()
                .post()
                .uri(endpoint(tts))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tts.getApiKey())
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> toResponse(tts, voice, format, body));
    }

    private VoiceTtsResponse toResponse(
            multimodalAgentProperties.RealtimeTts tts,
            String voice,
            String format,
            JsonNode body
    ) {
        String audio = firstText(body,
                "/audio",
                "/data/audio",
                "/data/audio_base64",
                "/result/audio",
                "/payload/audio");
        if (!hasText(audio)) {
            throw new IllegalStateException("MiniMax TTS response did not include audio.");
        }
        String audioBase64 = looksLikeHex(audio) ? Base64.getEncoder().encodeToString(hexToBytes(audio)) : audio;
        int byteLength = Base64.getDecoder().decode(audioBase64).length;
        return new VoiceTtsResponse(tts.getProvider(), tts.getModel(), voice, format, audioBase64, byteLength);
    }

    private String endpoint(multimodalAgentProperties.RealtimeTts tts) {
        if (!hasText(tts.getGroupId())) {
            return tts.getEndpoint();
        }
        String separator = tts.getEndpoint().contains("?") ? "&" : "?";
        return tts.getEndpoint() + separator + "GroupId=" + tts.getGroupId();
    }

    private void ensureConfigured(multimodalAgentProperties.RealtimeTts tts) {
        if (!"minimax".equalsIgnoreCase(tts.getProvider())) {
            throw new IllegalStateException("VOICE_TTS_PROVIDER must be minimax.");
        }
        if (!realText(tts.getEndpoint()) || !realText(tts.getApiKey()) || !realText(tts.getGroupId())) {
            throw new IllegalStateException("Configure VOICE_TTS_ENDPOINT, VOICE_TTS_API_KEY and VOICE_TTS_GROUP_ID before using MiniMax TTS.");
        }
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = node.at(path);
            if (value != null && value.isTextual() && hasText(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }

    private boolean looksLikeHex(String value) {
        return value.length() % 2 == 0 && value.matches("(?i)^[0-9a-f]+$");
    }

    private byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int pos = index * 2;
            bytes[index] = (byte) Integer.parseInt(hex.substring(pos, pos + 2), 16);
        }
        return bytes;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean realText(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !normalized.startsWith("your-")
                && !normalized.startsWith("你的")
                && !normalized.contains("your-")
                && !normalized.contains("placeholder");
    }
}
