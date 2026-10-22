package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Somebody who wants to hear about changes to a card. The watcher is the lower-cased display name. */
@Entity
@Table(name = "card_watchers")
public class CardWatcher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(nullable = false, length = 60)
    private String watcher;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CardWatcher() {
    }

    public CardWatcher(long cardId, String watcher, Instant createdAt) {
        this.cardId = cardId;
        this.watcher = Notification.key(watcher);
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public long getCardId() {
        return cardId;
    }

    public String getWatcher() {
        return watcher;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
