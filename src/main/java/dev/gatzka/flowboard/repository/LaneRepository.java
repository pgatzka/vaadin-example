package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Lane;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaneRepository extends JpaRepository<Lane, Long> {

    List<Lane> findAllByBoardIdOrderByPositionAsc(long boardId);

    long countByBoardId(long boardId);

    void deleteAllByBoardId(long boardId);
}
