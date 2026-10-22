package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.WorkEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkEntryRepository extends JpaRepository<WorkEntry, Long> {

    List<WorkEntry> findAllByCardIdOrderByLoggedAtDesc(long cardId);

    long countByPersonAndNoteAndLoggedAtAfter(String person, String note, java.time.Instant since);
}
