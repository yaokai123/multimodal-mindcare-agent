package com.multimodalAgent.agent.service.voice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.dto.VoiceTtsRequest;
import com.multimodalAgent.agent.dto.VoiceTtsResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class DoubaoTtsClient {

    private final multimodalAgentProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

    public DoubaoTtsClient(
            multimodalAgentProperties properties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public Mono<VoiceTtsResponse> synthesize(VoiceTtsRequest request) {
        multimodalAgentProperties.RealtimeTts tts = properties.getVoice().getTts();
        ensureConfigured(tts);
        return isWebSocket(tts.getEndpoint())
                ? synthesizeWithWebSocket(tts, request)
                : synthesizeWithHttp(tts, request);
    }

    private Mono<VoiceTtsResponse> synthesizeWithHttp(
            multimodalAgentProperties.RealtimeTts tts,
            VoiceTtsRequest request
    ) {
        return webClientBuilder.build()
                .post()
                .uri(tts.getEndpoint())
                .headers(headers -> applyVolcengineHeaders(headers, tts))
                .bodyValue(payload(tts, request))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> toResponse(tts, request, body));
    }

    private Mono<VoiceTtsResponse> synthesizeWithWebSocket(
            multimodalAgentProperties.RealtimeTts tts,
            VoiceTtsRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        applyVolcengineHeaders(headers, tts);
        byte[] payload = binaryRequest(payload(tts, request));
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        Sinks.One<VoiceTtsResponse> result = Sinks.one();
        Mono<Void> exchange = webSocketClient.execute(URI.create(tts.getEndpoint()), headers, session -> {
            Mono<Void> send = session.send(Mono.just(session.binaryMessage(factory -> factory.wrap(payload))));
            Mono<Void> receive = session.receive()
                    .map(message -> parseWebSocketMessage(message, audio))
                    .doOnNext(frame -> {
                        if (frame.response() != null) {
                            result.tryEmitValue(frame.response());
                        }
                    })
                    .takeUntil(TtsFrame::last)
                    .timeout(Duration.ofSeconds(30))
                    .doOnComplete(() -> {
                        if (audio.size() > 0) {
                            result.tryEmitValue(toBinaryResponse(tts, request, audio.toByteArray()));
                        }
                    })
                    .then();
            return send.then(receive);
        }).doOnError(result::tryEmitError);

        return exchange
                .then(result.asMono());
    }

    private Map<String, Object> payload(multimodalAgentProperties.RealtimeTts tts, VoiceTtsRequest request) {
        String voice = hasText(request.voice()) ? request.voice() : (realText(tts.getVoice()) ? tts.getVoice() : "");
        String format = hasText(request.format()) ? request.format() : tts.getAudioFormat();

        Map<String, Object> audioParams = new LinkedHashMap<>();
        audioParams.put("format", format);
        audioParams.put("sample_rate", tts.getSampleRate());

        Map<String, Object> reqParams = new LinkedHashMap<>();
        if (hasText(voice)) {
            reqParams.put("speaker", voice);
        }
        reqParams.put("text", request.text());
        reqParams.put("audio_params", audioParams);

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("req_params", reqParams);
        requestPayload.put("text", request.text());
        if (hasText(voice)) {
            requestPayload.put("speaker", voice);
        }
        return requestPayload;
    }

    private VoiceTtsResponse toResponse(
            multimodalAgentProperties.RealtimeTts tts,
            VoiceTtsRequest request,
            JsonNode body
    ) {
        String voice = hasText(request.voice()) ? request.voice() : tts.getVoice();
        String format = hasText(request.format()) ? request.format() : tts.getAudioFormat();
        String audio = firstText(body,
                "/audio",
                "/audio_base64",
                "/data",
                "/data/audio",
                "/data/audio_base64",
                "/data/audio_data",
                "/result/audio",
                "/result/audio_base64",
                "/result/data",
                "/payload/audio");
        if (!hasText(audio)) {
            String message = firstText(body, "/message", "/error/message", "/error", "/code");
            throw new IllegalStateException(hasText(message)
                    ? "Doubao TTS response did not include audio: " + message
                    : "Doubao TTS response did not include audio.");
        }
        String audioBase64 = looksLikeHex(audio) ? Base64.getEncoder().encodeToString(hexToBytes(audio)) : audio;
        int byteLength = Base64.getDecoder().decode(audioBase64).length;
        return new VoiceTtsResponse(tts.getProvider(), tts.getModel(), voice, format, audioBase64, byteLength);
    }

    private boolean containsAudioOrError(JsonNode node) {
        return hasText(firstText(node,
                "/audio",
                "/audio_base64",
                "/data",
                "/data/audio",
                "/data/audio_base64",
                "/data/audio_data",
                "/result/audio",
                "/result/audio_base64",
                "/result/data",
                "/payload/audio",
                "/message",
                "/error/message",
                "/error",
                "/code"));
    }

    private VoiceTtsResponse toBinaryResponse(
            multimodalAgentProperties.RealtimeTts tts,
            VoiceTtsRequest request,
            byte[] audio
    ) {
        String voice = hasText(request.voice()) ? request.voice() : tts.getVoice();
        String format = hasText(request.format()) ? request.format() : tts.getAudioFormat();
        return new VoiceTtsResponse(
                tts.getProvider(),
                tts.getModel(),
                voice,
                format,
                Base64.getEncoder().encodeToString(audio),
                audio.length);
    }

    private byte[] binaryRequest(Map<String, Object> payload) {
        byte[] jsonBytes = json(payload).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + jsonBytes.length);
        buffer.put(new byte[] {0x11, 0x10, 0x10, 0x00});
        buffer.putInt(jsonBytes.length);
        buffer.put(jsonBytes);
        return buffer.array();
    }

    private TtsFrame parseWebSocketMessage(WebSocketMessage message, ByteArrayOutputStream audio) {
        if (message.getType() == WebSocketMessage.Type.TEXT) {
            JsonNode body = jsonNode(message.getPayloadAsText());
            return new TtsFrame(toResponse(properties.getVoice().getTts(), emptyRequest(), body), true);
        }
        ByteBuffer buffer = message.getPayload().asByteBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return parseBinaryFrame(bytes, audio);
    }

    private TtsFrame parseBinaryFrame(byte[] bytes, ByteArrayOutputStream audio) {
        if (bytes.length < 4) {
            return new TtsFrame(null, false);
        }
        int headerSize = (bytes[0] & 0x0f) * 4;
        int messageType = (bytes[1] & 0xf0) >> 4;
        int flags = bytes[1] & 0x0f;
        int compression = bytes[2] & 0x0f;
        ByteBuffer payload = ByteBuffer.wrap(bytes, Math.min(headerSize, bytes.length), Math.max(0, bytes.length - headerSize));

        if (messageType == 0x0b) {
            int sequence = (flags == 0x01 || flags == 0x03) ? readInt(payload) : 0;
            int event = flags == 0x04 ? readEventAndSession(payload) : 0;
            int payloadSize = readInt(payload);
            if (payloadSize > 0 && payload.remaining() >= payloadSize) {
                byte[] chunk = new byte[payloadSize];
                payload.get(chunk);
                write(audio, chunk);
            }
            return new TtsFrame(null, sequence < 0 || event == 152);
        }

        if (messageType == 0x09) {
            int sequence = (flags == 0x01 || flags == 0x03) ? readInt(payload) : 0;
            int event = flags == 0x04 ? readEventAndSession(payload) : 0;
            int payloadSize = readInt(payload);
            if (payloadSize > 0 && payload.remaining() >= payloadSize) {
                byte[] data = new byte[payloadSize];
                payload.get(data);
                String json = new String(decompressIfNeeded(data, compression), StandardCharsets.UTF_8);
                VoiceTtsResponse response = containsAudioOrError(jsonNode(json))
                        ? toResponse(properties.getVoice().getTts(), emptyRequest(), jsonNode(json))
                        : null;
                return new TtsFrame(response, sequence < 0 || event == 152);
            }
            return new TtsFrame(null, sequence < 0 || event == 152);
        }

        if (messageType == 0x0f) {
            int code = readInt(payload);
            if (flags == 0x04) {
                readEventAndSession(payload);
            }
            int payloadSize = readInt(payload);
            byte[] data = new byte[Math.max(0, Math.min(payloadSize, payload.remaining()))];
            payload.get(data);
            String message = new String(decompressIfNeeded(data, compression), StandardCharsets.UTF_8);
            throw new IllegalStateException("Doubao TTS error " + code + ": " + message);
        }

        return new TtsFrame(null, false);
    }

    private int readEventAndSession(ByteBuffer payload) {
        int event = readInt(payload);
        if (event != 1 && event != 2 && event != 50 && event != 51 && event != 52) {
            int sessionIdSize = readInt(payload);
            if (sessionIdSize > 0 && payload.remaining() >= sessionIdSize) {
                payload.position(payload.position() + sessionIdSize);
            }
        }
        if (event == 50 || event == 51 || event == 52) {
            int connectIdSize = readInt(payload);
            if (connectIdSize > 0 && payload.remaining() >= connectIdSize) {
                payload.position(payload.position() + connectIdSize);
            }
        }
        return event;
    }

    private int readInt(ByteBuffer buffer) {
        return buffer.remaining() >= 4 ? buffer.getInt() : 0;
    }

    private void write(ByteArrayOutputStream output, byte[] chunk) {
        try {
            output.write(chunk);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to collect Doubao TTS audio.", exception);
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
            throw new IllegalStateException("Failed to gzip Doubao TTS request.", exception);
        }
    }

    private byte[] decompressIfNeeded(byte[] data, int compression) {
        if (compression != 1) {
            return data;
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode Doubao TTS response.", exception);
        }
    }

    private void applyVolcengineHeaders(HttpHeaders headers, multimodalAgentProperties.RealtimeTts tts) {
        headers.set("X-Api-Key", apiKey(tts));
        headers.set("X-Api-Resource-Id", resourceId(tts));
        headers.set("X-Api-Connect-Id", UUID.randomUUID().toString());
        headers.set("X-Control-Require-Usage-Tokens-Return", "*");
    }

    private void ensureConfigured(multimodalAgentProperties.RealtimeTts tts) {
        if (!"doubao".equalsIgnoreCase(tts.getProvider())) {
            throw new IllegalStateException("VOICE_TTS_PROVIDER must be doubao.");
        }
        if (!realText(tts.getEndpoint()) || !realText(apiKey(tts)) || !realText(resourceId(tts))) {
            throw new IllegalStateException("Configure VOICE_TTS_ENDPOINT, VOICE_TTS_API_KEY and VOICE_TTS_RESOURCE_ID before using Doubao TTS.");
        }
    }

    private String apiKey(multimodalAgentProperties.RealtimeTts tts) {
        return tts.getApiKey();
    }

    private String resourceId(multimodalAgentProperties.RealtimeTts tts) {
        return hasText(tts.getResourceId()) ? tts.getResourceId() : tts.getGroupId();
    }

    private JsonNode jsonNode(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Doubao TTS returned non-JSON text.", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to create Doubao TTS payload.", exception);
        }
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = node.at(path);
            if (value != null && value.isTextual() && hasText(value.asText())) {
                return value.asText();
            }
            if (value != null && value.isNumber()) {
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
                && !normalized.contains("your-")
                && !normalized.contains("placeholder");
    }

    private VoiceTtsRequest emptyRequest() {
        return new VoiceTtsRequest("", null, "", null, null);
    }

    private record TtsFrame(VoiceTtsResponse response, boolean last) {
    }
}
