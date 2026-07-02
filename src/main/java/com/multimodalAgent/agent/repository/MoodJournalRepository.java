package com.multimodalAgent.agent.repository;

import com.multimodalAgent.agent.domain.MoodJournalEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodJournalRepository extends JpaRepository<MoodJournalEntry, Long> {

    List<MoodJournalEntry> findTop14ByUser_IdOrderByCreatedAtDesc(Long userId);

    List<MoodJournalEntry> findByUser_IdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, Instant createdAfter);
}
