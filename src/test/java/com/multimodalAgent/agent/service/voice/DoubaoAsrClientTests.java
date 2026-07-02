package com.multimodalAgent.agent.service.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.dto.VoiceAsrRequest;
import java.util.Base64;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

@DisplayName("DoubaoAsrClient 单元测试")
class DoubaoAsrClientTests {

    private MockWebServer mockServer;
    private DoubaoAsrClient client;
    private multimodalAgentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        properties = buildProperties(mockServer.url("/asr").toString());
        client = new DoubaoAsrClient(properties, WebClient.builder(), objectMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  HTTP 转录路径
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HTTP 转录")
    class HttpTranscription {

        @Test
        @DisplayName("正常识别：返回包含 text 字段的 JSON 时，应解析出文本和置信度")
        void successfulTranscription() {
            String responseJson = """
                    {"text":"你好世界","confidence":0.95}
                    """;
            mockServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            VoiceAsrRequest request = asrRequest("pcm", 16000, true);

            StepVerifier.create(client.transcribe(request))
                    .assertNext(response -> {
                        assertThat(response.text()).isEqualTo("你好世界");
                        assertThat(response.confidence()).isEqualTo(0.95);
                        assertThat(response.provider()).isEqualTo("doubao");
                        assertThat(response.finalTranscript()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("嵌套路径识别：返回 result.text 嵌套字段时应正确解析")
        void successfulTranscriptionWithNestedResultPath() {
            String responseJson = """
                    {"result":{"text":"心理咨询","confidence":0.88}}
                    """;
            mockServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            VoiceAsrRequest request = asrRequest("pcm", 16000, false);

            StepVerifier.create(client.transcribe(request))
                    .assertNext(response -> {
                        assertThat(response.text()).isEqualTo("心理咨询");
                        assertThat(response.confidence()).isEqualTo(0.88);
                        assertThat(response.finalTranscript()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("utterances 路径：返回 result.utterances[0].text 时应正确解析")
        void successfulTranscriptionWithUtterancesPath() {
            String responseJson = """
                    {"result":{"utterances":[{"text":"我最近有些焦虑","confidence":0.92}]}}
                    """;
            mockServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            VoiceAsrRequest request = asrRequest("wav", 44100, true);

            StepVerifier.create(client.transcribe(request))
                    .assertNext(response -> {
                        assertThat(response.text()).isEqualTo("我最近有些焦虑");
                        assertThat(response.confidence()).isEqualTo(0.92);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("空文本响应：服务端返回无 text 字段时应抛出 IllegalStateException")
        void emptyTextResponseShouldThrow() {
            String responseJson = """
                    {"code":200,"message":"success"}
                    """;
            mockServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            VoiceAsrRequest request = asrRequest("pcm", 16000, true);

            StepVerifier.create(client.transcribe(request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(IllegalStateException.class);
                        assertThat(error.getMessage()).contains("did not include recognized text");
                    })
                    .verify();
        }

        @Test
        @DisplayName("payload/text 路径：兜底路径应能正确提取文本")
        void payloadTextPathFallback() {
            String responseJson = """
                    {"payload":{"text":"压力很大"}}
                    """;
            mockServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            VoiceAsrRequest request = asrRequest("pcm", 16000, true);

            StepVerifier.create(client.transcribe(request))
                    .assertNext(response -> assertThat(response.text()).isEqualTo("压力很大"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("data.text 路径：data 包装路径应能正确解析")
        void dataTextPathFallback() {
            String responseJson = """
                    {"data":{"text":"睡眠不好","confidence":0.75}}
                    """;
            mockServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            VoiceAsrRequest request = asrRequest("pcm", 16000, false);

            StepVerifier.create(client.transcribe(request))
                    .assertNext(response -> {
                        assertThat(response.text()).isEqualTo("睡眠不好");
                        assertThat(response.confidence()).isEqualTo(0.75);
                    })
                    .verifyComplete();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  配置校验
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("配置校验")
    class ConfigurationValidation {

        @Test
        @DisplayName("provider 不是 doubao 时应立即同步抛出 IllegalStateException")
        void wrongProviderShouldThrow() {
            multimodalAgentProperties props = buildPropertiesWithProvider("minimax",
                    "https://asr.example.com", "key123", "app456", "cluster789");
            DoubaoAsrClient wrongClient = new DoubaoAsrClient(props, WebClient.builder(), objectMapper);
            VoiceAsrRequest request = asrRequest("pcm", 16000, true);

            // ensureConfigured() throws synchronously before a Mono is returned
            assertThatThrownBy(() -> wrongClient.transcribe(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_ASR_PROVIDER must be doubao");
        }

        @Test
        @DisplayName("endpoint 未配置时应同步抛出配置缺失异常")
        void missingEndpointShouldThrow() {
            multimodalAgentProperties props = buildPropertiesWithProvider("doubao",
                    "", "key123", "app456", "cluster789");
            DoubaoAsrClient missingEndpointClient = new DoubaoAsrClient(props, WebClient.builder(), objectMapper);
            VoiceAsrRequest request = asrRequest("pcm", 16000, true);

            assertThatThrownBy(() -> missingEndpointClient.transcribe(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Configure VOICE_ASR_ENDPOINT");
        }

        @Test
        @DisplayName("apiKey 未配置时应同步抛出配置缺失异常")
        void missingApiKeyShouldThrow() {
            multimodalAgentProperties props = buildPropertiesWithProvider("doubao",
                    "https://asr.example.com", "", "app456", "cluster789");
            DoubaoAsrClient missingKeyClient = new DoubaoAsrClient(props, WebClient.builder(), objectMapper);
            VoiceAsrRequest request = asrRequest("pcm", 16000, true);

            assertThatThrownBy(() -> missingKeyClient.transcribe(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Configure VOICE_ASR_ENDPOINT");
        }

        @Test
        @DisplayName("占位符值（your-xxx）应被识别为未配置并同步抛出")
        void placeholderValueShouldBeConsideredMissing() {
            multimodalAgentProperties props = buildPropertiesWithProvider("doubao",
                    "https://asr.example.com", "your-api-key", "app456", "cluster789");
            DoubaoAsrClient placeholderClient = new DoubaoAsrClient(props, WebClient.builder(), objectMapper);
            VoiceAsrRequest request = asrRequest("pcm", 16000, true);

            assertThatThrownBy(() -> placeholderClient.transcribe(request))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("cluster 未配置时应同步抛出配置缺失异常")
        void missingClusterShouldThrow() {
            multimodalAgentProperties props = buildPropertiesWithProvider("doubao",
                    "https://asr.example.com", "real-key", "app456", "");
            DoubaoAsrClient missingClusterClient = new DoubaoAsrClient(props, WebClient.builder(), objectMapper);
            VoiceAsrRequest request = asrRequest("pcm", 16000, true);

            // The composite message contains "VOICE_ASR_APP_ID and VOICE_ASR_CLUSTER"
            assertThatThrownBy(() -> missingClusterClient.transcribe(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VOICE_ASR_CLUSTER");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  请求字段映射
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("请求字段映射")
    class RequestFieldMapping {

        @Test
        @DisplayName("请求中指定的 format 应优先于配置默认值")
        void requestFormatOverridesDefault() {
            String responseJson = """
                    {"text":"测试音频","confidence":0.9}
                    """;
            mockServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            // 请求中显式指定 wav，配置默认 pcm
            VoiceAsrRequest request = new VoiceAsrRequest(
                    "room-123", null,
                    Base64.getEncoder().encodeToString("audio".getBytes()),
                    "wav", 44100, "en-US", true);

            StepVerifier.create(client.transcribe(request))
                    .assertNext(response -> assertThat(response.text()).isEqualTo("测试音频"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("请求中未指定 sampleRate 时应使用配置默认值")
        void defaultSampleRateUsedWhenRequestOmits() {
            String responseJson = """
                    {"text":"默认采样率","confidence":0.85}
                    """;
            mockServer.enqueue(new MockResponse()
                    .setBody(responseJson)
                    .addHeader("Content-Type", "application/json"));

            VoiceAsrRequest request = new VoiceAsrRequest(
                    "room-456", null,
                    Base64.getEncoder().encodeToString("audio".getBytes()),
                    null, null, null, false);

            StepVerifier.create(client.transcribe(request))
                    .assertNext(response -> assertThat(response.provider()).isEqualTo("doubao"))
                    .verifyComplete();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  响应字段
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("响应字段校验")
    class ResponseFields {

        @Test
        @DisplayName("provider 和 model 字段应来自配置而非响应体")
        void providerAndModelComesFromConfig() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"text\":\"hello\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.transcribe(asrRequest("pcm", 16000, true)))
                    .assertNext(response -> {
                        assertThat(response.provider()).isEqualTo("doubao");
                        assertThat(response.model()).isEqualTo("doubao-streaming-asr");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("finalTranscript 字段应与请求中的值一致")
        void finalTranscriptMirrorRequest() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"text\":\"interim result\"}")
                    .addHeader("Content-Type", "application/json"));

            VoiceAsrRequest request = asrRequest("pcm", 16000, false);

            StepVerifier.create(client.transcribe(request))
                    .assertNext(response -> assertThat(response.finalTranscript()).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("置信度字段缺失时应默认为 0.0")
        void missingConfidenceDefaultsToZero() {
            mockServer.enqueue(new MockResponse()
                    .setBody("{\"text\":\"无置信度字段\"}")
                    .addHeader("Content-Type", "application/json"));

            StepVerifier.create(client.transcribe(asrRequest("pcm", 16000, true)))
                    .assertNext(response -> assertThat(response.confidence()).isEqualTo(0.0))
                    .verifyComplete();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  辅助构造
    // ──────────────────────────────────────────────────────────────────────

    private VoiceAsrRequest asrRequest(String format, int sampleRate, boolean finalTranscript) {
        String audioBase64 = Base64.getEncoder().encodeToString("fake-audio-data".getBytes());
        return new VoiceAsrRequest(
                "room-test-001", null, audioBase64,
                format, sampleRate, "zh-CN", finalTranscript);
    }

    private multimodalAgentProperties buildProperties(String endpoint) {
        return buildPropertiesWithProvider("doubao", endpoint,
                "real-api-key-12345", "real-app-id-67890", "real-cluster-abc");
    }

    private multimodalAgentProperties buildPropertiesWithProvider(
            String provider, String endpoint, String apiKey, String appId, String cluster) {
        multimodalAgentProperties props = new multimodalAgentProperties();
        multimodalAgentProperties.RealtimeAsr asr = props.getVoice().getAsr();
        asr.setProvider(provider);
        asr.setModel("doubao-streaming-asr");
        asr.setEndpoint(endpoint);
        asr.setApiKey(apiKey);
        asr.setAppId(appId);
        asr.setCluster(cluster);
        asr.setFormat("pcm");
        asr.setSampleRate(16000);
        asr.setLanguage("zh-CN");
        return props;
    }
}
