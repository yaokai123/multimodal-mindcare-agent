package com.multimodalAgent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.multimodalAgent.agent.domain.PsychologicalReport;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.domain.RiskTicket;
import com.multimodalAgent.agent.repository.PsychologicalReportRepository;
import com.multimodalAgent.agent.repository.RiskTicketEventRepository;
import com.multimodalAgent.agent.repository.RiskTicketRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RiskTicketServiceTests {

    @Test
    void createsTicketForHighRiskReport() {
        RiskTicketRepository repository = org.mockito.Mockito.mock(RiskTicketRepository.class);
        RiskTicketService service = new RiskTicketService(
                repository,
                org.mockito.Mockito.mock(PsychologicalReportRepository.class),
                org.mockito.Mockito.mock(RiskTicketEventRepository.class),
                org.mockito.Mockito.mock(AuditService.class));
        PsychologicalReport report = new PsychologicalReport();
        report.setRiskLevel(RiskLevel.HIGH);
        when(repository.findByReport_Id(null)).thenReturn(Optional.empty());
        when(repository.save(any(RiskTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureTicketForReport(report);

        ArgumentCaptor<RiskTicket> captor = ArgumentCaptor.forClass(RiskTicket.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReport()).isSameAs(report);
        assertThat(captor.getValue().getActionLog()).contains("created automatically");
    }

    @Test
    void skipsTicketForLowerRiskReport() {
        RiskTicketRepository repository = org.mockito.Mockito.mock(RiskTicketRepository.class);
        RiskTicketService service = new RiskTicketService(
                repository,
                org.mockito.Mockito.mock(PsychologicalReportRepository.class),
                org.mockito.Mockito.mock(RiskTicketEventRepository.class),
                org.mockito.Mockito.mock(AuditService.class));
        PsychologicalReport report = new PsychologicalReport();
        report.setRiskLevel(RiskLevel.MEDIUM);

        service.ensureTicketForReport(report);

        verify(repository, never()).save(any(RiskTicket.class));
    }
}
