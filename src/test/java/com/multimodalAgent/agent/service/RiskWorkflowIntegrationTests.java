package com.multimodalAgent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.multimodalAgent.agent.domain.EmotionLabel;
import com.multimodalAgent.agent.domain.IntentType;
import com.multimodalAgent.agent.domain.PsychologicalReport;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.domain.RiskTicket;
import com.multimodalAgent.agent.domain.RiskTicketStatus;
import com.multimodalAgent.agent.domain.UserAccount;
import com.multimodalAgent.agent.dto.RiskTicketEventRequest;
import com.multimodalAgent.agent.dto.RiskTicketUpdateRequest;
import com.multimodalAgent.agent.repository.PsychologicalReportRepository;
import com.multimodalAgent.agent.repository.RiskTicketRepository;
import com.multimodalAgent.agent.repository.UserAccountRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:risk-workflow-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class RiskWorkflowIntegrationTests {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PsychologicalReportRepository reportRepository;

    @Autowired
    private RiskTicketRepository riskTicketRepository;

    @Autowired
    private RiskTicketService riskTicketService;

    @Test
    void highRiskReportCanMoveThroughInterventionWorkflow() {
        UserAccount student = new UserAccount();
        student.setUsername("workflow-student");
        student.setDisplayName("Workflow Student");
        student.setPassword("{noop}secret");
        student.setRoles(Set.of("ROLE_STUDENT"));
        student = userAccountRepository.save(student);

        PsychologicalReport report = new PsychologicalReport();
        report.setUser(student);
        report.setContent("I may hurt myself tonight.");
        report.setIntent(IntentType.RISK);
        report.setEmotion(EmotionLabel.HIGH_RISK);
        report.setEmotionScore(4.8);
        report.setRiskLevel(RiskLevel.HIGH);
        report.setConfidence(0.92);
        report.setSummary("High-risk self-harm signal");
        report = reportRepository.save(report);

        riskTicketService.ensureTicketForReport(report);
        RiskTicket ticket = riskTicketRepository.findByReport_Id(report.getId()).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(RiskTicketStatus.PENDING);

        riskTicketService.addEvent(ticket.getId(), new RiskTicketEventRequest(
                "CONTACT",
                "CONTACTED_SAFE",
                "Counselor confirmed immediate support."), "counselor");
        ticket = riskTicketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(RiskTicketStatus.CONTACTED);

        ticket = riskTicketService.updateTicket(ticket.getId(), new RiskTicketUpdateRequest(
                RiskTicketStatus.CLOSED,
                "counselor",
                "Follow-up scheduled.",
                "PHONE",
                "student",
                null,
                "CONTINUED_FOLLOW_UP",
                "FOLLOW_UP_PLANNED",
                "Student has a next-day counselor appointment."), "counselor");
        assertThat(ticket.getStatus()).isEqualTo(RiskTicketStatus.CLOSED);
        assertThat(ticket.getClosedAt()).isNotNull();
        assertThat(ticket.getResolutionType()).isEqualTo("FOLLOW_UP_PLANNED");
    }
}
