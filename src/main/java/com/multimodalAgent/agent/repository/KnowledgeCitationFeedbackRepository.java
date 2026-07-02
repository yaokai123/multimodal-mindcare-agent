package com.multimodalAgent.agent.repository;

import com.multimodalAgent.agent.domain.KnowledgeCitationFeedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeCitationFeedbackRepository extends JpaRepository<KnowledgeCitationFeedback, Long> {

    List<KnowledgeCitationFeedback> findTop20BySourceOrderByCreatedAtDesc(String source);

    long countByChunkId(Long chunkId);
}
