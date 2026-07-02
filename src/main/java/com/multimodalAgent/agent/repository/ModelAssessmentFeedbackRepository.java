package com.multimodalAgent.agent.repository;

import com.multimodalAgent.agent.domain.ModelAssessmentFeedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelAssessmentFeedbackRepository extends JpaRepository<ModelAssessmentFeedback, Long> {

    List<ModelAssessmentFeedback> findTop20ByReportIdOrderByCreatedAtDesc(Long reportId);
}
