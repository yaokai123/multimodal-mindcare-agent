package com.multimodalAgent.agent.controller;

import com.multimodalAgent.agent.dto.VoiceAgentEventRequest;
import com.multimodalAgent.agent.dto.VoiceAgentTranscriptRequest;
import com.multimodalAgent.agent.dto.VoiceAsrRequest;
import com.multimodalAgent.agent.dto.VoiceAsrResponse;
import com.multimodalAgent.agent.dto.VoiceControlCommandRequest;
import com.multimodalAgent.agent.dto.VoiceControlEvent;
import com.multimodalAgent.agent.dto.VoiceSessionRequest;
import com.multimodalAgent.agent.dto.VoiceSessionResponse;
import com.multimodalAgent.agent.dto.VoiceTtsRequest;
import com.multimodalAgent.agent.dto.VoiceTtsResponse;
import com.multimodalAgent.agent.security.CurrentUser;
import com.multimodalAgent.agent.service.VoiceAgentControlService;
import com.multimodalAgent.agent.service.VoiceSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceSessionService voiceSessionService;
    private final VoiceAgentControlService voiceAgentControlService;

    public VoiceController(VoiceSessionService voiceSessionService, VoiceAgentControlService voiceAgentControlService) {
        this.voiceSessionService = voiceSessionService;
        this.voiceAgentControlService = voiceAgentControlService;
    }

    @GetMapping("/status")
    public VoiceSessionResponse status() {
        return voiceSessionService.status();
    }

    @PostMapping("/sessions")
    public VoiceSessionResponse createSession(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody(required = false) VoiceSessionRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceSessionService.create(currentUser, request == null ? new VoiceSessionRequest(null, null, null) : request);
    }

    @GetMapping(value = "/sessions/{roomName}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VoiceControlEvent>> studentEvents(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String roomName
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.streamForStudent(roomName, currentUser);
    }

    @GetMapping(value = "/agent/sessions/{roomName}/commands", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VoiceControlEvent>> agentCommands(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String roomName
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.streamForAgent(roomName, currentUser);
    }

    @PostMapping("/sessions/{roomName}/interrupt")
    public VoiceControlEvent interrupt(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String roomName,
            @Valid @RequestBody(required = false) VoiceControlCommandRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.command(roomName, currentUser, "interrupt_tts", request);
    }

    @PostMapping("/sessions/{roomName}/end")
    public VoiceControlEvent end(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String roomName,
            @Valid @RequestBody(required = false) VoiceControlCommandRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.command(roomName, currentUser, "end_session", request);
    }

    @PostMapping("/agent/events")
    public VoiceControlEvent agentEvent(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VoiceAgentEventRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.agentEvent(currentUser, request);
    }

    @PostMapping(value = "/agent/transcripts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VoiceControlEvent>> agentTranscript(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VoiceAgentTranscriptRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.transcript(currentUser, request);
    }

    @PostMapping("/agent/asr/doubao")
    public Mono<VoiceAsrResponse> doubaoAsr(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VoiceAsrRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.doubaoAsr(currentUser, request);
    }

    @PostMapping("/agent/tts/minimax")
    public Mono<VoiceTtsResponse> miniMaxTts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VoiceTtsRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.miniMaxTts(currentUser, request);
    }

    @PostMapping("/agent/tts/doubao")
    public Mono<VoiceTtsResponse> doubaoTts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VoiceTtsRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.doubaoTts(currentUser, request);
    }

    @PostMapping("/agent/tts")
    public Mono<VoiceTtsResponse> tts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VoiceTtsRequest request
    ) {
        rejectAdmin(currentUser);
        return voiceAgentControlService.tts(currentUser, request);
    }

    private void rejectAdmin(CurrentUser currentUser) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员账号不能发起学生语音会话。");
        }
    }
}
