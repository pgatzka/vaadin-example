package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Notification;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop30ByRecipientOrderByCreatedAtDesc(String recipient);

    List<Notification> findAllByRecipientAndReadAtIsNull(String recipient);

    long countByRecipientAndReadAtIsNull(String recipient);

    void deleteAllByBoardId(long boardId);

    boolean existsByRecipientAndCardIdAndActorAndCreatedAtAfter(String recipient, long cardId, String actor, Instant since);
}
