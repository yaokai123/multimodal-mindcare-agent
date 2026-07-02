package com.multimodalAgent.agent.repository;

import com.multimodalAgent.agent.domain.RiskTicketEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskTicketEventRepository extends JpaRepository<RiskTicketEvent, Long> {

    List<RiskTicketEvent> findByTicket_IdOrderByCreatedAtDesc(Long ticketId);
}
