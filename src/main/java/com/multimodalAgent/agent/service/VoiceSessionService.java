package com.multimodalAgent.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.dto.VoiceSessionRequest;
import com.multimodalAgent.agent.dto.VoiceSessionResponse;
import com.multimodalAgent.agent.dto.VoiceSupportProfileResponse;
import com.multimodalAgent.agent.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VoiceSessionService {

    private static final List<String> PIPELINE = List.of(
            "LiveKit room",
            "Doubao streaming ASR",
            "ChatService Qwen stream",
            "Realtime TTS",
            "barge-in interrupt");

    private final multimodalAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final VoiceSupportPolicyService voiceSupportPolicyService;
    private final Map<String, VoiceRuntimeSession> runtimeSessions = new ConcurrentHashMap<>();

    public VoiceSessionService(multimodalAgentProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    @Autowired
    public VoiceSessionService(
            multimodalAgentProperties properties,
            ObjectMapper objectMapper,
            VoiceSupportPolicyService voiceSupportPolicyService
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.voiceSupportPolicyService = voiceSupportPolicyService;
    }

    public VoiceSessionResponse status() {
        return response(false, null, null, "ALL", profileForRisk(RiskLevel.LOW), "Voice service is waiting for configuration");
    }

    public VoiceSessionResponse create(CurrentUser user, VoiceSessionRequest request) {
        String knowledgeScope = normalizeScope(request == null ? null : request.knowledgeScope());
        String roomName = roomName(user, request);
        String participantName = "student-" + user.getId();
        VoiceSupportProfileResponse profile = profileForUser(user.getId());
        VoiceSessionResponse response = response(true, roomName, participantName, knowledgeScope, profile, "Voice session is ready");
        runtimeSessions.put(roomName, new VoiceRuntimeSession(
                roomName,
                user.getId(),
                participantName,
                request == null ? null : request.sessionId(),
                knowledgeScope,
                request == null ? null : request.supportGoal(),
                profile.riskLevel(),
                profile.supportMode(),
                profile.ttsTone(),
                profile.ttsVoice(),
                profile.speakingPace(),
                profile.adviceDensity(),
                profile.sessionInstruction(),
                profile.crisis(),
                profile.safetyMessage(),
                profile.suggestedNextAction(),
                Instant.now(),
                response.expiresAt()));
        return response;
    }

    public Optional<VoiceRuntimeSession> findSession(String roomName) {
        if (!hasText(roomName)) {
            return Optional.empty();
        }
        VoiceRuntimeSession session = runtimeSessions.get(roomName);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt() != null && session.expiresAt().isBefore(Instant.now())) {
            runtimeSessions.remove(roomName);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public VoiceRuntimeSession requireOwnedSession(String roomName, CurrentUser user) {
        VoiceRuntimeSession session = findSession(roomName)
                .orElseThrow(() -> new IllegalArgumentException("Voice session not found or expired."));
        if (!session.userId().equals(user.getId())) {
            throw new IllegalArgumentException("Voice session does not belong to current user.");
        }
        return session;
    }

    public void attachChatSession(String roomName, String chatSessionId) {
        if (!hasText(roomName) || !hasText(chatSessionId)) {
            return;
        }
        runtimeSessions.computeIfPresent(roomName, (key, session) -> new VoiceRuntimeSession(
                session.roomName(),
                session.userId(),
                session.participantName(),
                chatSessionId,
                session.knowledgeScope(),
                session.supportGoal(),
                session.initialRiskLevel(),
                session.supportMode(),
                session.ttsTone(),
                session.ttsVoice(),
                session.speakingPace(),
                session.adviceDensity(),
                session.sessionInstruction(),
                session.crisis(),
                session.safetyMessage(),
                session.suggestedNextAction(),
                session.createdAt(),
                session.expiresAt()));
    }

    public void close(String roomName) {
        if (hasText(roomName)) {
            runtimeSessions.remove(roomName);
        }
    }

    private VoiceSessionResponse response(
            boolean includeToken,
            String roomName,
            String participantName,
            String knowledgeScope,
            VoiceSupportProfileResponse profile,
            String fallbackStatus
    ) {
        multimodalAgentProperties.Voice voice = properties.getVoice();
        multimodalAgentProperties.LiveKit livekit = voice.getLivekit();
        multimodalAgentProperties.RealtimeAsr asr = voice.getAsr();
        multimodalAgentProperties.RealtimeTts tts = voice.getTts();
        boolean livekitConfigured = realText(livekit.getUrl()) && realText(livekit.getApiKey()) && realText(livekit.getApiSecret());
        boolean asrConfigured = realText(asr.getEndpoint()) && realText(asr.getApiKey()) && realText(asr.getAppId()) && realText(asr.getCluster());
        boolean ttsConfigured = ttsConfigured(tts);
        boolean configured = voice.isEnabled() && livekitConfigured && asrConfigured && ttsConfigured;
        Instant expiresAt = Instant.now().plusSeconds(Math.max(1, livekit.getTokenTtlMinutes()) * 60L);
        String token = configured && includeToken
                ? livekitToken(roomName, participantName, expiresAt, livekit.getApiKey(), livekit.getApiSecret())
                : null;
        String status = configured ? "Ready for realtime voice" : missingStatus(
                fallbackStatus,
                voice.isEnabled(),
                livekitConfigured,
                asrConfigured,
                ttsConfigured,
                tts.getProvider());
        return new VoiceSessionResponse(
                voice.isEnabled(),
                configured,
                status,
                roomName,
                participantName,
                livekit.getUrl(),
                token,
                voice.getAsr().getProvider(),
                voice.getAsr().getModel(),
                properties.getAi().getProvider(),
                modelName(),
                voice.getTts().getProvider(),
                voice.getTts().getModel(),
                voice.getTts().getVoice(),
                voice.isInterruptEnabled(),
                normalizeScope(knowledgeScope),
                profile.supportMode(),
                profile.ttsTone(),
                profile.ttsVoice(),
                profile.speakingPace(),
                profile.adviceDensity(),
                profile.crisis(),
                profile.sessionInstruction(),
                PIPELINE,
                configured ? expiresAt : null);
    }

    private String roomName(CurrentUser user, VoiceSessionRequest request) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String scope = request != null && hasText(request.knowledgeScope())
                ? request.knowledgeScope().replaceAll("[^a-zA-Z0-9_-]", "-")
                : "support";
        return "mindcare-" + user.getId() + "-" + scope + "-" + suffix;
    }

    private String normalizeScope(String scope) {
        if (!hasText(scope)) {
            return "ALL";
        }
        String normalized = scope.trim();
        if ("support".equalsIgnoreCase(normalized) || "student-support".equalsIgnoreCase(normalized)) {
            return "ALL";
        }
        normalized = normalized.replaceAll("[^a-zA-Z0-9_\\-.\\u4e00-\\u9fa5]+", "-");
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String modelName() {
        String provider = properties.getAi().getProvider();
        if ("openai".equalsIgnoreCase(provider)) {
            return properties.getAi().getOpenai().getModel();
        }
        if ("ollama".equalsIgnoreCase(provider)) {
            return properties.getAi().getOllama().getModel();
        }
        return "mock";
    }

    private VoiceSupportProfileResponse profileForUser(Long userId) {
        if (voiceSupportPolicyService != null) {
            return voiceSupportPolicyService.profileForUser(userId);
        }
        return profileForRisk(RiskLevel.LOW);
    }

    private VoiceSupportProfileResponse profileForRisk(RiskLevel riskLevel) {
        if (voiceSupportPolicyService != null) {
            return voiceSupportPolicyService.profileForRisk(riskLevel);
        }
        String voice = hasText(properties.getVoice().getSoothingVoice())
                ? properties.getVoice().getSoothingVoice()
                : properties.getVoice().getTts().getVoice();
        return new VoiceSupportProfileResponse(
                RiskLevel.LOW,
                "SOOTHING_COMPANION",
                "SOOTHING",
                voice,
                "gentle",
                "supportive",
                "可以用一句话记录此刻最明显的感受。",
                "Voice mode: low-risk psychological companionship.",
                false,
                null,
                "继续语音倾听或轻量放松。");
    }

    private String livekitToken(String roomName, String participantName, Instant expiresAt, String apiKey, String apiSecret) {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> videoGrant = new LinkedHashMap<>();
        videoGrant.put("room", roomName);
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", true);
        videoGrant.put("canSubscribe", true);
        Map<String, Object> claims = new LinkedHashMap<>();
        long now = Instant.now().getEpochSecond();
        claims.put("iss", apiKey);
        claims.put("sub", participantName);
        claims.put("nbf", now);
        claims.put("iat", now);
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("video", videoGrant);
        String headerPart = jsonPart(header);
        String claimPart = jsonPart(claims);
        String signingInput = headerPart + "." + claimPart;
        return signingInput + "." + hmacSha256(signingInput, apiSecret);
    }

    private String jsonPart(Object value) {
        try {
            return base64Url(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to create LiveKit token.", exception);
        }
    }

    private String hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign LiveKit token.", exception);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private boolean ttsConfigured(multimodalAgentProperties.RealtimeTts tts) {
        if ("doubao".equalsIgnoreCase(tts.getProvider())) {
            return realText(tts.getEndpoint())
                    && realText(tts.getApiKey())
                    && realText(ttsResourceId(tts));
        }
        if ("minimax".equalsIgnoreCase(tts.getProvider())) {
            return realText(tts.getEndpoint())
                    && realText(tts.getApiKey())
                    && realText(tts.getGroupId());
        }
        return false;
    }

    private String missingStatus(
            String fallbackStatus,
            boolean enabled,
            boolean livekitConfigured,
            boolean asrConfigured,
            boolean ttsConfigured,
            String ttsProvider
    ) {
        List<String> missing = new ArrayList<>();
        if (!enabled) {
            missing.add("VOICE_ENABLED");
        }
        if (!livekitConfigured) {
            missing.add("LiveKit");
        }
        if (!asrConfigured) {
            missing.add("Doubao ASR");
        }
        if (!ttsConfigured) {
            missing.add(ttsProviderLabel(ttsProvider));
        }
        return fallbackStatus + ": missing " + String.join(", ", missing);
    }

    private String ttsProviderLabel(String provider) {
        if ("doubao".equalsIgnoreCase(provider)) {
            return "Doubao TTS";
        }
        if ("minimax".equalsIgnoreCase(provider)) {
            return "MiniMax TTS";
        }
        return "supported TTS provider";
    }

    private String ttsResourceId(multimodalAgentProperties.RealtimeTts tts) {
        return hasText(tts.getResourceId()) ? tts.getResourceId() : tts.getGroupId();
    }

    public record VoiceRuntimeSession(
            String roomName,
            Long userId,
            String participantName,
            String chatSessionId,
            String knowledgeScope,
            String supportGoal,
            RiskLevel initialRiskLevel,
            String supportMode,
            String ttsTone,
            String ttsVoice,
            String speakingPace,
            String adviceDensity,
            String sessionInstruction,
            boolean crisis,
            String safetyMessage,
            String suggestedNextAction,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}
