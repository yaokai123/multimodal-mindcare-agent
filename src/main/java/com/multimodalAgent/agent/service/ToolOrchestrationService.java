package com.multimodalAgent.agent.service;

import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.domain.AlertRecord;
import com.multimodalAgent.agent.domain.PsychologicalReport;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.domain.ToolStatus;
import com.multimodalAgent.agent.repository.AlertRecordRepository;
import com.multimodalAgent.agent.repository.PsychologicalReportRepository;
import com.multimodalAgent.agent.service.mcp.AlertNotifier;
import com.multimodalAgent.agent.service.mcp.ExcelReportWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ToolOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ToolOrchestrationService.class);

    private final ExcelReportWriter excelReportWriter;
    private final AlertNotifier alertNotifier;
    private final PsychologicalReportRepository reportRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final multimodalAgentProperties properties;
    private final TaskExecutor mcpTaskExecutor;
    private final TransactionTemplate transactionTemplate;

    public ToolOrchestrationService(
            ExcelReportWriter excelReportWriter,
            AlertNotifier alertNotifier,
            PsychologicalReportRepository reportRepository,
            AlertRecordRepository alertRecordRepository,
            multimodalAgentProperties properties,
            @Qualifier("mcpTaskExecutor") TaskExecutor mcpTaskExecutor,
            TransactionTemplate transactionTemplate
    ) {
        this.excelReportWriter = excelReportWriter;
        this.alertNotifier = alertNotifier;
        this.reportRepository = reportRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.properties = properties;
        this.mcpTaskExecutor = mcpTaskExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    public void handleAsync(Long reportId) {
        mcpTaskExecutor.execute(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> handleInTransaction(reportId));
            } catch (Exception exception) {
                log.error("Tool orchestration failed for reportId={}", reportId, exception);
            }
        });
    }

    @Transactional
    public void handle(Long reportId) {
        handleInTransaction(reportId);
    }

    private void handleInTransaction(Long reportId) {
        PsychologicalReport managedReport = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        writeExcel(managedReport);
        if (managedReport.getRiskLevel() == RiskLevel.HIGH && managedReport.getExcelStatus() == ToolStatus.SUCCESS) {
            sendAlerts(managedReport);
        }
        reportRepository.save(managedReport);
    }

    private void writeExcel(PsychologicalReport report) {
        try {
            excelReportWriter.write(report);
            report.setExcelStatus(ToolStatus.SUCCESS);
        } catch (Exception exception) {
            report.setExcelStatus(ToolStatus.FAILED);
            report.setToolError(shorten(exception.getMessage()));
            log.warn("Excel report write failed for reportId={}", report.getId(), exception);
        }
    }

    private void sendAlerts(PsychologicalReport report) {
        boolean allSuccess = true;
        for (String recipient : properties.getMcp().getEmail().getRecipients()) {
            AlertRecord alertRecord = new AlertRecord();
            alertRecord.setReport(report);
            alertRecord.setRecipient(recipient);
            alertRecordRepository.save(alertRecord);

            boolean sent = false;
            int maxAttempts = Math.max(1, properties.getMcp().getEmail().getMaxRetries() + 1);
            for (int attempt = 0; attempt < maxAttempts && !sent; attempt++) {
                try {
                    alertRecord.incrementAttempts();
                    alertNotifier.notify(alertRecord, report);
                    alertRecord.setStatus(ToolStatus.SUCCESS);
                    sent = true;
                } catch (Exception exception) {
                    alertRecord.setStatus(ToolStatus.FAILED);
                    alertRecord.setErrorMessage(shorten(exception.getMessage()));
                    log.warn(
                            "Alert send failed for reportId={}, recipient={}, attempt={}",
                            report.getId(),
                            recipient,
                            attempt + 1,
                            exception);
                }
            }
            alertRecordRepository.save(alertRecord);
            allSuccess = allSuccess && sent;
        }
        report.setEmailStatus(allSuccess ? ToolStatus.SUCCESS : ToolStatus.FAILED);
    }

    private String shorten(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
