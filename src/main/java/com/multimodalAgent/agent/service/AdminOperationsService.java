package com.multimodalAgent.agent.service;

import com.multimodalAgent.agent.domain.ChatMessage;
import com.multimodalAgent.agent.domain.ChatSession;
import com.multimodalAgent.agent.domain.MessageRole;
import com.multimodalAgent.agent.domain.MoodJournalEntry;
import com.multimodalAgent.agent.domain.PsychologicalReport;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.domain.RiskTicket;
import com.multimodalAgent.agent.domain.RiskTicketEvent;
import com.multimodalAgent.agent.domain.StudentCaseNote;
import com.multimodalAgent.agent.domain.SupportGoal;
import com.multimodalAgent.agent.domain.SupportTask;
import com.multimodalAgent.agent.domain.UserAccount;
import com.multimodalAgent.agent.dto.AdminDashboardResponse;
import com.multimodalAgent.agent.dto.ConversationMessageResponse;
import com.multimodalAgent.agent.dto.RiskTicketEventResponse;
import com.multimodalAgent.agent.dto.RiskTicketResponse;
import com.multimodalAgent.agent.dto.StudentCaseNoteRequest;
import com.multimodalAgent.agent.dto.StudentCaseNoteResponse;
import com.multimodalAgent.agent.dto.StudentCaseSummaryResponse;
import com.multimodalAgent.agent.dto.StudentAnomalyResponse;
import com.multimodalAgent.agent.dto.StudentProfileResponse;
import com.multimodalAgent.agent.dto.SupportGoalResponse;
import com.multimodalAgent.agent.dto.SupportTaskResponse;
import com.multimodalAgent.agent.dto.SupportTrendResponse;
import com.multimodalAgent.agent.repository.ChatMessageRepository;
import com.multimodalAgent.agent.repository.ChatSessionRepository;
import com.multimodalAgent.agent.repository.MoodJournalRepository;
import com.multimodalAgent.agent.repository.PsychologicalReportRepository;
import com.multimodalAgent.agent.repository.RiskTicketEventRepository;
import com.multimodalAgent.agent.repository.RiskTicketRepository;
import com.multimodalAgent.agent.repository.StudentCaseNoteRepository;
import com.multimodalAgent.agent.repository.SupportTaskRepository;
import com.multimodalAgent.agent.repository.UserAccountRepository;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationsService {

    private final PsychologicalReportRepository reportRepository;
    private final RiskTicketRepository riskTicketRepository;
    private final RiskTicketEventRepository eventRepository;
    private final MoodJournalRepository moodJournalRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SupportTaskRepository supportTaskRepository;
    private final StudentCaseNoteRepository caseNoteRepository;
    private final StudentSupportService supportService;
    private final UserAccountRepository userAccountRepository;
    private final AuditService auditService;

    public AdminOperationsService(
            PsychologicalReportRepository reportRepository,
            RiskTicketRepository riskTicketRepository,
            RiskTicketEventRepository eventRepository,
            MoodJournalRepository moodJournalRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            SupportTaskRepository supportTaskRepository,
            StudentCaseNoteRepository caseNoteRepository,
            StudentSupportService supportService,
            UserAccountRepository userAccountRepository,
            AuditService auditService
    ) {
        this.reportRepository = reportRepository;
        this.riskTicketRepository = riskTicketRepository;
        this.eventRepository = eventRepository;
        this.moodJournalRepository = moodJournalRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.supportTaskRepository = supportTaskRepository;
        this.caseNoteRepository = caseNoteRepository;
        this.supportService = supportService;
        this.userAccountRepository = userAccountRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        List<PsychologicalReport> reports = reportRepository.findTop100ByOrderByCreatedAtDesc();
        List<RiskTicket> tickets = riskTicketRepository.findTop100ByOrderByUpdatedAtDesc();
        return new AdminDashboardResponse(
                highRiskTrend(reports),
                openTicketTrend(tickets),
                averageResponseMinutes(tickets),
                riskSources(reports),
                anomalies(reports, tickets));
    }

    @Transactional(readOnly = true)
    public List<StudentCaseSummaryResponse> studentCases() {
        List<RiskTicket> tickets = riskTicketRepository.findTop100ByOrderByUpdatedAtDesc();
        List<PsychologicalReport> reports = reportRepository.findTop100ByOrderByCreatedAtDesc();
        return userAccountRepository.findAll().stream()
                .filter(user -> user.getRoles().contains("ROLE_STUDENT"))
                .map(user -> studentCaseSummary(user, reports, tickets))
                .sorted(Comparator.comparing(StudentCaseSummaryResponse::latestActivityAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse studentProfile(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        List<ChatSession> sessions = chatSessionRepository.findTop5ByUser_IdOrderByUpdatedAtDesc(userId);
        List<ConversationMessageResponse> recentConversation = sessions.isEmpty()
                ? List.of()
                : chatMessageRepository.findTop20BySession_IdOrderByCreatedAtDesc(sessions.get(0).getId()).stream()
                        .filter(message -> message.getRole() != MessageRole.SYSTEM)
                        .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                        .map(ConversationMessageResponse::from)
                        .toList();
        List<RiskTicketResponse> tickets = riskTicketRepository.findTop100ByOrderByUpdatedAtDesc().stream()
                .filter(ticket -> ticket.getReport().getUser().getId().equals(userId))
                .map(ticket -> RiskTicketResponse.from(ticket, eventRepository.findByTicket_IdOrderByCreatedAtDesc(ticket.getId()).stream()
                        .map(RiskTicketEventResponse::from)
                        .toList()))
                .toList();
        List<StudentCaseNoteResponse> notes = caseNoteRepository.findTop20ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(StudentCaseNoteResponse::from)
                .toList();
        List<SupportGoalResponse> goals = supportService.goals(userId).stream().map(SupportGoalResponse::from).toList();
        return new StudentProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                currentSupportFocus(goals),
                List.of(windowSummary(userId, 7), windowSummary(userId, 14), windowSummary(userId, 30)),
                recentConversation,
                supportService.trend(userId, 30),
                supportService.latestTasks(userId).stream().map(SupportTaskResponse::from).toList(),
                tickets,
                notes,
                goals);
    }

    @Transactional
    public StudentCaseNoteResponse addStudentCaseNote(Long userId, StudentCaseNoteRequest request, String actor) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        StudentCaseNote note = new StudentCaseNote();
        note.setUser(user);
        note.setActor(actor);
        note.setNoteType(normalizeNoteType(request.noteType()));
        note.setContent(request.content().trim());
        StudentCaseNote saved = caseNoteRepository.save(note);
        auditService.log(actor, "STUDENT_CASE_NOTE", "STUDENT", String.valueOf(userId),
                "noteType=" + saved.getNoteType());
        return StudentCaseNoteResponse.from(saved);
    }

    @Transactional
    public void triggerNotification(Long ticketId, String actor) {
        RiskTicket ticket = riskTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Risk ticket not found: " + ticketId));
        RiskTicketEvent event = new RiskTicketEvent();
        event.setTicket(ticket);
        event.setEventType("NOTIFICATION");
        event.setOutcome("MANUAL_TRIGGER");
        event.setActor(actor);
        event.setNote("Manual notification requested for " + ticket.getReport().getUser().getUsername());
        eventRepository.save(event);
        ticket.appendAction(actor + " triggered manual notification");
        riskTicketRepository.save(ticket);
        auditService.log(actor, "NOTIFICATION_TRIGGERED", "RISK_TICKET", String.valueOf(ticketId),
                "Manual notification requested for " + ticket.getReport().getUser().getUsername());
    }

    @Transactional(readOnly = true)
    public String exportRiskTicketsCsv() {
        StringBuilder csv = new StringBuilder("ticketId,username,status,riskLevel,assignedTo,updatedAt,resolutionType\n");
        for (RiskTicket ticket : riskTicketRepository.findTop100ByOrderByUpdatedAtDesc()) {
            csv.append(ticket.getId()).append(',')
                    .append(escape(ticket.getReport().getUser().getUsername())).append(',')
                    .append(ticket.getStatus()).append(',')
                    .append(ticket.getReport().getRiskLevel()).append(',')
                    .append(escape(ticket.getAssignedTo())).append(',')
                    .append(ticket.getUpdatedAt()).append(',')
                    .append(escape(ticket.getResolutionType())).append('\n');
        }
        return csv.toString();
    }

    private List<AdminDashboardResponse.DailyRiskPoint> highRiskTrend(List<PsychologicalReport> reports) {
        Map<LocalDate, Long> counts = reports.stream()
                .filter(report -> report.getRiskLevel() == RiskLevel.HIGH)
                .collect(Collectors.groupingBy(this::reportDate, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AdminDashboardResponse.DailyRiskPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<AdminDashboardResponse.DailyTicketPoint> openTicketTrend(List<RiskTicket> tickets) {
        Map<LocalDate, Long> counts = tickets.stream()
                .filter(ticket -> ticket.getStatus().isActive())
                .collect(Collectors.groupingBy(ticket -> LocalDate.ofInstant(ticket.getCreatedAt(), ZoneId.systemDefault()),
                        LinkedHashMap::new,
                        Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AdminDashboardResponse.DailyTicketPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private double averageResponseMinutes(List<RiskTicket> tickets) {
        return tickets.stream()
                .filter(ticket -> ticket.getClosedAt() != null)
                .mapToLong(ticket -> Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()).toMinutes())
                .average()
                .orElse(0.0);
    }

    private List<AdminDashboardResponse.NamedCount> riskSources(List<PsychologicalReport> reports) {
        return reports.stream()
                .collect(Collectors.groupingBy(report -> report.getIntent().name(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new AdminDashboardResponse.NamedCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(AdminDashboardResponse.NamedCount::count).reversed())
                .toList();
    }

    private List<StudentAnomalyResponse> anomalies(List<PsychologicalReport> reports, List<RiskTicket> tickets) {
        Map<Long, List<RiskTicket>> ticketsByUser = tickets.stream()
                .collect(Collectors.groupingBy(ticket -> ticket.getReport().getUser().getId()));
        return reports.stream()
                .filter(report -> report.getRiskLevel() == RiskLevel.HIGH || report.getRiskLevel() == RiskLevel.MEDIUM)
                .collect(Collectors.groupingBy(report -> report.getUser().getId()))
                .values().stream()
                .map(userReports -> anomaly(userReports, ticketsByUser.getOrDefault(userReports.get(0).getUser().getId(), List.of())))
                .sorted(Comparator.comparing(StudentAnomalyResponse::latestActivityAt).reversed())
                .limit(12)
                .toList();
    }

    private StudentCaseSummaryResponse studentCaseSummary(
            UserAccount user,
            List<PsychologicalReport> reports,
            List<RiskTicket> tickets
    ) {
        Long userId = user.getId();
        List<PsychologicalReport> userReports = reports.stream()
                .filter(report -> report.getUser().getId().equals(userId))
                .toList();
        List<RiskTicket> userTickets = tickets.stream()
                .filter(ticket -> ticket.getReport().getUser().getId().equals(userId))
                .toList();
        List<SupportGoal> goals = supportService.goals(userId);
        List<SupportTask> tasks = supportService.latestTasks(userId);
        PsychologicalReport latestReport = userReports.stream()
                .max(Comparator.comparing(PsychologicalReport::getCreatedAt))
                .orElse(null);
        long openTickets = userTickets.stream().filter(ticket -> ticket.getStatus().isActive()).count();
        long pendingTasks = tasks.stream().filter(task -> !task.isCompleted()).count();
        Instant latestActivityAt = latestActivity(userId, userReports, userTickets);
        String nextStep = nextStep(openTickets, latestReport == null ? RiskLevel.LOW : latestReport.getRiskLevel(), goals, pendingTasks);
        return new StudentCaseSummaryResponse(
                userId,
                user.getUsername(),
                user.getDisplayName(),
                latestReport == null ? RiskLevel.LOW.name() : latestReport.getRiskLevel().name(),
                recentMoodAverage(userId),
                openTickets,
                goals.stream().filter(SupportGoal::isActive).count(),
                pendingTasks,
                latestActivityAt,
                nextStep);
    }

    private StudentProfileResponse.WindowSummary windowSummary(Long userId, int days) {
        SupportTrendResponse trend = supportService.trend(userId, days);
        long openTickets = riskTicketRepository.findTop100ByOrderByUpdatedAtDesc().stream()
                .filter(ticket -> ticket.getReport().getUser().getId().equals(userId))
                .filter(ticket -> ticket.getStatus().isActive())
                .count();
        double averageMood = trend.moodTrend().stream()
                .mapToDouble(point -> point.averageScore())
                .average()
                .orElse(0.0);
        int moodEntries = trend.moodTrend().stream()
                .mapToInt(point -> point.entries())
                .sum();
        return new StudentProfileResponse.WindowSummary(
                days,
                averageMood,
                moodEntries,
                trend.completedTasks(),
                trend.totalTasks(),
                openTickets,
                trend.latestRiskLevel(),
                trend.careSuggestion());
    }

    private String currentSupportFocus(List<SupportGoalResponse> goals) {
        List<SupportGoalResponse> active = goals.stream()
                .filter(SupportGoalResponse::active)
                .toList();
        if (active.isEmpty()) {
            return "No active support goal yet";
        }
        return active.stream()
                .map(goal -> goal.category() + ": " + goal.title())
                .collect(Collectors.joining(" / "));
    }

    private Instant latestActivity(Long userId, List<PsychologicalReport> reports, List<RiskTicket> tickets) {
        Instant reportAt = reports.stream().map(PsychologicalReport::getCreatedAt).max(Instant::compareTo).orElse(null);
        Instant ticketAt = tickets.stream().map(RiskTicket::getUpdatedAt).max(Instant::compareTo).orElse(null);
        Instant moodAt = moodJournalRepository.findTop14ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(MoodJournalEntry::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(null);
        return List.of(reportAt, ticketAt, moodAt).stream()
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private String nextStep(long openTickets, RiskLevel riskLevel, List<SupportGoal> goals, long pendingTasks) {
        if (openTickets > 0 || riskLevel == RiskLevel.HIGH) {
            return "Prioritize human follow-up and intervention timeline review.";
        }
        if (goals.stream().noneMatch(SupportGoal::isActive)) {
            return "Set one active support goal before the next conversation.";
        }
        if (pendingTasks == 0) {
            return "Generate or assign one small support task tied to the active goal.";
        }
        return "Continue goal-based check-in and monitor mood trend.";
    }

    private String normalizeNoteType(String noteType) {
        if (noteType == null || noteType.isBlank()) {
            return "FOLLOW_UP";
        }
        String normalized = noteType.trim().toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private StudentAnomalyResponse anomaly(List<PsychologicalReport> reports, List<RiskTicket> tickets) {
        PsychologicalReport latest = reports.stream()
                .max(Comparator.comparing(PsychologicalReport::getCreatedAt))
                .orElseThrow();
        long openTickets = tickets.stream().filter(ticket -> ticket.getStatus().isActive()).count();
        double moodAverage = recentMoodAverage(latest.getUser().getId());
        String reason = latest.getRiskLevel() == RiskLevel.HIGH ? "latest high-risk report" : "medium-risk trend";
        if (openTickets > 0) {
            reason += ", open intervention";
        }
        if (moodAverage > 0 && moodAverage <= 2.5) {
            reason += ", low mood average";
        }
        return new StudentAnomalyResponse(
                latest.getUser().getId(),
                latest.getUser().getUsername(),
                latest.getRiskLevel().name(),
                moodAverage,
                openTickets,
                reason,
                latest.getCreatedAt());
    }

    private LocalDate reportDate(PsychologicalReport report) {
        return LocalDate.ofInstant(report.getCreatedAt(), ZoneId.systemDefault());
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private double recentMoodAverage(Long userId) {
        return moodJournalRepository.findTop14ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .mapToInt(com.multimodalAgent.agent.domain.MoodJournalEntry::getMoodScore)
                .average()
                .orElse(0.0);
    }
}
