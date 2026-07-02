package com.multimodalAgent.agent.service;

import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.domain.ChatMessage;
import com.multimodalAgent.agent.domain.MessageRole;
import com.multimodalAgent.agent.domain.PsychologicalReport;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.domain.StudentCaseNote;
import com.multimodalAgent.agent.domain.UserAccount;
import com.multimodalAgent.agent.dto.VoiceSessionSummaryResponse;
import com.multimodalAgent.agent.dto.VoiceSupportProfileResponse;
import com.multimodalAgent.agent.repository.ChatMessageRepository;
import com.multimodalAgent.agent.repository.PsychologicalReportRepository;
import com.multimodalAgent.agent.repository.StudentCaseNoteRepository;
import com.multimodalAgent.agent.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VoiceSupportPolicyService {

    private final multimodalAgentProperties properties;
    private final PsychologicalReportRepository reportRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StudentCaseNoteRepository caseNoteRepository;
    private final UserAccountRepository userAccountRepository;

    public VoiceSupportPolicyService(
            multimodalAgentProperties properties,
            PsychologicalReportRepository reportRepository,
            ChatMessageRepository chatMessageRepository,
            StudentCaseNoteRepository caseNoteRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.properties = properties;
        this.reportRepository = reportRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.caseNoteRepository = caseNoteRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public VoiceSupportProfileResponse profileForUser(Long userId) {
        return profileForRisk(latestRisk(userId));
    }

    public VoiceSupportProfileResponse profileForRisk(RiskLevel riskLevel) {
        RiskLevel normalized = riskLevel == null ? RiskLevel.LOW : riskLevel;
        return switch (normalized) {
            case HIGH -> highRiskProfile();
            case MEDIUM -> mediumRiskProfile();
            case LOW -> lowRiskProfile();
        };
    }

    public RiskLevel latestRisk(Long userId) {
        return reportRepository.findTop20ByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .map(PsychologicalReport::getRiskLevel)
                .orElse(RiskLevel.LOW);
    }

    public VoiceSessionSummaryResponse closeSummary(VoiceSessionService.VoiceRuntimeSession session) {
        RiskLevel riskAfter = latestRisk(session.userId());
        boolean changed = session.initialRiskLevel() != riskAfter;
        String summary = buildSummary(session, riskAfter, changed);
        String followUp = followUp(session, riskAfter);
        VoiceSessionSummaryResponse response = new VoiceSessionSummaryResponse(
                session.roomName(),
                session.chatSessionId(),
                session.createdAt(),
                Instant.now(),
                session.initialRiskLevel(),
                riskAfter,
                changed,
                session.supportMode(),
                session.ttsTone(),
                summary,
                followUp);
        saveCaseNote(session.userId(), response);
        return response;
    }

    private VoiceSupportProfileResponse lowRiskProfile() {
        return new VoiceSupportProfileResponse(
                RiskLevel.LOW,
                "SOOTHING_COMPANION",
                "SOOTHING",
                voice(properties.getVoice().getSoothingVoice()),
                "gentle",
                "supportive",
                "可以用一句话记录今晚最明显的感受，也可以只做一次慢呼吸。",
                """
                Voice mode: low-risk psychological companionship.
                Reply in warm, concise Chinese. Prioritize emotional listening, validation, and gentle companionship.
                For bedtime or fatigue topics, offer a short relaxation step. Give at most one small suggestion.
                """,
                false,
                null,
                "继续语音倾听或引导一次轻量放松。");
    }

    private VoiceSupportProfileResponse mediumRiskProfile() {
        return new VoiceSupportProfileResponse(
                RiskLevel.MEDIUM,
                "GUIDED_JOURNAL",
                "CONCISE",
                voice(properties.getVoice().getConciseVoice()),
                "slow",
                "low",
                "先写下触发事件、身体感受、此刻强度 1-5 分，再选择一个今晚能完成的小动作。",
                """
                Voice mode: medium-risk guided support.
                Speak slower and use shorter sentences. Reduce advice density. Ask one grounding question at a time.
                Gently guide the student to record a mood journal with trigger, feeling, score, and one tiny next task.
                """,
                false,
                null,
                "引导情绪日记，并把建议缩减为一个可完成的小任务。");
    }

    private VoiceSupportProfileResponse highRiskProfile() {
        String safetyMessage = "我会先暂停普通陪聊。现在最重要的是确认你是否安全：请先远离可能伤害自己的物品，尽量到有人能看见你的地方，并联系身边可信任的人或学校值班老师。若有立即危险，请立刻拨打当地紧急电话。";
        return new VoiceSupportProfileResponse(
                RiskLevel.HIGH,
                "CRISIS_SAFETY",
                "FORMAL_ALERT",
                voice(properties.getVoice().getFormalAlertVoice()),
                "slow-clear",
                "safety-only",
                null,
                """
                Voice mode: high-risk crisis safety flow.
                Stop ordinary companionship. Do not continue open-ended chat. Use calm, direct safety language.
                Focus on immediate safety, trusted human contact, emergency help, and handoff to intervention workflow.
                """,
                true,
                safetyMessage,
                "切换危机安全流程，提醒人工介入，并停止普通陪聊。");
    }

    private String buildSummary(VoiceSessionService.VoiceRuntimeSession session, RiskLevel riskAfter, boolean changed) {
        String transcript = recentConversation(session.chatSessionId());
        String changeText = changed
                ? "风险等级从 " + session.initialRiskLevel() + " 变化为 " + riskAfter + "。"
                : "风险等级保持为 " + riskAfter + "。";
        String focus = hasText(session.supportGoal()) ? "支持目标：" + session.supportGoal() + "。" : "未指定支持目标。";
        if (transcript.isBlank()) {
            return "语音会话已结束。" + focus + changeText + "本次会话未形成可汇总的聊天文本。";
        }
        return "语音会话已结束。" + focus + changeText + "近期对话要点：" + transcript;
    }

    private String followUp(VoiceSessionService.VoiceRuntimeSession session, RiskLevel riskAfter) {
        if (riskAfter == RiskLevel.HIGH || "CRISIS_SAFETY".equals(session.supportMode())) {
            return "建议管理员立即查看风险工单，确认联系记录和升级状态。";
        }
        if (riskAfter == RiskLevel.MEDIUM) {
            return "建议继续跟进情绪日记和一个低负担小任务，24 小时内复核风险变化。";
        }
        return "建议保持低频关怀，鼓励睡前放松或记录一次稳定情绪。";
    }

    private String recentConversation(String chatSessionId) {
        if (!hasText(chatSessionId)) {
            return "";
        }
        List<ChatMessage> messages = chatMessageRepository.findBySession_PublicIdOrderByCreatedAtAsc(chatSessionId);
        return messages.stream()
                .filter(message -> message.getRole() != MessageRole.SYSTEM)
                .skip(Math.max(0, messages.size() - 8))
                .map(message -> label(message.getRole()) + ":" + shorten(message.getContent(), 90))
                .reduce((left, right) -> left + " | " + right)
                .map(text -> shorten(text, 420))
                .orElse("");
    }

    private void saveCaseNote(Long userId, VoiceSessionSummaryResponse response) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        StudentCaseNote note = new StudentCaseNote();
        note.setUser(user);
        note.setActor("voice-system");
        note.setNoteType("VOICE_SESSION_SUMMARY");
        note.setContent(response.summary() + "\n建议：" + response.suggestedFollowUp());
        caseNoteRepository.save(note);
    }

    private String voice(String configured) {
        return hasText(configured) ? configured : properties.getVoice().getTts().getVoice();
    }

    private String label(MessageRole role) {
        return role == MessageRole.ASSISTANT ? "助手" : "学生";
    }

    private String shorten(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
