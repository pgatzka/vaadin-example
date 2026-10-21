package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.WebhookDelivery;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findAllByWebhookIdOrderByAttemptedAtDesc(long webhookId);

    Optional<WebhookDelivery> findFirstByWebhookIdOrderByAttemptedAtDesc(long webhookId);
}
