package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Card;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {

    /** All cards of a board with their tags loaded in the same query. */
    @EntityGraph(attributePaths = "tags")
    List<Card> findAllByLaneBoardIdOrderByPositionAsc(long boardId);

    List<Card> findAllByLaneIdOrderByPositionAsc(long laneId);

    long countByLaneId(long laneId);

    long countByLaneBoardId(long boardId);

    long countByLaneBoardIdAndLaneDoneFalseAndDueDateBefore(long boardId, LocalDate date);

    void deleteAllByLaneId(long laneId);

    void deleteAllByLaneBoardId(long boardId);
}
