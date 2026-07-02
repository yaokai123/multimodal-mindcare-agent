package com.multimodalAgent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.domain.UserAccount;
import com.multimodalAgent.agent.dto.VoiceSessionRequest;
import com.multimodalAgent.agent.dto.VoiceSessionResponse;
import com.multimodalAgent.agent.security.CurrentUser;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VoiceSessionService 单元测试")
class VoiceSessionServiceTests {

    private VoiceSessionService service;
    private multimodalAgentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new multimodalAgentProperties();
        service = new VoiceSessionService(properties, objectMapper);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  status() — 未配置时的状态
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("status() — 服务状态查询")
    class StatusQuery {

        @Test
        @DisplayName("未配置任何语音参数时，enabled 和 configured 应均为 false")
        void defaultStatusIsNotConfigured() {
            VoiceSessionResponse status = service.status();
            assertThat(status.enabled()).isFalse();
            assertThat(status.configured()).isFalse();
            assertThat(status.status()).contains("waiting");
        }

        @Test
        @DisplayName("未配置时 pipeline 列表不应为空")
        void pipelineListIsNotEmpty() {
            VoiceSessionResponse status = service.status();
            assertThat(status.pipeline()).isNotEmpty();
            assertThat(status.pipeline()).contains("Doubao streaming ASR");
        }

