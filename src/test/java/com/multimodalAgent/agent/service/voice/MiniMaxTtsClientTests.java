package com.multimodalAgent.agent.service.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

@DisplayName("MiniMaxTtsClient 单元测试")
class MiniMaxTtsClientTests {

    private MockWebServer mockServer;
    private MiniMaxTtsClient client;
    private multimodalAgentProperties properties;

    private static final String FAKE_AUDIO_BASE64 =
            Base64.getEncoder().encodeToString("minimax-audio-data".getBytes());

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        properties = buildProperties(mockServer.url("/tts").toString(), "group-888");
        client = new MiniMaxTtsClient(properties, WebClient.builder());
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
        @DisplayName("返回顶层 audio 字段时应正确解析")
        void topLevelAudioField() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("你好，这是 MiniMax 语音")))
                    .assertNext(response -> {
                        assertThat(response.audioBase64()).isEqualTo(FAKE_AUDIO_BASE64);
                        assertThat(response.provider()).isEqualTo("minimax");
                        assertThat(response.byteLength()).isGreaterThan(0);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("返回 data.audio 嵌套字段时应正确解析")
        void nestedDataAudioField() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"data\":{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("嵌套字段测试")))
                    .assertNext(response -> assertThat(response.audioBase64()).isEqualTo(FAKE_AUDIO_BASE64))
                    .verifyComplete();
        }

        @Test
        @DisplayName("返回 result.audio 嵌套字段时应正确解析")
        void nestedResultAudioField() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"result\":{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("result 路径测试")))
                    .assertNext(response -> assertThat(response.audioBase64()).isEqualTo(FAKE_AUDIO_BASE64))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Hex 格式音频应被转换为 Base64")
        void hexAudioConvertedToBase64() {
            String hex = bytesToHex("minimaxaudio".getBytes());
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + hex + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("Hex 转换测试")))
                    .assertNext(response -> {
                        byte[] decoded = Base64.getDecoder().decode(response.audioBase64());
                        assertThat(new String(decoded)).isEqualTo("minimaxaudio");
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
        @DisplayName("无音频字段时应抛出 IllegalStateException")
        void noAudioFieldThrows() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"code\":1001}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("无音频测试")))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(IllegalStateException.class);
                        assertThat(error.getMessage()).contains("did not include audio");
                    })
                    .verify();
        }

        @Test
        @DisplayName("响应为空对象时应抛出异常")
        void emptyResponseThrows() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("空对象测试")))
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
        @DisplayName("provider 不是 minimax 时应同步抛出 IllegalStateException")
        void wrongProviderShouldThrow() {
            multimodalAgentProperties props = buildProperties("https://tts.example.com", "grp-abc");
            props.getVoice().getTts().setProvider("doubao");
            MiniMaxTtsClient wrongClient = new MiniMaxTtsClient(props, WebClient.builder());

            // ensureConfigured() throws synchronously before a Mono is returned
            assertThatThrownBy(() -> wrongClient.synthesize(ttsRequest("测试")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_TTS_PROVIDER must be minimax");
        }

        @Test
        @DisplayName("groupId 未配置时应同步抛出配置缺失异常")
        void missingGroupIdShouldThrow() {
            multimodalAgentProperties props = buildProperties("https://tts.example.com", "");
            MiniMaxTtsClient missingGroupClient = new MiniMaxTtsClient(props, WebClient.builder());

            assertThatThrownBy(() -> missingGroupClient.synthesize(ttsRequest("测试")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_TTS_GROUP_ID");
        }

        @Test
        @DisplayName("apiKey 未配置时应同步抛出配置缺失异常")
        void missingApiKeyShouldThrow() {
            multimodalAgentProperties props = buildProperties("https://tts.example.com", "grp-123");
            props.getVoice().getTts().setApiKey("");
            MiniMaxTtsClient missingKeyClient = new MiniMaxTtsClient(props, WebClient.builder());

            assertThatThrownBy(() -> missingKeyClient.synthesize(ttsRequest("测试")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_TTS_API_KEY");
        }

        @Test
        @DisplayName("endpoint 未配置时应同步抛出配置缺失异常")
        void missingEndpointShouldThrow() {
            multimodalAgentProperties props = buildProperties("", "grp-123");
            MiniMaxTtsClient missingEndpointClient = new MiniMaxTtsClient(props, WebClient.builder());

            assertThatThrownBy(() -> missingEndpointClient.synthesize(ttsRequest("测试")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_TTS_ENDPOINT");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  HTTP 请求头与 URL
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HTTP 请求头与 URL 构造")
    class HttpRequestDetails {

        @Test
        @DisplayName("请求应携带 Bearer 认证头")
        void requestShouldHaveBearerAuth() throws Exception {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("认证头测试")))
                    .expectNextCount(1)
                    .verifyComplete();

            RecordedRequest request = mockServer.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer real-api-key-xyz");
        }

        @Test
        @DisplayName("URL 应追加 GroupId 查询参数")
        void urlShouldContainGroupId() throws Exception {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("GroupId 测试")))
                    .expectNextCount(1)
                    .verifyComplete();

            RecordedRequest request = mockServer.takeRequest();
            assertThat(request.getPath()).contains("GroupId=group-888");
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
                    "room-001", null, "自定义声音", "custom-minimax-voice", null);

            StepVerifier.create(client.synthesize(request))
                    .assertNext(response -> assertThat(response.voice()).isEqualTo("custom-minimax-voice"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("byteLength 应等于 Base64 解码后字节数")
        void byteLengthEqualsDecodedLength() {
            byte[] audioData = "exactly-16-bytes".getBytes();
            String audioBase64 = Base64.getEncoder().encodeToString(audioData);
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + audioBase64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("字节长度测试")))
                    .assertNext(response -> assertThat(response.byteLength()).isEqualTo(audioData.length))
                    .verifyComplete();
        }

        @Test
        @DisplayName("model 字段应来自配置")
        void modelFieldComesFromConfig() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"audio\":\"" + FAKE_AUDIO_BASE64 + "\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.synthesize(ttsRequest("模型字段测试")))
                    .assertNext(response -> assertThat(response.model()).isEqualTo("speech-01"))
                    .verifyComplete();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  辅助构造
    // ──────────────────────────────────────────────────────────────────────

    private VoiceTtsRequest ttsRequest(String text) {
        return new VoiceTtsRequest("room-minimax-001", null, text, null, null);
    }

    private multimodalAgentProperties buildProperties(String endpoint, String groupId) {
        multimodalAgentProperties props = new multimodalAgentProperties();
        multimodalAgentProperties.RealtimeTts tts = props.getVoice().getTts();
        tts.setProvider("minimax");
        tts.setModel("speech-01");
        tts.setEndpoint(endpoint);
        tts.setApiKey("real-api-key-xyz");
        tts.setGroupId(groupId);
        tts.setVoice("Bowen_ai_dialog");
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
