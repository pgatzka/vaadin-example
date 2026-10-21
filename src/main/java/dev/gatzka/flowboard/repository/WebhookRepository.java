package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Webhook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findAllByBoardIdOrderByCreatedAtAsc(long boardId);

    List<Webhook> findAllByBoardIdAndEnabledTrue(long boardId);

    void deleteAllByBoardId(long boardId);
}
