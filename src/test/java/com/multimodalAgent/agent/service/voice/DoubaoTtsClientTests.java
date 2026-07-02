package com.multimodalAgent.agent.service.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.dto.VoiceTtsRequest;
import java.util.Base64;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

@DisplayName("DoubaoTtsClient 单元测试")
class DoubaoTtsClientTests {

    private MockWebServer mockServer;
    private DoubaoTtsClient client;
    private multimodalAgentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 一段合法的 Base64 编码模拟音频（约 10 字节） */
    private static final String FAKE_AUDIO_BASE64 =
            Base64.getEncoder().encodeToString("fake-audio-bytes".getBytes());

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        properties = buildProperties(mockServer.url("/tts").toString());
        client = new DoubaoTtsClient(properties, WebClient.builder(), objectMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  正常合成路径
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("正常 TTS 合成")
    class SuccessfulSynthesis {

        @Test
        @DisplayName("返回顶层 audio 字段时应正确解析 Base64 音频")
        void topLevelAudioField() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("你好，我是心理助手")))
                    .assertNext(response -> {
                        assertThat(response.audioBase64()).isEqualTo(FAKE_AUDIO_BASE64);
                        assertThat(response.byteLength()).isGreaterThan(0);
                        assertThat(response.provider()).isEqualTo("doubao");
                        assertThat(response.format()).isEqualTo("mp3");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("返回 data.audio_base64 嵌套字段时应正确解析")
        void nestedDataAudioBase64Field() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"data\":{\"audio_base64\":\"" + FAKE_AUDIO_BASE64 + "\"}}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("今天心情怎么样")))
                    .assertNext(response -> {
                        assertThat(response.audioBase64()).isEqualTo(FAKE_AUDIO_BASE64);
                        assertThat(response.provider()).isEqualTo("doubao");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("返回 result.audio 嵌套字段时应正确解析")
        void nestedResultAudioField() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"result\":{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("放松心情")))
                    .assertNext(response -> assertThat(response.audioBase64()).isEqualTo(FAKE_AUDIO_BASE64))
                    .verifyComplete();
        }

        @Test
        @DisplayName("返回 payload.audio 字段时应正确解析")
        void payloadAudioField() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"payload\":{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("深呼吸练习")))
                    .assertNext(response -> assertThat(response.audioBase64()).isEqualTo(FAKE_AUDIO_BASE64))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Hex 编码音频应被转换为 Base64")
        void hexEncodedAudioConvertedToBase64() {
            // 将 "fakeaudio" 转成 hex 字符串
            String hex = bytesToHex("fakeaudio".getBytes());
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + hex + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("测试 Hex")))
                    .assertNext(response -> {
                        // 解码 Base64 应还原为原始字节
                        byte[] decoded = Base64.getDecoder().decode(response.audioBase64());
                        assertThat(new String(decoded)).isEqualTo("fakeaudio");
                    })
                    .verifyComplete();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  错误响应
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("错误响应处理")
    class ErrorResponseHandling {

        @Test
        @DisplayName("响应中无音频字段时应抛出包含错误描述的异常")
        void noAudioFieldThrows() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"code\":500,\"message\":\"Internal Server Error\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("错误测试")))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(IllegalStateException.class);
                        assertThat(error.getMessage()).contains("did not include audio");
                    })
                    .verify();
        }

