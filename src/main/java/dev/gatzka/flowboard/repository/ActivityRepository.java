package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Activity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findTop50ByBoardIdOrderByOccurredAtDesc(long boardId);

    List<Activity> findTop50ByCardIdOrderByOccurredAtDesc(long cardId);

    List<Activity> findTop12ByOrderByOccurredAtDesc();

    long countByBoardIdAndOccurredAtAfterAndActorNot(long boardId, Instant since, String actor);

    void deleteAllByBoardId(long boardId);
}
