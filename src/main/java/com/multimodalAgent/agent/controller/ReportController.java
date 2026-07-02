package com.multimodalAgent.agent.controller;

import com.multimodalAgent.agent.dto.AdminDashboardResponse;
import com.multimodalAgent.agent.dto.AuditLogResponse;
import com.multimodalAgent.agent.dto.ConversationResponse;
import com.multimodalAgent.agent.dto.AlertRecordResponse;
import com.multimodalAgent.agent.dto.ExcelRecordResponse;
import com.multimodalAgent.agent.dto.ModelAssessmentFeedbackRequest;
import com.multimodalAgent.agent.dto.ModelAssessmentFeedbackResponse;
import com.multimodalAgent.agent.dto.ReportResponse;
import com.multimodalAgent.agent.dto.StudentCaseNoteRequest;
import com.multimodalAgent.agent.dto.StudentCaseNoteResponse;
import com.multimodalAgent.agent.dto.StudentCaseSummaryResponse;
import com.multimodalAgent.agent.dto.StudentProfileResponse;
import com.multimodalAgent.agent.security.CurrentUser;
import com.multimodalAgent.agent.service.AdminOperationsService;
import com.multimodalAgent.agent.service.AuditService;
import com.multimodalAgent.agent.service.ReportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
/**
 * 报告、Excel 记录、邮件记录和完整会话查询接口。
 *
 * <p>管理员后台的数据列表和详情弹窗主要由这些接口驱动。</p>
 */
public class ReportController {

    private final ReportService reportService;
    private final AdminOperationsService adminOperationsService;
    private final AuditService auditService;

    public ReportController(
            ReportService reportService,
            AdminOperationsService adminOperationsService,
            AuditService auditService
    ) {
        this.reportService = reportService;
        this.adminOperationsService = adminOperationsService;
        this.auditService = auditService;
    }

    @GetMapping("/reports/me")
    public List<ReportResponse> myReports(@AuthenticationPrincipal CurrentUser currentUser) {
        return reportService.myReports(currentUser.getId()).stream()
                .map(ReportResponse::from)
                .toList();
    }

    @GetMapping("/admin/reports")
    public List<ReportResponse> latestReports() {
        // 管理员统计大屏使用这个接口作为对话报告主数据源。
        return reportService.latestReports().stream()
                .map(ReportResponse::from)
                .toList();
    }

    @GetMapping("/admin/excel-records")
    public List<ExcelRecordResponse> excelRecords() {
        return reportService.excelRecords();
    }

    @GetMapping("/admin/alerts")
    public List<AlertRecordResponse> alertRecords() {
        return reportService.alertRecords();
    }

    @GetMapping("/admin/conversations/{sessionId}")
    public ConversationResponse conversation(@PathVariable String sessionId) {
        // 点开任一后台记录时读取完整会话，便于辅导员回看上下文。
        return reportService.conversation(sessionId);
    }

    @PostMapping("/admin/reports/{reportId}/feedback")
    public ModelAssessmentFeedbackResponse modelFeedback(
            @PathVariable Long reportId,
            @Valid @org.springframework.web.bind.annotation.RequestBody ModelAssessmentFeedbackRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        ModelAssessmentFeedbackResponse response = reportService.recordFeedback(reportId, request, actor(currentUser));
        auditService.log(actor(currentUser), "MODEL_ASSESSMENT_FEEDBACK", "PSYCHOLOGICAL_REPORT", String.valueOf(reportId),
                "type=" + request.feedbackType() + ", correctedRisk=" + request.correctedRiskLevel());
        return response;
    }

    @GetMapping("/admin/dashboard/trends")
    public AdminDashboardResponse dashboardTrends() {
        return adminOperationsService.dashboard();
    }

    @GetMapping("/admin/students/cases")
    public List<StudentCaseSummaryResponse> studentCases() {
        return adminOperationsService.studentCases();
    }

    @GetMapping("/admin/students/{userId}/profile")
    public StudentProfileResponse studentProfile(@PathVariable Long userId) {
        return adminOperationsService.studentProfile(userId);
    }

    @PostMapping("/admin/students/{userId}/notes")
    public StudentCaseNoteResponse addStudentCaseNote(
            @PathVariable Long userId,
            @Valid @org.springframework.web.bind.annotation.RequestBody StudentCaseNoteRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return adminOperationsService.addStudentCaseNote(userId, request, actor(currentUser));
    }

    @PostMapping("/admin/risk-tickets/{ticketId}/notify")
    public void triggerNotification(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        adminOperationsService.triggerNotification(ticketId, actor(currentUser));
    }

    @GetMapping("/admin/export/risk-tickets.csv")
    public ResponseEntity<String> exportRiskTickets(@AuthenticationPrincipal CurrentUser currentUser) {
        auditService.log(actor(currentUser), "EXPORT_RISK_TICKETS", "CSV", "risk-tickets", "Downloaded risk ticket CSV export.");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"risk-tickets.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(adminOperationsService.exportRiskTicketsCsv());
    }

    @GetMapping("/admin/audit-logs")
    public List<AuditLogResponse> auditLogs() {
        return auditService.latest().stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private String actor(CurrentUser currentUser) {
        return currentUser.getDisplayName() == null || currentUser.getDisplayName().isBlank()
                ? currentUser.getUsername()
                : currentUser.getDisplayName();
    }
}
