package com.multimodalAgent.agent.repository;

import com.multimodalAgent.agent.domain.StudentCaseNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCaseNoteRepository extends JpaRepository<StudentCaseNote, Long> {

    List<StudentCaseNote> findTop20ByUser_IdOrderByCreatedAtDesc(Long userId);
}
