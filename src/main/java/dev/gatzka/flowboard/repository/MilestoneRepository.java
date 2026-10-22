package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Milestone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findAllByBoardId(long boardId);

    void deleteAllByBoardId(long boardId);
}
