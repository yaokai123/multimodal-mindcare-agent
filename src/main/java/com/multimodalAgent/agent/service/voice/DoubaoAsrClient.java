package com.multimodalAgent.agent.service.voice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.dto.VoiceAsrRequest;
import com.multimodalAgent.agent.dto.VoiceAsrResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class DoubaoAsrClient {

    private final multimodalAgentProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

    public DoubaoAsrClient(
            multimodalAgentProperties properties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public Mono<VoiceAsrResponse> transcribe(VoiceAsrRequest request) {
        multimodalAgentProperties.RealtimeAsr asr = properties.getVoice().getAsr();
        ensureConfigured(asr);
        return isWebSocket(asr.getEndpoint())
                ? transcribeWithWebSocket(asr, request)
                : transcribeWithHttp(asr, request);
    }

    private Mono<VoiceAsrResponse> transcribeWithHttp(
            multimodalAgentProperties.RealtimeAsr asr,
            VoiceAsrRequest request
    ) {
        return webClientBuilder.build()
                .post()
                .uri(asr.getEndpoint())
                .headers(headers -> applyVolcengineHeaders(headers, asr))
                .bodyValue(payload(asr, request))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> toResponse(asr, request, body));
    }

    private Mono<VoiceAsrResponse> transcribeWithWebSocket(
            multimodalAgentProperties.RealtimeAsr asr,
            VoiceAsrRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        applyVolcengineHeaders(headers, asr);
        byte[] configFrame = binaryFullClientRequest(webSocketPayload(asr, request));
        byte[] audioFrame = binaryAudioOnlyRequest(request, true);
        Sinks.One<JsonNode> result = Sinks.one();

        Mono<Void> exchange = webSocketClient.execute(URI.create(asr.getEndpoint()), headers, session -> {
                    Mono<Void> send = session.send(Flux.just(configFrame, audioFrame)
                            .map(frame -> session.binaryMessage(factory -> factory.wrap(frame))));
                    Mono<Void> receive = session.receive()
                            .map(this::parseWebSocketMessage)
                            .filter(Objects::nonNull)
                            .takeUntil(frame -> containsRecognizedTextOrError(frame.body()) || frame.last())
                            .take(Duration.ofSeconds(25))
                            .collectList()
                            .map(this::bestFrame)
                            .doOnNext(result::tryEmitValue)
                            .then();
                    return send.then(receive);
                }).doOnError(result::tryEmitError);

        return exchange
                .then(result.asMono())
                .map(body -> toResponse(asr, request, body));
    }

    private JsonNode bestFrame(List<AsrFrame> frames) {
        return frames.stream()
                .map(AsrFrame::body)
                .filter(this::containsRecognizedTextOrError)
                .findFirst()
                .or(() -> frames.stream().filter(AsrFrame::last).map(AsrFrame::body).findFirst())
                .orElseThrow(() -> new IllegalStateException("Doubao ASR did not return a final transcript frame."));
    }

    private Map<String, Object> payload(multimodalAgentProperties.RealtimeAsr asr, VoiceAsrRequest request) {
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", hasText(request.format()) ? request.format() : asr.getFormat());
        audio.put("sample_rate", request.sampleRate() == null ? asr.getSampleRate() : request.sampleRate());
        audio.put("language", hasText(request.language()) ? request.language() : asr.getLanguage());
        audio.put("data", request.audioBase64());

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("model", asr.getModel());
        requestPayload.put("app_key", asr.getAppId());
        requestPayload.put("resource_id", asr.getCluster());
        requestPayload.put("audio", audio);
        requestPayload.put("final", request.finalTranscript());
        requestPayload.put("request_id", UUID.randomUUID().toString());
        return requestPayload;
    }

    private Map<String, Object> webSocketPayload(multimodalAgentProperties.RealtimeAsr asr, VoiceAsrRequest request) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", hasText(request.roomName()) ? request.roomName() : UUID.randomUUID().toString());

        String format = hasText(request.format()) ? request.format() : asr.getFormat();
        int sampleRate = request.sampleRate() == null ? asr.getSampleRate() : request.sampleRate();

        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", format);
        audio.put("codec", "raw");
        audio.put("rate", sampleRate);
        audio.put("sample_rate", sampleRate);
        audio.put("bits", 16);
        audio.put("channel", 1);
        audio.put("language", hasText(request.language()) ? request.language() : asr.getLanguage());

        Map<String, Object> asrRequest = new LinkedHashMap<>();
        asrRequest.put("reqid", UUID.randomUUID().toString());
        asrRequest.put("model_name", asr.getModel());
        asrRequest.put("model", asr.getModel());
        asrRequest.put("result_type", request.finalTranscript() ? "full" : "single");
        asrRequest.put("enable_itn", true);
        asrRequest.put("enable_punc", true);
        asrRequest.put("enable_ddc", false);

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("user", user);
        requestPayload.put("audio", audio);
        requestPayload.put("request", asrRequest);
        requestPayload.put("model", asr.getModel());
        requestPayload.put("app_key", asr.getAppId());
        requestPayload.put("resource_id", asr.getCluster());
        requestPayload.put("request_id", UUID.randomUUID().toString());
        return requestPayload;
    }

    private VoiceAsrResponse toResponse(
            multimodalAgentProperties.RealtimeAsr asr,
            VoiceAsrRequest request,
            JsonNode body
    ) {
        String text = firstText(body,
                "/text",
                "/result/text",
                "/result/0/text",
                "/data/text",
                "/payload/text",
                "/result/utterances/0/text");
        double confidence = firstDouble(body, 0.0,
                "/confidence",
                "/result/confidence",
                "/data/confidence",
                "/result/utterances/0/confidence");
        if (!hasText(text)) {
            throw new IllegalStateException("Doubao ASR response did not include recognized text.");
        }
        return new VoiceAsrResponse(asr.getProvider(), asr.getModel(), text, confidence, request.finalTranscript());
    }

    private boolean containsRecognizedTextOrError(JsonNode body) {
        return hasText(firstText(body,
                "/text",
                "/result/text",
                "/result/0/text",
                "/data/text",
                "/payload/text",
                "/result/utterances/0/text",
                "/result/additions/result",
                "/payload_msg/result/text",
                "/payload_msg/text",
                "/message",
                "/error",
                "/error/message",
                "/code"));
    }

    private byte[] binaryFullClientRequest(Map<String, Object> payload) {
        byte[] jsonBytes = json(payload).getBytes(StandardCharsets.UTF_8);
        return binaryFrame(new byte[] {0x11, 0x10, 0x11, 0x00}, gzip(jsonBytes));
    }

    private byte[] binaryAudioOnlyRequest(VoiceAsrRequest request, boolean last) {
        byte[] audio = decodeAudio(request.audioBase64());
        byte flags = last ? (byte) 0x23 : (byte) 0x21;
        byte[] compressed = gzip(audio);
        ByteBuffer buffer = ByteBuffer.allocate(12 + compressed.length);
        buffer.put(new byte[] {0x11, flags, 0x11, 0x00});
        buffer.putInt(last ? -2 : 1);
        buffer.putInt(compressed.length);
        buffer.put(compressed);
        return buffer.array();
    }

    private byte[] binaryFrame(byte[] header, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length);
        buffer.put(header);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private AsrFrame parseWebSocketMessage(WebSocketMessage message) {
        if (message.getType() == WebSocketMessage.Type.TEXT) {
            return new AsrFrame(jsonNode(message.getPayloadAsText()), true);
        }
        ByteBuffer buffer = message.getPayload().asByteBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return parseBinaryFrame(bytes);
    }

    private AsrFrame parseBinaryFrame(byte[] bytes) {
        if (bytes.length < 4) {
            return null;
        }
        int headerSize = Math.max(4, (bytes[0] & 0x0f) * 4);
        int messageType = (bytes[1] & 0xf0) >> 4;
        int flags = bytes[1] & 0x0f;
        int compression = bytes[2] & 0x0f;
        ByteBuffer payload = ByteBuffer.wrap(bytes, Math.min(headerSize, bytes.length), Math.max(0, bytes.length - headerSize));

        if (messageType == 0x09) {
            int sequence = 0;
            if (flags != 0) {
                sequence = readInt(payload);
            }
            JsonNode body = readJsonPayload(payload, compression);
            return body == null ? null : new AsrFrame(body, sequence < 0);
        }

        if (messageType == 0x0b) {
            int sequence = 0;
            if (flags != 0) {
                sequence = readInt(payload);
            }
            JsonNode body = readJsonPayload(payload, compression);
            return body == null ? null : new AsrFrame(body, sequence < 0);
        }

        if (messageType == 0x0f) {
            int code = readInt(payload);
            int payloadSize = readInt(payload);
            byte[] data = readPayloadBytes(payload, payloadSize);
            String message = new String(decompressIfNeeded(data, compression), StandardCharsets.UTF_8);
            throw new IllegalStateException("Doubao ASR error " + code + ": " + message);
        }

        return null;
    }

    private JsonNode readJsonPayload(ByteBuffer payload, int compression) {
        int payloadSize = readInt(payload);
        if (payloadSize <= 0 || payload.remaining() < payloadSize) {
            return null;
        }
        byte[] data = readPayloadBytes(payload, payloadSize);
        String json = new String(decompressIfNeeded(data, compression), StandardCharsets.UTF_8);
        return jsonNode(json);
    }

    private int readInt(ByteBuffer buffer) {
        return buffer.remaining() >= 4 ? buffer.getInt() : 0;
    }

    private byte[] readPayloadBytes(ByteBuffer payload, int payloadSize) {
        byte[] data = new byte[Math.max(0, Math.min(payloadSize, payload.remaining()))];
        payload.get(data);
        return data;
    }

    private byte[] decodeAudio(String audioBase64) {
        try {
            return Base64.getDecoder().decode(audioBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("VOICE ASR audioBase64 must be valid Base64 audio data.", exception);
        }
    }

    private byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(data);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to gzip Doubao ASR request.", exception);
        }
    }

    private byte[] decompressIfNeeded(byte[] data, int compression) {
        if (compression != 1) {
            return data;
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode Doubao ASR response.", exception);
        }
    }

    private void applyVolcengineHeaders(HttpHeaders headers, multimodalAgentProperties.RealtimeAsr asr) {
        headers.set("X-Api-App-Key", asr.getAppId());
        headers.set("X-Api-Access-Key", asr.getApiKey());
        headers.set("X-Api-Resource-Id", asr.getCluster());
        headers.set("X-Api-Connect-Id", UUID.randomUUID().toString());
        headers.setBearerAuth(asr.getApiKey());
    }

    private void ensureConfigured(multimodalAgentProperties.RealtimeAsr asr) {
        if (!"doubao".equalsIgnoreCase(asr.getProvider())) {
            throw new IllegalStateException("VOICE_ASR_PROVIDER must be doubao.");
        }
        if (!realText(asr.getEndpoint()) || !realText(asr.getApiKey()) || !realText(asr.getAppId()) || !realText(asr.getCluster())) {
            throw new IllegalStateException("Configure VOICE_ASR_ENDPOINT, VOICE_ASR_API_KEY, VOICE_ASR_APP_ID and VOICE_ASR_CLUSTER before using Doubao ASR.");
        }
    }

    private JsonNode jsonNode(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Doubao ASR returned non-JSON text.", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to create Doubao ASR payload.", exception);
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

    private double firstDouble(JsonNode node, double fallback, String... paths) {
        for (String path : paths) {
            JsonNode value = node.at(path);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }
        return fallback;
    }

    private boolean isWebSocket(String endpoint) {
        return endpoint != null && (endpoint.startsWith("ws://") || endpoint.startsWith("wss://"));
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

    private record AsrFrame(JsonNode body, boolean last) {
    }
}
