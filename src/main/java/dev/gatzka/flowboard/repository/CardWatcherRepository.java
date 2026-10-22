package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.CardWatcher;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CardWatcherRepository extends JpaRepository<CardWatcher, Long> {

    List<CardWatcher> findAllByCardId(long cardId);

    Optional<CardWatcher> findByCardIdAndWatcher(long cardId, String watcher);

    /** Ids of the given person's watched cards on a board. */
    @Query("select w.cardId from CardWatcher w where w.watcher = :watcher and w.cardId in "
            + "(select c.id from Card c where c.lane.board.id = :boardId)")
    List<Long> findCardIdsWatchedOnBoard(long boardId, String watcher);

    void deleteAllByCardId(long cardId);

    @Modifying
    @Query("delete from CardWatcher w where w.cardId in (select c.id from Card c where c.lane.board.id = :boardId)")
    void deleteAllOnBoard(long boardId);
}
