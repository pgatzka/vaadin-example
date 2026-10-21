package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.SavedView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedViewRepository extends JpaRepository<SavedView, Long> {

    List<SavedView> findAllByBoardIdOrderByNameAsc(long boardId);

    Optional<SavedView> findByBoardIdAndNameIgnoreCase(long boardId, String name);

    void deleteAllByBoardId(long boardId);
}
