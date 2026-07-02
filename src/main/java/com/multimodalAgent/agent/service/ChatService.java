package com.multimodalAgent.agent.service;

import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.domain.ChatMessage;
import com.multimodalAgent.agent.domain.ChatSession;
import com.multimodalAgent.agent.domain.IntentType;
import com.multimodalAgent.agent.domain.MessageRole;
import com.multimodalAgent.agent.domain.PsychologicalReport;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.domain.SupportGoal;
import com.multimodalAgent.agent.domain.UserAccount;
import com.multimodalAgent.agent.dto.ChatRequest;
import com.multimodalAgent.agent.dto.ChatStreamEvent;
import com.multimodalAgent.agent.dto.ConversationResponse;
import com.multimodalAgent.agent.dto.KnowledgeCitation;
import com.multimodalAgent.agent.dto.StudentConversationSummaryResponse;
import com.multimodalAgent.agent.repository.ChatMessageRepository;
import com.multimodalAgent.agent.repository.ChatSessionRepository;
import com.multimodalAgent.agent.repository.PsychologicalReportRepository;
import com.multimodalAgent.agent.repository.SupportGoalRepository;
import com.multimodalAgent.agent.repository.UserAccountRepository;
import com.multimodalAgent.agent.service.ai.AiClient;
import com.multimodalAgent.agent.service.ai.AiMessage;
import com.multimodalAgent.agent.service.ai.PromptTemplates;
import com.multimodalAgent.agent.service.knowledge.AgenticRagResult;
import com.multimodalAgent.agent.service.knowledge.AgenticRagService;
import com.multimodalAgent.agent.service.knowledge.KnowledgeService;
import com.multimodalAgent.agent.service.memory.ShortTermMemoryService;
import com.multimodalAgent.agent.service.memory.ShortTermMemoryService.MemoryMessage;
import com.multimodalAgent.agent.service.multimodal.MultimodalAnalysis;
import com.multimodalAgent.agent.service.multimodal.MultimodalSignal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
/**
 * 学生聊天主流程服务。
 *
 * <p>负责会话管理、Redis 短期记忆、MySQL 长期记忆、意图分类、RAG 检索、模型流式调用和后台报告触发。</p>
 */
public class ChatService {

    private final UserAccountRepository userAccountRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PsychologicalReportRepository reportRepository;
    private final multimodalAgentProperties properties;
    private final IntentClassifier intentClassifier;
    private final PsychologicalAssessmentService assessmentService;
    private final KnowledgeService knowledgeService;
    private final AgenticRagService agenticRagService;
    private final ToolOrchestrationService toolOrchestrationService;
    private final PrivacySanitizer privacySanitizer;
    private final ShortTermMemoryService shortTermMemoryService;
    private final AiClient aiClient;
    private final RiskTicketService riskTicketService;
    private final SupportGoalRepository supportGoalRepository;

    public ChatService(
            UserAccountRepository userAccountRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            PsychologicalReportRepository reportRepository,
            multimodalAgentProperties properties,
            IntentClassifier intentClassifier,
            PsychologicalAssessmentService assessmentService,
            KnowledgeService knowledgeService,
            AgenticRagService agenticRagService,
            ToolOrchestrationService toolOrchestrationService,
            PrivacySanitizer privacySanitizer,
            ShortTermMemoryService shortTermMemoryService,
            AiClient aiClient,
            RiskTicketService riskTicketService,
            SupportGoalRepository supportGoalRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.reportRepository = reportRepository;
        this.properties = properties;
        this.intentClassifier = intentClassifier;
        this.assessmentService = assessmentService;
        this.knowledgeService = knowledgeService;
        this.agenticRagService = agenticRagService;
        this.toolOrchestrationService = toolOrchestrationService;
        this.privacySanitizer = privacySanitizer;
        this.shortTermMemoryService = shortTermMemoryService;
        this.aiClient = aiClient;
        this.riskTicketService = riskTicketService;
        this.supportGoalRepository = supportGoalRepository;
    }

