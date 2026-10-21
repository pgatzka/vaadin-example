package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.CardTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardTemplateRepository extends JpaRepository<CardTemplate, Long> {

    List<CardTemplate> findAllByBoardIdOrderByNameAsc(long boardId);

    Optional<CardTemplate> findByBoardIdAndNameIgnoreCase(long boardId, String name);

    void deleteAllByBoardId(long boardId);
}
