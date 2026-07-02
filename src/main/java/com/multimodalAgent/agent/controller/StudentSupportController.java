package com.multimodalAgent.agent.controller;

import com.multimodalAgent.agent.dto.MoodJournalRequest;
import com.multimodalAgent.agent.dto.MoodJournalResponse;
import com.multimodalAgent.agent.dto.SupportGoalRequest;
import com.multimodalAgent.agent.dto.SupportGoalResponse;
import com.multimodalAgent.agent.dto.SupportTrendResponse;
import com.multimodalAgent.agent.dto.SupportTaskRequest;
import com.multimodalAgent.agent.dto.SupportTaskResponse;
import com.multimodalAgent.agent.security.CurrentUser;
import com.multimodalAgent.agent.service.StudentSupportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/support")
public class StudentSupportController {

    private final StudentSupportService supportService;

    public StudentSupportController(StudentSupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping("/mood")
    public List<MoodJournalResponse> moodEntries(@AuthenticationPrincipal CurrentUser currentUser) {
        rejectAdmin(currentUser);
        return supportService.latestMoodEntries(currentUser.getId()).stream()
                .map(MoodJournalResponse::from)
                .toList();
    }

    @PostMapping("/mood")
    public MoodJournalResponse createMoodEntry(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody MoodJournalRequest request
    ) {
        rejectAdmin(currentUser);
        return MoodJournalResponse.from(supportService.createMoodEntry(currentUser.getId(), request));
    }

    @GetMapping("/trends")
    public SupportTrendResponse trends(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "14") int days
    ) {
        rejectAdmin(currentUser);
        return supportService.trend(currentUser.getId(), days);
    }

    @GetMapping("/goals")
    public List<SupportGoalResponse> goals(@AuthenticationPrincipal CurrentUser currentUser) {
        rejectAdmin(currentUser);
        return supportService.goals(currentUser.getId()).stream()
                .map(SupportGoalResponse::from)
                .toList();
    }

    @PostMapping("/goals")
    public SupportGoalResponse createGoal(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody SupportGoalRequest request
    ) {
        rejectAdmin(currentUser);
        return SupportGoalResponse.from(supportService.createGoal(currentUser.getId(), request));
    }

    @PatchMapping("/goals/{id}")
    public SupportGoalResponse updateGoal(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SupportGoalRequest request
    ) {
        rejectAdmin(currentUser);
        return SupportGoalResponse.from(supportService.updateGoal(currentUser.getId(), id, request));
    }

    @GetMapping("/tasks")
    public List<SupportTaskResponse> tasks(@AuthenticationPrincipal CurrentUser currentUser) {
        rejectAdmin(currentUser);
        return supportService.latestTasks(currentUser.getId()).stream()
                .map(SupportTaskResponse::from)
                .toList();
    }

    @PostMapping("/tasks")
    public SupportTaskResponse createTask(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody SupportTaskRequest request
    ) {
        rejectAdmin(currentUser);
        return SupportTaskResponse.from(supportService.createTask(currentUser.getId(), request));
    }

    @PostMapping("/tasks/suggested")
    public List<SupportTaskResponse> seedSuggestedTasks(@AuthenticationPrincipal CurrentUser currentUser) {
        rejectAdmin(currentUser);
        return supportService.seedSuggestedTasks(currentUser.getId()).stream()
                .map(SupportTaskResponse::from)
                .toList();
    }

    @PostMapping("/tasks/smart")
    public List<SupportTaskResponse> generateSmartTasks(@AuthenticationPrincipal CurrentUser currentUser) {
        rejectAdmin(currentUser);
        return supportService.generateSmartTasks(currentUser.getId()).stream()
                .map(SupportTaskResponse::from)
                .toList();
    }

    @PatchMapping("/tasks/{id}")
    public SupportTaskResponse updateTask(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @RequestParam boolean completed
    ) {
        rejectAdmin(currentUser);
        return SupportTaskResponse.from(supportService.setTaskCompleted(currentUser.getId(), id, completed));
    }

    private void rejectAdmin(CurrentUser currentUser) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin accounts cannot write student support records.");
        }
    }
}
