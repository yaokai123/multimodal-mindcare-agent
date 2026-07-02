package com.multimodalAgent.agent.repository;

import com.multimodalAgent.agent.domain.SupportGoal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportGoalRepository extends JpaRepository<SupportGoal, Long> {

    List<SupportGoal> findTop20ByUser_IdOrderByActiveDescUpdatedAtDesc(Long userId);

    Optional<SupportGoal> findByIdAndUser_Id(Long id, Long userId);
}