    public Flux<ServerSentEvent<ChatStreamEvent>> streamChat(Long userId, ChatRequest request) {
        // 聊天接口使用 SSE 流式返回；数据库读写放到 boundedElastic，避免阻塞响应线程。
        return Flux.just(event("phase", ChatStreamEvent.phase(null, "input")))
                .concatWith(Mono.fromCallable(() -> prepare(userId, request, null, null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::streamPrepared))
                .onErrorResume(exception -> Flux.just(event(
                        "error",
                        ChatStreamEvent.error(null, "服务暂时不可用：" + exception.getMessage()))));
    }

    public Flux<ServerSentEvent<ChatStreamEvent>> streamChat(Long userId, ChatRequest request, String knowledgeScope) {
        return Flux.just(event("phase", ChatStreamEvent.phase(null, "input")))
                .concatWith(Mono.fromCallable(() -> prepare(userId, request, null, knowledgeScope))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::streamPrepared))
                .onErrorResume(exception -> Flux.just(event(
                        "error",
                        ChatStreamEvent.error(null, "服务暂时不可用：" + exception.getMessage()))));
    }

    public Flux<ServerSentEvent<ChatStreamEvent>> streamChat(
            Long userId,
            ChatRequest request,
            String knowledgeScope,
            String extraSystemInstruction
    ) {
        return Flux.just(event("phase", ChatStreamEvent.phase(null, "input")))
                .concatWith(Mono.fromCallable(() -> prepare(userId, request, null, knowledgeScope, extraSystemInstruction))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::streamPrepared))
                .onErrorResume(exception -> Flux.just(event(
                        "error",
                        ChatStreamEvent.error(null, "鏈嶅姟鏆傛椂涓嶅彲鐢細" + exception.getMessage()))));
    }

    public Flux<ServerSentEvent<ChatStreamEvent>> streamMultimodal(Long userId, ChatRequest request, MultimodalAnalysis analysis) {
        return Mono.fromCallable(() -> prepare(userId, request, analysis, null, null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::streamPrepared)
                .onErrorResume(exception -> Flux.just(event(
                        "error",
                        ChatStreamEvent.error(null, "服务暂时不可用：" + exception.getMessage()))));
    }

    @Transactional(readOnly = true)
    public List<StudentConversationSummaryResponse> recentConversations(Long userId) {
        return chatSessionRepository.findTop10ByUser_IdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toStudentConversationSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse studentConversation(Long userId, String sessionId) {
        ChatSession session = chatSessionRepository.findByPublicIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        List<ChatMessage> messages = chatMessageRepository.findBySession_PublicIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(message -> message.getRole() != MessageRole.SYSTEM)
                .toList();
        return ConversationResponse.from(session, messages);
    }

    private PreparedConversation prepare(Long userId, ChatRequest request, MultimodalAnalysis multimodalAnalysis, String knowledgeScope) {
        return prepare(userId, request, multimodalAnalysis, knowledgeScope, null);
    }

    private PreparedConversation prepare(
            Long userId,
            ChatRequest request,
            MultimodalAnalysis multimodalAnalysis,
            String knowledgeScope,
            String extraSystemInstruction
    ) {
        String input = request.message().trim();
        String modelInput = privacySanitizer.sanitize(multimodalAnalysis == null ? input : multimodalAnalysis.modelText());
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ChatSession session = resolveSession(user, request.sessionId(), input);
        List<AiMessage> previousHistory = recentModelHistory(session);
        saveMessage(user, session, MessageRole.USER, input);
        if (multimodalAnalysis != null) {
            saveMultimodalMemory(user, session, multimodalAnalysis);
        }

        List<AiMessage> modelHistory = withCurrentUser(previousHistory, modelInput);
        IntentType intent = intentClassifier.classify(modelInput, modelHistory);
        if (multimodalAnalysis != null && multimodalAnalysis.fusedAssessment().risk() == RiskLevel.HIGH) {
            intent = IntentType.RISK;
        } else if (multimodalAnalysis != null && multimodalAnalysis.fusedAssessment().risk() == RiskLevel.MEDIUM && intent == IntentType.CHAT) {
            intent = IntentType.CONSULT;
        }
        PsychologyAssessment assessment = null;
        AgenticRagResult ragResult = AgenticRagResult.empty();
        PsychologicalReport report = null;

        // 普通聊天不进入心理评估和 RAG，避免把学习/生活问题强行变成测评。
        if (intent != IntentType.CHAT) {
            ragResult = agenticRagService.retrieve(modelInput, modelHistory, knowledgeScope);
            assessment = multimodalAnalysis == null
                    ? assessmentService.assess(modelInput, modelHistory)
                    : multimodalAnalysis.fusedAssessment();
            // 明确危险意图优先按高风险处理，防止模型评估偏保守导致预警漏触发。
            if (intent == IntentType.RISK && assessment.risk() != RiskLevel.HIGH) {
                assessment = new PsychologyAssessment(
                        assessment.emotion(),
                        Math.max(assessment.emotionScore(), 4.0),
                        RiskLevel.HIGH,
                        assessment.confidence(),
                        assessment.summary());
            }
            report = saveReport(user, session, input, intent, assessment, multimodalAnalysis);
        }

        RiskLevel riskLevel = assessment == null ? RiskLevel.LOW : assessment.risk();
        List<AiMessage> messages = buildMessages(user, intent, riskLevel, ragResult, modelHistory, extraSystemInstruction);
        Long reportId = report == null ? null : report.getId();
        return new PreparedConversation(
                user,
                session,
                intent,
                riskLevel,
                messages,
                reportId,
                ragResult.citations(),
                ragResult.sufficient(),
                ragResult.review(),
                multimodalAnalysis == null ? List.of() : multimodalAnalysis.signals());
    }

    private Flux<ServerSentEvent<ChatStreamEvent>> streamPrepared(PreparedConversation prepared) {
        StringBuilder assistantReply = new StringBuilder();
        Flux<ServerSentEvent<ChatStreamEvent>> meta = Flux.just(event(
                "meta",
                ChatStreamEvent.meta(prepared.session().getPublicId())));
        Flux<ServerSentEvent<ChatStreamEvent>> multimodal = prepared.multimodalSignals().isEmpty()
                ? Flux.empty()
                : Flux.just(event(
                        "multimodal",
                        ChatStreamEvent.multimodal(prepared.session().getPublicId(), prepared.multimodalSignals())));
        Flux<ServerSentEvent<ChatStreamEvent>> routerPhase = Flux.just(event(
                "phase",
                ChatStreamEvent.phase(prepared.session().getPublicId(), "router")));
        Flux<ServerSentEvent<ChatStreamEvent>> ragPhase = prepared.intent() == IntentType.CHAT
                ? Flux.empty()
                : Flux.just(event(
                        "phase",
                        ChatStreamEvent.phase(prepared.session().getPublicId(), "rag")));
        Flux<ServerSentEvent<ChatStreamEvent>> streamPhase = Flux.just(event(
                "phase",
                ChatStreamEvent.phase(prepared.session().getPublicId(), "stream")));

        Flux<ServerSentEvent<ChatStreamEvent>> tokens = aiClient.stream(prepared.messages())
                .doOnNext(assistantReply::append)
                .map(token -> event("token", ChatStreamEvent.token(prepared.session().getPublicId(), token)))
                .timeout(Duration.ofSeconds(45))
                .onErrorResume(exception -> Flux.just(event(
                        "error",
                        ChatStreamEvent.error(prepared.session().getPublicId(), "模型响应超时或失败，请稍后重试。"))))
                .switchIfEmpty(Flux.just(event(
                        "error",
                        ChatStreamEvent.error(prepared.session().getPublicId(), "模型没有返回内容，请稍后重试。"))));

        Flux<ServerSentEvent<ChatStreamEvent>> citations = prepared.intent() == IntentType.CHAT
                ? Flux.empty()
                : Flux.just(event(
                        "citations",
                        ChatStreamEvent.citations(
                                prepared.session().getPublicId(),
                                prepared.citations(),
                                prepared.grounded(),
                                prepared.ragReview())));
        Flux<ServerSentEvent<ChatStreamEvent>> mcpPhase = prepared.reportId() == null
                ? Flux.empty()
                : Flux.just(event(
                        "phase",
                        ChatStreamEvent.phase(prepared.session().getPublicId(), "mcp")));

        Mono<ServerSentEvent<ChatStreamEvent>> done = Mono.fromCallable(() -> {
            if (!assistantReply.isEmpty()) {
                saveMessage(prepared.user(), prepared.session(), MessageRole.ASSISTANT, assistantReply.toString());
            }
            // 工具链在模型回复完成后异步执行，不打断学生端正在进行的对话体验。
            if (prepared.reportId() != null) {
                toolOrchestrationService.handleAsync(prepared.reportId());
            }
            return event("done", ChatStreamEvent.done(prepared.session().getPublicId()));
        }).subscribeOn(Schedulers.boundedElastic());

        return meta
                .concatWith(multimodal)
                .concatWith(routerPhase)
                .concatWith(ragPhase)
                .concatWith(streamPhase)
                .concatWith(tokens)
                .concatWith(citations)
                .concatWith(mcpPhase)
                .concatWith(done);
    }

    private ChatSession resolveSession(UserAccount user, String publicId, String input) {
        if (publicId != null && !publicId.isBlank()) {
            return chatSessionRepository.findByPublicIdAndUser_Id(publicId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        }
        ChatSession session = new ChatSession();
        session.setPublicId(UUID.randomUUID().toString().replace("-", ""));
        session.setUser(user);
        session.setTitle(input.length() > 36 ? input.substring(0, 36) : input);
        return chatSessionRepository.save(session);
    }

    private StudentConversationSummaryResponse toStudentConversationSummary(ChatSession session) {
        List<ChatMessage> messages = chatMessageRepository.findBySession_PublicIdOrderByCreatedAtAsc(session.getPublicId());
        List<ChatMessage> visible = messages.stream()
                .filter(message -> message.getRole() != MessageRole.SYSTEM)
                .toList();
        PsychologicalReport report = reportRepository.findFirstBySession_IdOrderByCreatedAtDesc(session.getId())
                .orElse(null);
        String summary = visible.stream()
                .filter(message -> message.getRole() == MessageRole.USER)
                .reduce((first, second) -> second)
                .map(ChatMessage::getContent)
                .orElse(session.getTitle());
        String evidence = messages.stream()
                .map(ChatMessage::getContent)
                .collect(java.util.stream.Collectors.joining("\n"))
                .toLowerCase();
        return new StudentConversationSummaryResponse(
                session.getPublicId(),
                session.getTitle(),
                shorten(summary, 96),
                report == null ? RiskLevel.LOW : report.getRiskLevel(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                containsAny(evidence, "audio", "asr", "whisper", "语音", "音频"),
                containsAny(evidence, "image", "visual", "mediapipe", "图像", "图片"),
                containsAny(evidence, "video", "face", "视频"));
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String shorten(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "New conversation";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > limit ? normalized.substring(0, limit - 1) + "…" : normalized;
    }

    private void saveMessage(UserAccount user, ChatSession session, MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setUser(user);
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        chatMessageRepository.save(message);
        session.touch();
        chatSessionRepository.save(session);
        shortTermMemoryService.append(session.getPublicId(), role, content);
    }

    private void saveMultimodalMemory(UserAccount user, ChatSession session, MultimodalAnalysis analysis) {
        saveMessage(user, session, MessageRole.SYSTEM, multimodalMemory(analysis));
    }

    private String multimodalMemory(MultimodalAnalysis analysis) {
        String modalities = String.join("、", analysis.signals().stream()
                .map(MultimodalSignal::modality)
                .distinct()
                .toList());
        String evidence = String.join("；", analysis.signals().stream()
                .map(signal -> signal.modality() + "=" + signal.evidence())
                .toList());
        return """
                【多模态分析记忆】
                用户本轮上传了%s，后端已完成多模态情绪分析。后续如果用户追问“你是否根据图片/语音/视频分析”，应说明：我是基于后端多模态分析结果和你的文字一起判断，不是只凭文字猜测。不要否认已上传附件，也不要声称自己直接查看了原始文件。
                分析摘要：%s
                情绪标签：%s
                分析证据：%s
                """.formatted(
                modalities.isBlank() ? "附件" : modalities,
                analysis.summary(),
                analysis.emotionTagsJson(),
                evidence.isBlank() ? "无" : evidence);
    }

    private PsychologicalReport saveReport(
            UserAccount user,
            ChatSession session,
            String content,
            IntentType intent,
            PsychologyAssessment assessment,
            MultimodalAnalysis multimodalAnalysis
    ) {
        PsychologicalReport report = new PsychologicalReport();
        report.setUser(user);
        report.setSession(session);
        report.setContent(content);
        report.setIntent(intent);
        report.setEmotion(assessment.emotion());
        report.setEmotionScore(assessment.emotionScore());
        report.setRiskLevel(assessment.risk());
        report.setConfidence(assessment.confidence());
        report.setSummary(assessment.summary());
        report.setModalityScoresJson(modalityScoresJson(assessment, multimodalAnalysis));
        report.setFusionExplanation(fusionExplanation(assessment, multimodalAnalysis));
        report.setReviewRecommended(assessment.confidence() < 0.65 || (multimodalAnalysis != null
                && multimodalAnalysis.signals().stream().anyMatch(signal -> signal.confidence() < 0.6)));
        report.setRawEvidenceJson(rawEvidenceJson(content, multimodalAnalysis));
        if (multimodalAnalysis != null) {
            report.setEmotionTags(multimodalAnalysis.emotionTagsJson());
        }
        PsychologicalReport saved = reportRepository.save(report);
        riskTicketService.ensureTicketForReport(saved);
        return saved;
    }

    private String modalityScoresJson(PsychologyAssessment assessment, MultimodalAnalysis multimodalAnalysis) {
        if (multimodalAnalysis == null || multimodalAnalysis.signals().isEmpty()) {
            return """
                    {"text":{"score":%.2f,"confidence":%.2f,"emotion":"%s"}}
                    """.formatted(assessment.emotionScore(), assessment.confidence(), assessment.emotion());
        }
        return "{" + multimodalAnalysis.signals().stream()
                .map(signal -> "\"%s\":{\"score\":%.2f,\"confidence\":%.2f,\"emotion\":\"%s\"}"
                        .formatted(jsonSafe(signal.modality()), signal.score(), signal.confidence(), signal.emotion()))
                .collect(java.util.stream.Collectors.joining(",")) + "}";
    }

    private String fusionExplanation(PsychologyAssessment assessment, MultimodalAnalysis multimodalAnalysis) {
        if (multimodalAnalysis == null || multimodalAnalysis.signals().isEmpty()) {
            return "Text-only assessment. Risk was driven by text emotion score %.2f and confidence %.2f."
                    .formatted(assessment.emotionScore(), assessment.confidence());
        }
        String drivers = multimodalAnalysis.signals().stream()
                .filter(signal -> signal.score() >= 2.0 || signal.emotion() == com.multimodalAgent.agent.domain.EmotionLabel.HIGH_RISK)
                .map(signal -> signal.modality() + " raised risk: " + signal.emotion()
                        + " score=" + String.format("%.2f", signal.score())
                        + ", confidence=" + String.format("%.2f", signal.confidence()))
                .collect(java.util.stream.Collectors.joining("; "));
        if (drivers.isBlank()) {
            drivers = "No single modality strongly raised risk; final risk comes from weighted fusion.";
        }
        String review = assessment.confidence() < 0.65 ? " Confidence is low, manual review is recommended." : "";
        return drivers + review;
    }

    private String rawEvidenceJson(String content, MultimodalAnalysis multimodalAnalysis) {
        String textEvidence = redactEvidence(content);
        if (multimodalAnalysis == null || multimodalAnalysis.signals().isEmpty()) {
            return "{\"text\":\"" + jsonSafe(textEvidence) + "\"}";
        }
        return "{" + multimodalAnalysis.signals().stream()
                .map(signal -> "\"%s\":\"%s\"".formatted(jsonSafe(signal.modality()), jsonSafe(redactEvidence(signal.evidence()))))
                .collect(java.util.stream.Collectors.joining(",")) + "}";
    }

    private String redactEvidence(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[email]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[phone]")
                .replaceAll("\\b\\d{6,}\\b", "[number]")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String jsonSafe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<AiMessage> buildMessages(
            UserAccount user,
            IntentType intent,
            RiskLevel riskLevel,
            AgenticRagResult ragResult,
            List<AiMessage> history,
            String extraSystemInstruction
    ) {
        // Agentic RAG 计划和证据只作为系统上下文给模型使用，不直接展示后台评估信息给学生。
        String context = ragResult.contextBlock();
        List<AiMessage> messages = new ArrayList<>();
        messages.add(PromptTemplates.answerSystemPrompt(intent, riskLevel, context, user.getUsername()));
        String supportContext = activeSupportGoalContext(user.getId());
        if (!supportContext.isBlank()) {
            messages.add(AiMessage.system(supportContext));
        }
        if (extraSystemInstruction != null && !extraSystemInstruction.isBlank()) {
            messages.add(AiMessage.system(extraSystemInstruction));
        }

        int limit = messageWindowLimit();
        history.stream()
                .skip(Math.max(0, history.size() - limit))
                .forEach(messages::add);
        return messages;
    }

    private String activeSupportGoalContext(Long userId) {
        List<SupportGoal> goals = supportGoalRepository.findTop20ByUser_IdOrderByActiveDescUpdatedAtDesc(userId)
                .stream()
                .filter(SupportGoal::isActive)
                .limit(4)
                .toList();
        if (goals.isEmpty()) {
            return "";
        }
        String goalLines = goals.stream()
                .map(goal -> "- " + goal.getCategory() + ": " + goal.getTitle()
                        + (goal.getDetail() == null || goal.getDetail().isBlank() ? "" : " | " + goal.getDetail()))
                .collect(java.util.stream.Collectors.joining("\n"));
        return """
                Current student support goals:
                %s

                Use these goals as durable context. In normal or low-risk conversation, gently connect the reply to one
                active goal and suggest one small next step. If the user is in crisis or expresses self-harm intent,
                prioritize immediate safety and human support over goal progress.
                """.formatted(goalLines);
    }

    private List<ChatMessage> recentHistory(ChatSession session) {
        List<ChatMessage> history = chatMessageRepository.findTop20BySession_IdOrderByCreatedAtDesc(session.getId());
        Collections.reverse(history);
        return history;
    }

    private List<AiMessage> recentModelHistory(ChatSession session) {
        List<MemoryMessage> redisHistory = shortTermMemoryService.recent(session.getPublicId());
        if (!redisHistory.isEmpty()) {
            return redisHistory.stream()
                    .map(this::toAiMessage)
                    .toList();
        }

        // Redis 中没有短期记忆时，从 MySQL 长期记忆恢复最近 10 轮上下文。
        List<ChatMessage> databaseHistory = recentHistory(session);
        shortTermMemoryService.refresh(session.getPublicId(), databaseHistory.stream()
                .map(message -> new MemoryMessage(message.getRole(), message.getContent()))
                .toList());
        return databaseHistory.stream()
                .map(this::toAiMessage)
                .toList();
    }

    private List<AiMessage> withCurrentUser(List<AiMessage> previousHistory, String currentInput) {
        List<AiMessage> history = new ArrayList<>(previousHistory);
        history.add(AiMessage.user(currentInput));
        int limit = messageWindowLimit();
        return history.stream()
                .skip(Math.max(0, history.size() - limit))
                .toList();
    }

    private int messageWindowLimit() {
        // history-limit 以轮次理解，这里乘 2 保留用户和助手两侧消息。
        return Math.max(2, properties.getChat().getHistoryLimit() * 2);
    }

    private AiMessage toAiMessage(ChatMessage chatMessage) {
        String content = privacySanitizer.sanitize(chatMessage.getContent());
        return switch (chatMessage.getRole()) {
            case ASSISTANT -> AiMessage.assistant(content);
            case SYSTEM -> AiMessage.system(content);
            case USER -> AiMessage.user(content);
        };
    }

    private AiMessage toAiMessage(MemoryMessage memoryMessage) {
        String content = privacySanitizer.sanitize(memoryMessage.content());
        return switch (memoryMessage.role()) {
            case ASSISTANT -> AiMessage.assistant(content);
            case SYSTEM -> AiMessage.system(content);
            case USER -> AiMessage.user(content);
        };
    }

    private ServerSentEvent<ChatStreamEvent> event(String name, ChatStreamEvent data) {
        return ServerSentEvent.builder(data).event(name).build();
    }

    private record PreparedConversation(
            UserAccount user,
            ChatSession session,
            IntentType intent,
            RiskLevel riskLevel,
            List<AiMessage> messages,
            Long reportId,
            List<KnowledgeCitation> citations,
            boolean grounded,
            String ragReview,
            List<MultimodalSignal> multimodalSignals
    ) {
    }
}