        @Test
        @DisplayName("未配置时 expiresAt 应为 null")
        void expiresAtIsNullWhenNotConfigured() {
            VoiceSessionResponse status = service.status();
            assertThat(status.expiresAt()).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  create() — 会话创建
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — 创建语音会话")
    class CreateSession {

        @Test
        @DisplayName("创建会话后，roomName 应包含用户 ID")
        void roomNameContainsUserId() {
            CurrentUser user = studentUser(42L, "student1");
            VoiceSessionRequest request = new VoiceSessionRequest(null, null, null);

            VoiceSessionResponse response = service.create(user, request);

            assertThat(response.roomName()).contains("42");
        }

        @Test
        @DisplayName("创建会话后，participantName 应包含用户 ID")
        void participantNameContainsUserId() {
            CurrentUser user = studentUser(99L, "student99");
            VoiceSessionRequest request = new VoiceSessionRequest(null, null, null);

            VoiceSessionResponse response = service.create(user, request);

            assertThat(response.participantName()).isEqualTo("student-99");
        }

        @Test
        @DisplayName("指定 knowledgeScope 时，roomName 应包含该 scope")
        void roomNameContainsKnowledgeScope() {
            CurrentUser user = studentUser(1L, "student1");
            VoiceSessionRequest request = new VoiceSessionRequest(null, "stress-relief", null);

            VoiceSessionResponse response = service.create(user, request);

            assertThat(response.roomName()).contains("stress-relief");
        }

        @Test
        @DisplayName("knowledgeScope 含特殊字符时，roomName 中应被替换为连字符")
        void specialCharsInScopeAreReplaced() {
            CurrentUser user = studentUser(1L, "student1");
            VoiceSessionRequest request = new VoiceSessionRequest(null, "scope with spaces!", null);

            VoiceSessionResponse response = service.create(user, request);

            assertThat(response.roomName()).doesNotContain(" ").doesNotContain("!");
        }

        @Test
        @DisplayName("未指定 knowledgeScope 时，roomName 应包含默认 'support' 字样")
        void defaultScopeIsSupport() {
            CurrentUser user = studentUser(1L, "student1");
            VoiceSessionRequest request = new VoiceSessionRequest(null, null, null);

            VoiceSessionResponse response = service.create(user, request);

            assertThat(response.roomName()).contains("support");
        }

        @Test
        @DisplayName("多次创建会话时，每次 roomName 应不同（UUID 后缀保证唯一性）")
        void roomNamesAreUniqueAcrossCreations() {
            CurrentUser user = studentUser(1L, "student1");
            VoiceSessionRequest request = new VoiceSessionRequest(null, null, null);

            VoiceSessionResponse r1 = service.create(user, request);
            VoiceSessionResponse r2 = service.create(user, request);

            assertThat(r1.roomName()).isNotEqualTo(r2.roomName());
        }

        @Test
        @DisplayName("创建会话后应能通过 findSession 找到该会话")
        void sessionIsStoredAfterCreation() {
            CurrentUser user = studentUser(7L, "student7");
            VoiceSessionResponse response = service.create(user, new VoiceSessionRequest(null, null, null));

            assertThat(service.findSession(response.roomName())).isPresent();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  requireOwnedSession() — 会话归属校验
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("requireOwnedSession() — 会话归属校验")
    class RequireOwnedSession {

        @Test
        @DisplayName("会话不存在时应抛出 IllegalArgumentException")
        void nonExistentSessionThrows() {
            CurrentUser user = studentUser(1L, "student1");

            assertThatThrownBy(() -> service.requireOwnedSession("non-existent-room", user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("会话属于其他用户时应抛出 IllegalArgumentException")
        void sessionOwnedByOtherUserThrows() {
            CurrentUser owner = studentUser(10L, "owner");
            CurrentUser other = studentUser(20L, "other");

            VoiceSessionResponse response = service.create(owner, new VoiceSessionRequest(null, null, null));

            assertThatThrownBy(() -> service.requireOwnedSession(response.roomName(), other))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong");
        }

        @Test
        @DisplayName("会话属于当前用户时应正常返回会话对象")
        void ownedSessionIsReturned() {
            CurrentUser user = studentUser(5L, "student5");
            VoiceSessionResponse response = service.create(user, new VoiceSessionRequest(null, null, null));

            VoiceSessionService.VoiceRuntimeSession session =
                    service.requireOwnedSession(response.roomName(), user);

            assertThat(session).isNotNull();
            assertThat(session.roomName()).isEqualTo(response.roomName());
            assertThat(session.userId()).isEqualTo(5L);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  attachChatSession() — 聊天 session 绑定
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("attachChatSession() — 聊天会话绑定")
    class AttachChatSession {

        @Test
        @DisplayName("绑定聊天 sessionId 后，findSession 返回的会话应包含该 sessionId")
        void chatSessionIdIsAttached() {
            CurrentUser user = studentUser(3L, "student3");
            VoiceSessionResponse response = service.create(user, new VoiceSessionRequest(null, null, null));

            service.attachChatSession(response.roomName(), "chat-session-abc");

            VoiceSessionService.VoiceRuntimeSession session =
                    service.findSession(response.roomName()).orElseThrow();
            assertThat(session.chatSessionId()).isEqualTo("chat-session-abc");
        }

        @Test
        @DisplayName("房间名为空时 attachChatSession 不应抛出异常")
        void emptyRoomNameIsIgnored() {
            service.attachChatSession("", "chat-session-xyz"); // 不应抛出
        }

        @Test
        @DisplayName("chatSessionId 为空时 attachChatSession 不应更新会话")
        void emptyChatSessionIdIsIgnored() {
            CurrentUser user = studentUser(4L, "student4");
            VoiceSessionResponse response = service.create(user, new VoiceSessionRequest("original-session", null, null));

            service.attachChatSession(response.roomName(), "");

            // sessionId 未被覆盖（保持原始值 original-session）
            VoiceSessionService.VoiceRuntimeSession session =
                    service.findSession(response.roomName()).orElseThrow();
            assertThat(session.chatSessionId()).isEqualTo("original-session");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  close() — 会话关闭
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("close() — 关闭会话")
    class CloseSession {

        @Test
        @DisplayName("关闭会话后，findSession 应返回 empty")
        void closedSessionIsNotFound() {
            CurrentUser user = studentUser(8L, "student8");
            VoiceSessionResponse response = service.create(user, new VoiceSessionRequest(null, null, null));

            service.close(response.roomName());

            assertThat(service.findSession(response.roomName())).isEmpty();
        }

        @Test
        @DisplayName("关闭不存在的房间名不应抛出异常")
        void closeNonExistentRoomIsNoOp() {
            service.close("non-existent-room"); // 不应抛出
        }

        @Test
        @DisplayName("关闭空房间名不应抛出异常")
        void closeEmptyRoomNameIsNoOp() {
            service.close(""); // 不应抛出
            service.close(null); // 不应抛出
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  完整配置时的响应
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("完整配置 — 语音服务就绪")
    class FullyConfigured {

        @Test
        @DisplayName("完整配置时 status() 应返回 configured=true 且 enabled=true")
        void fullyConfiguredStatusIsReady() {
            multimodalAgentProperties props = fullyConfiguredProperties();
            VoiceSessionService fullyConfiguredService = new VoiceSessionService(props, objectMapper);

            VoiceSessionResponse status = fullyConfiguredService.status();

            assertThat(status.enabled()).isTrue();
            assertThat(status.configured()).isTrue();
            assertThat(status.status()).contains("Ready");
        }

        @Test
        @DisplayName("完整配置时 create() 应生成 JWT token")
        void fullyConfiguredCreateGeneratesToken() {
            multimodalAgentProperties props = fullyConfiguredProperties();
            VoiceSessionService fullyConfiguredService = new VoiceSessionService(props, objectMapper);
            CurrentUser user = studentUser(1L, "student1");

            VoiceSessionResponse response = fullyConfiguredService.create(user,
                    new VoiceSessionRequest(null, null, null));

            // LiveKit JWT 格式：header.payload.signature（三段 Base64Url 以 . 分隔）
            assertThat(response.livekitToken()).isNotNull();
            String[] parts = response.livekitToken().split("\\.");
            assertThat(parts).hasSize(3);
        }

        @Test
        @DisplayName("完整配置时 create() 返回的 livekitUrl 应等于配置值")
        void livekitUrlEqualsConfigValue() {
            multimodalAgentProperties props = fullyConfiguredProperties();
            VoiceSessionService fullyConfiguredService = new VoiceSessionService(props, objectMapper);
            CurrentUser user = studentUser(1L, "student1");

            VoiceSessionResponse response = fullyConfiguredService.create(user,
                    new VoiceSessionRequest(null, null, null));

            assertThat(response.livekitUrl()).isEqualTo("wss://livekit.example.com");
        }

        @Test
        @DisplayName("完整配置时，status 响应的 asrProvider 应等于配置值")
        void asrProviderIsReflectedInStatus() {
            multimodalAgentProperties props = fullyConfiguredProperties();
            VoiceSessionService fullyConfiguredService = new VoiceSessionService(props, objectMapper);

            VoiceSessionResponse status = fullyConfiguredService.status();

            assertThat(status.asrProvider()).isEqualTo("doubao");
        }

        @Test
        @DisplayName("完整配置时，status 响应的 ttsProvider 应等于配置值")
        void ttsProviderIsReflectedInStatus() {
            multimodalAgentProperties props = fullyConfiguredProperties();
            VoiceSessionService fullyConfiguredService = new VoiceSessionService(props, objectMapper);

            VoiceSessionResponse status = fullyConfiguredService.status();

            assertThat(status.ttsProvider()).isEqualTo("doubao");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  缺少部分配置时的状态消息
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("部分配置 — 缺少项列举")
    class PartialConfiguration {

        @Test
        @DisplayName("未启用 VOICE_ENABLED 时，status 消息应提示缺少 VOICE_ENABLED")
        void disabledVoiceReflectedInStatus() {
            // 默认 voice.enabled = false
            VoiceSessionResponse status = service.status();
            assertThat(status.status()).contains("VOICE_ENABLED");
        }

        @Test
        @DisplayName("启用但 LiveKit 未配置时，status 消息应提示缺少 LiveKit")
        void missingLivekitReflectedInStatus() {
            multimodalAgentProperties props = new multimodalAgentProperties();
            props.getVoice().setEnabled(true);
            // LiveKit、ASR、TTS 均未配置
            VoiceSessionService partialService = new VoiceSessionService(props, objectMapper);

            VoiceSessionResponse status = partialService.status();
            assertThat(status.status()).contains("LiveKit");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────────────────────────────

    private CurrentUser studentUser(Long id, String username) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword("password");
        account.setDisplayName(username);
        account.setRoles(Set.of("ROLE_STUDENT"));
        // 通过反射设置 id（id 为 @GeneratedValue，无 setter）
        try {
            var field = UserAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new CurrentUser(account);
    }

    private multimodalAgentProperties fullyConfiguredProperties() {
        multimodalAgentProperties props = new multimodalAgentProperties();
        props.getVoice().setEnabled(true);

        multimodalAgentProperties.LiveKit lk = props.getVoice().getLivekit();
        lk.setUrl("wss://livekit.example.com");
        lk.setApiKey("lk-api-key-abcdefg");
        lk.setApiSecret("lk-api-secret-xyz-long-secret");
        lk.setTokenTtlMinutes(30);

        multimodalAgentProperties.RealtimeAsr asr = props.getVoice().getAsr();
        asr.setProvider("doubao");
        asr.setModel("doubao-streaming-asr");
        asr.setEndpoint("https://asr.doubao.example.com/v1");
        asr.setApiKey("asr-api-key-12345");
        asr.setAppId("asr-app-id-67890");
        asr.setCluster("asr-cluster-abc");

        multimodalAgentProperties.RealtimeTts tts = props.getVoice().getTts();
        tts.setProvider("doubao");
        tts.setModel("doubao-tts");
        tts.setEndpoint("https://tts.doubao.example.com/v1");
        tts.setApiKey("tts-api-key-xyz");
        tts.setResourceId("tts-resource-id-456");

        return props;
    }
}