        @Test
        @DisplayName("响应包含 error.message 时，异常信息应包含错误详情")
        void errorMessageIncludedInException() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"error\":{\"message\":\"voice id not found\"}}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("错误消息测试")))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(IllegalStateException.class);
                        assertThat(error.getMessage()).contains("voice id not found");
                    })
                    .verify();
        }

        @Test
        @DisplayName("响应为空对象时应抛出不包含具体错误信息的通用异常")
        void emptyResponseThrows() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("空响应测试")))
                    .expectErrorSatisfies(error ->
                            assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  配置校验
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("配置校验")
    class ConfigurationValidation {

        @Test
        @DisplayName("provider 不是 doubao 时应同步抛出 IllegalStateException")
        void wrongProviderShouldThrow() {
            multimodalAgentProperties props = buildPropertiesWithProvider("minimax",
                    "https://tts.example.com", "real-key", "real-resource");
            DoubaoTtsClient wrongClient = new DoubaoTtsClient(props, WebClient.builder(), objectMapper);

            // ensureConfigured() throws synchronously before a Mono is returned
            assertThatThrownBy(() -> wrongClient.synthesize(ttsRequest("测试")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_TTS_PROVIDER must be doubao");
        }

        @Test
        @DisplayName("endpoint 未配置时应同步抛出配置缺失异常")
        void missingEndpointShouldThrow() {
            multimodalAgentProperties props = buildPropertiesWithProvider("doubao",
                    "", "real-key", "real-resource");
            DoubaoTtsClient missingEndpointClient = new DoubaoTtsClient(props, WebClient.builder(), objectMapper);

            assertThatThrownBy(() -> missingEndpointClient.synthesize(ttsRequest("测试")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_TTS_ENDPOINT");
        }

        @Test
        @DisplayName("apiKey 为占位符时应同步抛出异常")
        void apiKeyPlaceholderShouldThrow() {
            multimodalAgentProperties props = buildPropertiesWithProvider("doubao",
                    "https://tts.example.com", "your-api-key", "real-resource");
            DoubaoTtsClient placeholderClient = new DoubaoTtsClient(props, WebClient.builder(), objectMapper);

            assertThatThrownBy(() -> placeholderClient.synthesize(ttsRequest("测试")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("resourceId 未配置时应同步抛出配置缺失异常")
        void missingResourceIdShouldThrow() {
            multimodalAgentProperties props = buildPropertiesWithProvider("doubao",
                    "https://tts.example.com", "real-key", "");
            DoubaoTtsClient missingResourceClient = new DoubaoTtsClient(props, WebClient.builder(), objectMapper);

            assertThatThrownBy(() -> missingResourceClient.synthesize(ttsRequest("测试")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_TTS_RESOURCE_ID");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  请求字段与 HTTP 头
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HTTP 请求头校验")
    class HttpHeaders {

        @Test
        @DisplayName("请求应携带 X-Api-Key 头")
        void requestShouldContainApiKeyHeader() throws Exception {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("测试头信息")))
                    .expectNextCount(1)
                    .verifyComplete();

            RecordedRequest recordedRequest = mockServer.takeRequest();
            assertThat(recordedRequest.getHeader("X-Api-Key")).isEqualTo("real-api-key-12345");
        }

        @Test
        @DisplayName("请求应携带 X-Api-Resource-Id 头")
        void requestShouldContainResourceIdHeader() throws Exception {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("测试资源头")))
                    .expectNextCount(1)
                    .verifyComplete();

            RecordedRequest recordedRequest = mockServer.takeRequest();
            assertThat(recordedRequest.getHeader("X-Api-Resource-Id")).isEqualTo("real-resource-id-abc");
        }

        @Test
        @DisplayName("请求应携带唯一的 X-Api-Request-Id 头")
        void requestShouldContainUniqueRequestId() throws Exception {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("第一次")))
                    .expectNextCount(1).verifyComplete();
            StepVerifier.create(client.synthesize(ttsRequest("第二次")))
                    .expectNextCount(1).verifyComplete();

            String requestId1 = mockServer.takeRequest().getHeader("X-Api-Connect-Id");
            String requestId2 = mockServer.takeRequest().getHeader("X-Api-Connect-Id");
            assertThat(requestId1).isNotBlank();
            assertThat(requestId2).isNotBlank();
            assertThat(requestId1).isNotEqualTo(requestId2);
        }

        @Test
        @DisplayName("璇锋眰浣撳簲浣跨敤 req_params 缁撴瀯")
        void requestBodyShouldUseVolcengineReqParamsShape() throws Exception {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            VoiceTtsRequest request = new VoiceTtsRequest(
                    "room-003", null, "hello tts", "speaker-001", "mp3");

            StepVerifier.create(client.synthesize(request))
                    .expectNextCount(1)
                    .verifyComplete();

            RecordedRequest recordedRequest = mockServer.takeRequest();
            String body = recordedRequest.getBody().readUtf8();
            assertThat(body).contains("\"req_params\"");
            assertThat(body).contains("\"speaker\":\"speaker-001\"");
            assertThat(body).contains("\"text\":\"hello tts\"");
            assertThat(body).contains("\"speaker\":\"speaker-001\"");
            assertThat(body).contains("\"audio_params\"");
            assertThat(body).contains("\"format\":\"mp3\"");
            assertThat(body).contains("\"sample_rate\":32000");
            assertThat(recordedRequest.getHeader("X-Control-Require-Usage-Tokens-Return")).isEqualTo("*");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  响应字段校验
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("响应字段校验")
    class ResponseFields {

        @Test
        @DisplayName("请求中指定的 voice 应覆盖配置默认 voice")
        void requestVoiceOverridesDefault() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            VoiceTtsRequest request = new VoiceTtsRequest(
                    "room-001", null, "自定义声音测试", "custom-voice-id", null);

            StepVerifier.create(client.synthesize(request))
                    .assertNext(response -> assertThat(response.voice()).isEqualTo("custom-voice-id"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("请求中指定的 format 应覆盖配置默认 format")
        void requestFormatOverridesDefault() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            VoiceTtsRequest request = new VoiceTtsRequest(
                    "room-002", null, "音频格式测试", null, "wav");

            StepVerifier.create(client.synthesize(request))
                    .assertNext(response -> assertThat(response.format()).isEqualTo("wav"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("byteLength 应等于解码后 Base64 的字节长度")
        void byteLengthEqualsDecodedLength() {
            byte[] audioData = "this-is-16-bytes".getBytes();
            String audioBase64 = Base64.getEncoder().encodeToString(audioData);
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + audioBase64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("字节长度测试")))
                    .assertNext(response -> assertThat(response.byteLength()).isEqualTo(audioData.length))
                    .verifyComplete();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  辅助构造
    // ──────────────────────────────────────────────────────────────────────

    private VoiceTtsRequest ttsRequest(String text) {
        return new VoiceTtsRequest("room-test-001", null, text, null, null);
    }

    private multimodalAgentProperties buildProperties(String endpoint) {
        return buildPropertiesWithProvider("doubao", endpoint,
                "real-api-key-12345", "real-resource-id-abc");
    }

    private multimodalAgentProperties buildPropertiesWithProvider(
            String provider, String endpoint, String apiKey, String resourceId) {
        multimodalAgentProperties props = new multimodalAgentProperties();
        multimodalAgentProperties.RealtimeTts tts = props.getVoice().getTts();
        tts.setProvider(provider);
        tts.setModel("doubao-tts");
        tts.setEndpoint(endpoint);
        tts.setApiKey(apiKey);
        tts.setResourceId(resourceId);
        tts.setVoice("default-voice");
        tts.setAudioFormat("mp3");
        tts.setSampleRate(32000);
        return props;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
