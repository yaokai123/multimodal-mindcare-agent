package com.multimodalAgent.agent.repository;

import com.multimodalAgent.agent.domain.SupportTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTaskRepository extends JpaRepository<SupportTask, Long> {

    List<SupportTask> findTop20ByUser_IdOrderByCompletedAscCreatedAtDesc(Long userId);

    Optional<SupportTask> findByIdAndUser_Id(Long id, Long userId);
}
