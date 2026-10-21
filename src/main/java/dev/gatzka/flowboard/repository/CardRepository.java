package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Card;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Cards on the board are "live"; archived cards keep their lane but have an archive timestamp.
 * Queries about board content only consider live cards.
 */
public interface CardRepository extends JpaRepository<Card, Long> {

    /** All live cards of a board with tags, checklists and comments loaded in the same query. */
    @EntityGraph(attributePaths = {"tags", "checklist", "comments", "voters", "blockers"})
    List<Card> findAllByLaneBoardIdAndArchivedAtIsNullOrderByPositionAsc(long boardId);

    @EntityGraph(attributePaths = {"tags", "checklist", "comments", "voters", "blockers"})
    List<Card> findAllByLaneBoardIdAndArchivedAtIsNotNullOrderByArchivedAtDesc(long boardId);

    List<Card> findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(long laneId);

    long countByLaneIdAndArchivedAtIsNull(long laneId);

    long countByLaneBoardIdAndArchivedAtIsNull(long boardId);

    long countByLaneBoardIdAndArchivedAtIsNotNull(long boardId);

    long countByLaneBoardIdAndLaneDoneFalseAndArchivedAtIsNullAndDueDateBefore(long boardId, LocalDate date);

    @EntityGraph(attributePaths = {"tags", "checklist", "comments", "voters", "blockers", "lane", "lane.board"})
    List<Card> findAllByAssigneeIgnoreCaseAndArchivedAtIsNull(String assignee);

    @EntityGraph(attributePaths = {"tags", "checklist", "comments", "voters", "blockers", "lane", "lane.board"})
    List<Card> findAllByArchivedAtIsNull();

    List<Card> findAllByLaneId(long laneId);

    /** Cards that list the given card as a blocker. */
    @Query("select c from Card c join c.blockers b where b.id = :blockerId")
    List<Card> findAllBlockedBy(long blockerId);

    /** Removes every dependency link of a board's cards, ahead of deleting the cards. */
    @Modifying
    @Query(value = "delete from card_blockers where card_id in (select c.id from cards c join lanes l on c.lane_id = l.id where l.board_id = :boardId)",
            nativeQuery = true)
    void clearBlockersOfBoard(long boardId);

    void deleteAllByLaneId(long laneId);

    void deleteAllByLaneBoardId(long boardId);
}
