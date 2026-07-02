package com.multimodalAgent.agent.repository;

import com.multimodalAgent.agent.domain.RiskTicket;
import com.multimodalAgent.agent.domain.RiskTicketStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskTicketRepository extends JpaRepository<RiskTicket, Long> {

    Optional<RiskTicket> findByReport_Id(Long reportId);

    @EntityGraph(attributePaths = {"report", "report.user", "report.session"})
    List<RiskTicket> findTop100ByOrderByUpdatedAtDesc();

    long countByStatusNot(RiskTicketStatus status);
}
