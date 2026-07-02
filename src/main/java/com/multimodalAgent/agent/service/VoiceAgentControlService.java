package com.multimodalAgent.agent.service;

import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.dto.ChatRequest;
import com.multimodalAgent.agent.dto.ChatStreamEvent;
import com.multimodalAgent.agent.dto.VoiceAgentEventRequest;
import com.multimodalAgent.agent.dto.VoiceAgentTranscriptRequest;
import com.multimodalAgent.agent.dto.VoiceAsrRequest;
import com.multimodalAgent.agent.dto.VoiceAsrResponse;
import com.multimodalAgent.agent.dto.VoiceControlCommandRequest;
import com.multimodalAgent.agent.dto.VoiceControlEvent;
import com.multimodalAgent.agent.dto.VoiceSessionSummaryResponse;
import com.multimodalAgent.agent.dto.VoiceTtsRequest;
import com.multimodalAgent.agent.dto.VoiceTtsResponse;
import com.multimodalAgent.agent.security.CurrentUser;
import com.multimodalAgent.agent.service.voice.DoubaoAsrClient;
import com.multimodalAgent.agent.service.voice.DoubaoTtsClient;
import com.multimodalAgent.agent.service.voice.MiniMaxTtsClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class VoiceAgentControlService {

    private final VoiceSessionService voiceSessionService;
    private final ChatService chatService;
    private final VoiceSupportPolicyService voiceSupportPolicyService;
    private final multimodalAgentProperties properties;
    private final DoubaoAsrClient doubaoAsrClient;
    private final DoubaoTtsClient doubaoTtsClient;
    private final MiniMaxTtsClient miniMaxTtsClient;
    private final Map<String, Sinks.Many<VoiceControlEvent>> roomSinks = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> roomSequences = new ConcurrentHashMap<>();
    private final Map<String, VoiceLatencyState> roomLatency = new ConcurrentHashMap<>();

    public VoiceAgentControlService(
            VoiceSessionService voiceSessionService,
            ChatService chatService,
            VoiceSupportPolicyService voiceSupportPolicyService,
            multimodalAgentProperties properties,
            DoubaoAsrClient doubaoAsrClient,
            DoubaoTtsClient doubaoTtsClient,
            MiniMaxTtsClient miniMaxTtsClient
    ) {
        this.voiceSessionService = voiceSessionService;
        this.chatService = chatService;
        this.voiceSupportPolicyService = voiceSupportPolicyService;
        this.properties = properties;
        this.doubaoAsrClient = doubaoAsrClient;
        this.doubaoTtsClient = doubaoTtsClient;
        this.miniMaxTtsClient = miniMaxTtsClient;
    }

    public Flux<ServerSentEvent<VoiceControlEvent>> streamForStudent(String roomName, CurrentUser user) {
        VoiceSessionService.VoiceRuntimeSession session = voiceSessionService.requireOwnedSession(roomName, user);
        publish(VoiceControlEvent.status(roomName, session.chatSessionId(), "control-channel-ready", next(roomName)));
        return eventStream(roomName);
    }

    public Flux<ServerSentEvent<VoiceControlEvent>> streamForAgent(String roomName, CurrentUser user) {
        voiceSessionService.requireOwnedSession(roomName, user);
        publish(VoiceControlEvent.status(roomName, voiceSessionService.findSession(roomName)
                .map(VoiceSessionService.VoiceRuntimeSession::chatSessionId)
                .orElse(null), "agent-command-stream-ready", next(roomName)));
        return eventStream(roomName);
    }

    public VoiceControlEvent command(String roomName, CurrentUser user, String defaultCommand, VoiceControlCommandRequest request) {
        VoiceSessionService.VoiceRuntimeSession session = voiceSessionService.requireOwnedSession(roomName, user);
        String command = request != null && hasText(request.command()) ? request.command() : defaultCommand;
        String reason = request == null ? null : request.reason();
        VoiceControlEvent event = VoiceControlEvent.command(roomName, session.chatSessionId(), command, reason, next(roomName));
        publish(event);
        if ("end_session".equals(command)) {
            VoiceSessionSummaryResponse summary = voiceSupportPolicyService.closeSummary(session);
            publish(new VoiceControlEvent(
                    "voice_session_summary",
                    roomName,
                    session.chatSessionId(),
                    null,
                    "summary",
                    summary.summary() + " " + summary.suggestedFollowUp(),
                    null,
                    null,
                    null,
                    next(roomName),
                    Instant.now()));
            voiceSessionService.close(roomName);
        }
        return event;
    }

    public VoiceControlEvent agentEvent(CurrentUser user, VoiceAgentEventRequest request) {
        VoiceSessionService.VoiceRuntimeSession session = voiceSessionService.requireOwnedSession(request.roomName(), user);
        String type = hasText(request.type()) ? request.type() : "agent_event";
        VoiceControlEvent event = new VoiceControlEvent(
                type,
                request.roomName(),
                coalesce(request.sessionId(), session.chatSessionId()),
                null,
                request.phase(),
                request.text(),
                null,
                null,
                null,
                next(request.roomName()),
                Instant.now());
        publish(event);
        return event;
    }

    public Flux<ServerSentEvent<VoiceControlEvent>> transcript(CurrentUser user, VoiceAgentTranscriptRequest request) {
        VoiceSessionService.VoiceRuntimeSession session = voiceSessionService.requireOwnedSession(request.roomName(), user);
        String sessionId = coalesce(request.sessionId(), session.chatSessionId());
        VoiceControlEvent transcript = VoiceControlEvent.transcript(
                request.roomName(),
                sessionId,
                request.text(),
                request.finalTranscript(),
                next(request.roomName()));
        publish(transcript);

        if (!request.finalTranscript()) {
            return Flux.just(toSse("transcript", transcript));
        }

        VoiceLatencyState latency = latency(request.roomName());
        latency.asrDoneAt = System.currentTimeMillis();
        latency.llmStartAt = latency.asrDoneAt;
        publishLatency(request.roomName(), sessionId);
        publish(new VoiceControlEvent(
                "voice_policy",
                request.roomName(),
                sessionId,
                null,
                session.supportMode(),
                "tone=" + session.ttsTone() + ", pace=" + session.speakingPace() + ", advice=" + session.adviceDensity(),
                null,
                null,
                null,
                next(request.roomName()),
                Instant.now()));
        if (session.crisis()) {
            return crisisSafetyFlow(request.roomName(), session, sessionId);
        }
        ChatRequest chatRequest = new ChatRequest(sessionId, request.text());
        return chatService.streamChat(session.userId(), chatRequest, session.knowledgeScope(), session.sessionInstruction())
                .map(ServerSentEvent::data)
                .filter(data -> data != null)
                .map(data -> toVoiceEvent(request.roomName(), data))
                .doOnNext(this::publish)
                .map(event -> toSse(event.type(), event));
    }

    public Mono<VoiceAsrResponse> doubaoAsr(CurrentUser user, VoiceAsrRequest request) {
        VoiceSessionService.VoiceRuntimeSession session = voiceSessionService.requireOwnedSession(request.roomName(), user);
        latency(request.roomName()).asrStartAt = System.currentTimeMillis();
        publish(VoiceControlEvent.phase(request.roomName(), session.chatSessionId(), "asr", next(request.roomName())));
        return doubaoAsrClient.transcribe(request)
                .doOnNext(response -> {
                    VoiceLatencyState latency = latency(request.roomName());
                    latency.asrDoneAt = System.currentTimeMillis();
                    publish(VoiceControlEvent.transcript(
                            request.roomName(),
                            coalesce(request.sessionId(), session.chatSessionId()),
                            response.text(),
                            response.finalTranscript(),
                            next(request.roomName())));
                    publishLatency(request.roomName(), coalesce(request.sessionId(), session.chatSessionId()));
                });
    }

    public Mono<VoiceTtsResponse> miniMaxTts(CurrentUser user, VoiceTtsRequest request) {
        return tts(user, request);
    }

    public Mono<VoiceTtsResponse> doubaoTts(CurrentUser user, VoiceTtsRequest request) {
        return tts(user, request);
    }

    public Mono<VoiceTtsResponse> tts(CurrentUser user, VoiceTtsRequest request) {
        VoiceSessionService.VoiceRuntimeSession session = voiceSessionService.requireOwnedSession(request.roomName(), user);
        latency(request.roomName()).ttsStartAt = System.currentTimeMillis();
        publish(VoiceControlEvent.phase(request.roomName(), session.chatSessionId(), "tts", next(request.roomName())));
        VoiceTtsRequest effectiveRequest = new VoiceTtsRequest(
                request.roomName(),
                request.sessionId(),
                request.text(),
                hasText(request.voice()) ? request.voice() : session.ttsVoice(),
                request.format());
        return ttsClient(effectiveRequest)
                .doOnNext(response -> {
                    VoiceLatencyState latency = latency(request.roomName());
                    latency.ttsDoneAt = System.currentTimeMillis();
                    publish(new VoiceControlEvent(
                            "tts_ready",
                            effectiveRequest.roomName(),
                            coalesce(effectiveRequest.sessionId(), session.chatSessionId()),
                            null,
                            "tts",
                            "audio-ready:" + response.byteLength(),
                            null,
                            null,
                            null,
                            next(effectiveRequest.roomName()),
                            Instant.now()));
                    publishLatency(effectiveRequest.roomName(), coalesce(effectiveRequest.sessionId(), session.chatSessionId()));
                });
    }

    private Mono<VoiceTtsResponse> ttsClient(VoiceTtsRequest request) {
        String provider = properties.getVoice().getTts().getProvider();
        if ("doubao".equalsIgnoreCase(provider)) {
            return doubaoTtsClient.synthesize(request);
        }
        if ("minimax".equalsIgnoreCase(provider)) {
            return miniMaxTtsClient.synthesize(request);
        }
        return Mono.error(new IllegalStateException("Unsupported VOICE_TTS_PROVIDER: " + provider));
    }

    private Flux<ServerSentEvent<VoiceControlEvent>> crisisSafetyFlow(
            String roomName,
            VoiceSessionService.VoiceRuntimeSession session,
            String sessionId
    ) {
        VoiceLatencyState latency = latency(roomName);
        latency.llmFirstTokenAt = System.currentTimeMillis();
        latency.llmDoneAt = latency.llmFirstTokenAt;
        String message = coalesce(session.safetyMessage(), "我会先暂停普通陪聊。现在请优先确认自身安全，并尽快联系可信任的人或学校值班老师。");
        VoiceControlEvent phase = VoiceControlEvent.phase(roomName, sessionId, "crisis_safety", next(roomName));
        VoiceControlEvent token = VoiceControlEvent.assistantToken(roomName, sessionId, message, next(roomName));
        VoiceControlEvent done = VoiceControlEvent.done(roomName, sessionId, "crisis-safety-flow-complete", next(roomName));
        publish(phase);
        publish(token);
        publish(done);
        publishLatency(roomName, sessionId);
        return Flux.just(phase, token, done).map(event -> toSse(event.type(), event));
    }

    private Flux<ServerSentEvent<VoiceControlEvent>> eventStream(String roomName) {
        return sink(roomName).asFlux()
                .mergeWith(Flux.interval(Duration.ofSeconds(20))
                        .map(tick -> VoiceControlEvent.status(roomName, null, "heartbeat", next(roomName))))
                .map(event -> toSse(event.type(), event));
    }

    private VoiceControlEvent toVoiceEvent(String roomName, ChatStreamEvent event) {
        String sessionId = event.sessionId();
        if ("meta".equals(event.type())) {
            voiceSessionService.attachChatSession(roomName, sessionId);
            return VoiceControlEvent.status(roomName, sessionId, "chat-session-ready", next(roomName));
        }
        if ("phase".equals(event.type())) {
            return VoiceControlEvent.phase(roomName, sessionId, event.phase(), next(roomName));
        }
        if ("token".equals(event.type())) {
            VoiceLatencyState latency = latency(roomName);
            if (latency.llmFirstTokenAt == null) {
                latency.llmFirstTokenAt = System.currentTimeMillis();
                publishLatency(roomName, sessionId);
            }
            return VoiceControlEvent.assistantToken(roomName, sessionId, event.content(), next(roomName));
        }
        if ("done".equals(event.type())) {
            latency(roomName).llmDoneAt = System.currentTimeMillis();
            publishLatency(roomName, sessionId);
            return VoiceControlEvent.done(roomName, sessionId, "assistant-response-complete", next(roomName));
        }
        if ("error".equals(event.type())) {
            return VoiceControlEvent.error(roomName, sessionId, event.content(), next(roomName));
        }
        return VoiceControlEvent.status(roomName, sessionId, event.type(), next(roomName));
    }

    private Sinks.Many<VoiceControlEvent> sink(String roomName) {
        return roomSinks.computeIfAbsent(roomName, key -> Sinks.many().replay().limit(64));
    }

    private void publish(VoiceControlEvent event) {
        sink(event.roomName()).tryEmitNext(event);
    }

    private VoiceLatencyState latency(String roomName) {
        return roomLatency.computeIfAbsent(roomName, key -> new VoiceLatencyState(System.currentTimeMillis()));
    }

    private void publishLatency(String roomName, String sessionId) {
        VoiceLatencyState state = latency(roomName);
        publish(VoiceControlEvent.latency(roomName, sessionId, state.snapshot(), next(roomName)));
    }

    private long next(String roomName) {
        return roomSequences.computeIfAbsent(roomName, key -> new AtomicLong()).incrementAndGet();
    }

    private ServerSentEvent<VoiceControlEvent> toSse(String eventName, VoiceControlEvent event) {
        return ServerSentEvent.builder(event).event(eventName == null ? "voice" : eventName).build();
    }

    private String coalesce(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static class VoiceLatencyState {
        private final long startedAt;
        private Long asrStartAt;
        private Long asrDoneAt;
        private Long llmStartAt;
        private Long llmFirstTokenAt;
        private Long llmDoneAt;
        private Long ttsStartAt;
        private Long ttsDoneAt;

        private VoiceLatencyState(long startedAt) {
            this.startedAt = startedAt;
        }

        private Map<String, Long> snapshot() {
            Map<String, Long> metrics = new LinkedHashMap<>();
            putDuration(metrics, "sessionMs", startedAt, System.currentTimeMillis());
            putDuration(metrics, "asrMs", asrStartAt, asrDoneAt);
            putDuration(metrics, "llmFirstTokenMs", llmStartAt, llmFirstTokenAt);
            putDuration(metrics, "llmTotalMs", llmStartAt, llmDoneAt);
            putDuration(metrics, "ttsMs", ttsStartAt, ttsDoneAt);
            if (asrDoneAt != null && ttsDoneAt != null) {
                metrics.put("turnTotalMs", ttsDoneAt - startedAt);
            }
            return metrics;
        }

        private void putDuration(Map<String, Long> metrics, String key, Long start, Long end) {
            if (start != null && end != null && end >= start) {
                metrics.put(key, end - start);
            }
        }
    }
}
