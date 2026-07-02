package com.multimodalAgent.agent.service;

import com.multimodalAgent.agent.domain.MoodJournalEntry;
import com.multimodalAgent.agent.domain.PsychologicalReport;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.domain.SupportTask;
import com.multimodalAgent.agent.domain.SupportGoal;
import com.multimodalAgent.agent.domain.UserAccount;
import com.multimodalAgent.agent.dto.MoodJournalRequest;
import com.multimodalAgent.agent.dto.MoodTrendPoint;
import com.multimodalAgent.agent.dto.SupportGoalRequest;
import com.multimodalAgent.agent.dto.SupportTaskRequest;
import com.multimodalAgent.agent.dto.SupportTrendResponse;
import com.multimodalAgent.agent.dto.TriggerClusterResponse;
import com.multimodalAgent.agent.repository.MoodJournalRepository;
import com.multimodalAgent.agent.repository.PsychologicalReportRepository;
import com.multimodalAgent.agent.repository.SupportGoalRepository;
import com.multimodalAgent.agent.repository.SupportTaskRepository;
import com.multimodalAgent.agent.repository.UserAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentSupportService {

    private final MoodJournalRepository moodJournalRepository;
    private final SupportTaskRepository supportTaskRepository;
    private final UserAccountRepository userAccountRepository;
    private final PsychologicalReportRepository reportRepository;
    private final SupportGoalRepository supportGoalRepository;

    public StudentSupportService(
            MoodJournalRepository moodJournalRepository,
            SupportTaskRepository supportTaskRepository,
            UserAccountRepository userAccountRepository,
            PsychologicalReportRepository reportRepository,
            SupportGoalRepository supportGoalRepository
    ) {
        this.moodJournalRepository = moodJournalRepository;
        this.supportTaskRepository = supportTaskRepository;
        this.userAccountRepository = userAccountRepository;
        this.reportRepository = reportRepository;
        this.supportGoalRepository = supportGoalRepository;
    }

    @Transactional(readOnly = true)
    public List<MoodJournalEntry> latestMoodEntries(Long userId) {
        return moodJournalRepository.findTop14ByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public MoodJournalEntry createMoodEntry(Long userId, MoodJournalRequest request) {
        UserAccount user = user(userId);
        MoodJournalEntry entry = new MoodJournalEntry();
        entry.setUser(user);
        entry.setMoodScore(request.moodScore());
        entry.setMoodLabel(request.moodLabel().trim());
        entry.setNote(blankToNull(request.note()));
        entry.setTrigger(blankToNull(request.trigger()));
        return moodJournalRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public SupportTrendResponse trend(Long userId, int days) {
        int safeDays = days == 7 || days == 14 || days == 30 ? days : 14;
        Instant since = Instant.now().minusSeconds((long) safeDays * 24 * 60 * 60);
        List<MoodJournalEntry> entries = moodJournalRepository.findByUser_IdAndCreatedAtAfterOrderByCreatedAtAsc(userId, since);
        List<SupportTask> tasks = latestTasks(userId);
        List<PsychologicalReport> reports = reportRepository.findTop20ByUser_IdOrderByCreatedAtDesc(userId);
        String latestRisk = reports.isEmpty() ? RiskLevel.LOW.name() : reports.get(0).getRiskLevel().name();
        long completed = tasks.stream().filter(SupportTask::isCompleted).count();
        int lowStreak = consecutiveLowMoodEntries(entries);
        return new SupportTrendResponse(
                safeDays,
                moodTrend(entries),
                triggerClusters(entries),
                lowStreak,
                lowStreak >= 3 || RiskLevel.HIGH.name().equals(latestRisk),
                latestRisk,
                careSuggestion(entries, latestRisk),
                tasks.size(),
                completed,
                tasks.isEmpty() ? 0.0 : completed / (double) tasks.size());
    }

    @Transactional(readOnly = true)
    public List<SupportTask> latestTasks(Long userId) {
        return supportTaskRepository.findTop20ByUser_IdOrderByCompletedAscCreatedAtDesc(userId);
    }

    @Transactional
    public SupportTask createTask(Long userId, SupportTaskRequest request) {
        UserAccount user = user(userId);
        SupportTask task = new SupportTask();
        task.setUser(user);
        task.setTitle(request.title().trim());
        task.setCategory(normalizeCategory(request.category()));
        task.setDetail(blankToNull(request.detail()));
        return supportTaskRepository.save(task);
    }

    @Transactional
    public SupportTask setTaskCompleted(Long userId, Long taskId, boolean completed) {
        SupportTask task = supportTaskRepository.findByIdAndUser_Id(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Support task not found: " + taskId));
        task.setCompleted(completed);
        return supportTaskRepository.save(task);
    }

    @Transactional
    public List<SupportTask> seedSuggestedTasks(Long userId) {
        if (!latestTasks(userId).isEmpty()) {
            return latestTasks(userId);
        }
        smartSuggestedTasks(userId).forEach(request ->
                createRecommendedTask(userId, request, "initial suggested support plan"));
        return latestTasks(userId);
    }

    @Transactional
    public List<SupportTask> generateSmartTasks(Long userId) {
        smartSuggestedTasks(userId).forEach(request ->
                createRecommendedTask(userId, request, "generated from recent mood and risk signals"));
        return latestTasks(userId);
    }

    @Transactional(readOnly = true)
    public List<SupportGoal> goals(Long userId) {
        return supportGoalRepository.findTop20ByUser_IdOrderByActiveDescUpdatedAtDesc(userId);
    }

    @Transactional
    public SupportGoal createGoal(Long userId, SupportGoalRequest request) {
        SupportGoal goal = new SupportGoal();
        goal.setUser(user(userId));
        goal.setTitle(request.title().trim());
        goal.setCategory(normalizeCategory(request.category()));
        goal.setDetail(blankToNull(request.detail()));
        if (request.active() != null) {
            goal.setActive(request.active());
        }
        return supportGoalRepository.save(goal);
    }

    @Transactional
    public SupportGoal updateGoal(Long userId, Long goalId, SupportGoalRequest request) {
        SupportGoal goal = supportGoalRepository.findByIdAndUser_Id(goalId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Support goal not found: " + goalId));
        goal.setTitle(request.title().trim());
        goal.setCategory(normalizeCategory(request.category()));
        goal.setDetail(blankToNull(request.detail()));
        if (request.active() != null) {
            goal.setActive(request.active());
        }
        return supportGoalRepository.save(goal);
    }

    private UserAccount user(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private SupportTask createRecommendedTask(Long userId, SupportTaskRequest request, String reason) {
        SupportTask task = createTask(userId, request);
        task.setRecommendationReason(reason);
        return supportTaskRepository.save(task);
    }

    private List<SupportTaskRequest> smartSuggestedTasks(Long userId) {
        List<MoodJournalEntry> moods = moodJournalRepository.findTop14ByUser_IdOrderByCreatedAtDesc(userId);
        List<PsychologicalReport> reports = reportRepository.findTop20ByUser_IdOrderByCreatedAtDesc(userId);
        String latestRisk = reports.isEmpty() ? RiskLevel.LOW.name() : reports.get(0).getRiskLevel().name();
        double averageMood = moods.stream().mapToInt(MoodJournalEntry::getMoodScore).average().orElse(3.0);
        String trigger = moods.stream()
                .map(MoodJournalEntry::getTrigger)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("recent stressor");
        String activeGoal = goals(userId).stream()
                .filter(SupportGoal::isActive)
                .map(SupportGoal::getCategory)
                .findFirst()
                .orElse("");
        if (RiskLevel.HIGH.name().equals(latestRisk)) {
            return List.of(
                    new SupportTaskRequest("Contact a trusted person today", "CRISIS_CONNECTION", "Send one short message to a counselor, family member, roommate, or trusted friend."),
                    new SupportTaskRequest("Write down your immediate safety plan", "SAFETY_PLAN", "List one safe place, one person to contact, and one thing to remove from reach."),
                    new SupportTaskRequest("Use a 3-minute grounding exercise", "GROUNDING", "Name 5 things you see, 4 you feel, 3 you hear, 2 you smell, and 1 you taste."));
        }
        if (averageMood <= 2.5) {
            return List.of(
                    new SupportTaskRequest("Take a 10-minute reset walk", "BEHAVIORAL_ACTIVATION", "Keep it small: shoes on, step outside or walk indoors, then return."),
                    new SupportTaskRequest("Record one helpful thought", "JOURNAL", "Write one sentence that is kinder or more balanced than the hardest thought today."),
                    new SupportTaskRequest("Ask for one concrete help item", "CONNECTION", "Message someone with one specific request connected to " + trigger + "."));
        }
        return List.of(
                new SupportTaskRequest(goalTaskTitle(activeGoal), activeGoal.isBlank() ? "STUDY_STRESS" : activeGoal, "Set a 20-minute timer and choose only one tiny task connected to your active support goal."),
                new SupportTaskRequest("Do one body check-in", "SELF_CARE", "Drink water, stretch your shoulders, and rate tension from 1 to 5."),
                new SupportTaskRequest("Write tomorrow's first step", "PLANNING", "Choose one action that takes less than 10 minutes."));
    }

    private String goalTaskTitle(String activeGoal) {
        return switch (activeGoal) {
            case "SLEEP", "SLEEP_SUPPORT" -> "Prepare one sleep-friendly routine step";
            case "STUDY_STRESS" -> "Plan one low-pressure study block";
            case "CONNECTION", "RELATIONSHIP" -> "Send one low-pressure check-in message";
            case "EMOTION_STABILITY" -> "Practice one mood regulation step";
            default -> "Plan one low-pressure support step";
        };
    }

    private List<MoodTrendPoint> moodTrend(List<MoodJournalEntry> entries) {
        Map<LocalDate, List<MoodJournalEntry>> byDate = entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> LocalDate.ofInstant(entry.getCreatedAt(), ZoneId.systemDefault()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return byDate.entrySet().stream()
                .map(entry -> new MoodTrendPoint(
                        entry.getKey(),
                        entry.getValue().stream().mapToInt(MoodJournalEntry::getMoodScore).average().orElse(0.0),
                        entry.getValue().size()))
                .toList();
    }

    private List<TriggerClusterResponse> triggerClusters(List<MoodJournalEntry> entries) {
        return entries.stream()
                .filter(entry -> entry.getTrigger() != null && !entry.getTrigger().isBlank())
                .collect(Collectors.groupingBy(entry -> entry.getTrigger().trim().toLowerCase(Locale.ROOT)))
                .entrySet().stream()
                .map(entry -> new TriggerClusterResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToInt(MoodJournalEntry::getMoodScore).average().orElse(0.0)))
                .sorted(Comparator.comparingLong(TriggerClusterResponse::count).reversed())
                .limit(6)
                .toList();
    }

    private int consecutiveLowMoodEntries(List<MoodJournalEntry> entries) {
        int count = 0;
        List<MoodJournalEntry> latestFirst = entries.stream()
                .sorted(Comparator.comparing(MoodJournalEntry::getCreatedAt).reversed())
                .toList();
        for (MoodJournalEntry entry : latestFirst) {
            if (entry.getMoodScore() > 2) {
                break;
            }
            count++;
        }
        return count;
    }

    private String careSuggestion(List<MoodJournalEntry> entries, String latestRisk) {
        if (RiskLevel.HIGH.name().equals(latestRisk)) {
            return "High risk signal detected: prioritize immediate human contact and escalation support.";
        }
        int lowStreak = consecutiveLowMoodEntries(entries);
        if (lowStreak >= 3) {
            return "Mood has stayed low across recent entries: consider a counselor check-in and smaller daily goals.";
        }
        double average = entries.stream().mapToInt(MoodJournalEntry::getMoodScore).average().orElse(3.0);
        if (average <= 2.5) {
            return "Recent mood is below baseline: use short grounding, connection, and one tiny activation task.";
        }
        return "Mood trend is stable enough for light planning and preventive self-care.";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "SELF_CARE";
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
