package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Card;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Cards on the board are "live"; archived cards keep their lane but have an archive timestamp.
 * Queries about board content only consider live cards.
 */
public interface CardRepository extends JpaRepository<Card, Long> {

    /** All live cards of a board with tags, checklists and comments loaded in the same query. */
    @EntityGraph(attributePaths = {"tags", "checklist", "comments"})
    List<Card> findAllByLaneBoardIdAndArchivedAtIsNullOrderByPositionAsc(long boardId);

    @EntityGraph(attributePaths = {"tags", "checklist", "comments"})
    List<Card> findAllByLaneBoardIdAndArchivedAtIsNotNullOrderByArchivedAtDesc(long boardId);

    List<Card> findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(long laneId);

    long countByLaneIdAndArchivedAtIsNull(long laneId);

    long countByLaneBoardIdAndArchivedAtIsNull(long boardId);

    long countByLaneBoardIdAndArchivedAtIsNotNull(long boardId);

    long countByLaneBoardIdAndLaneDoneFalseAndArchivedAtIsNullAndDueDateBefore(long boardId, LocalDate date);

    @EntityGraph(attributePaths = {"tags", "checklist", "comments", "lane", "lane.board"})
    List<Card> findAllByAssigneeIgnoreCaseAndArchivedAtIsNull(String assignee);

    @EntityGraph(attributePaths = {"tags", "checklist", "comments", "lane", "lane.board"})
    List<Card> findAllByArchivedAtIsNull();

    void deleteAllByLaneId(long laneId);

    void deleteAllByLaneBoardId(long boardId);
}
