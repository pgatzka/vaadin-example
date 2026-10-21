package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.BoardVisit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardVisitRepository extends JpaRepository<BoardVisit, Long> {

    Optional<BoardVisit> findByUsernameAndBoardId(String username, long boardId);

    List<BoardVisit> findAllByUsername(String username);

    void deleteAllByBoardId(long boardId);
}
