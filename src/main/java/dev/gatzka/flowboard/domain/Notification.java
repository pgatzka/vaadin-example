package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

/** Something a person should know about: a mention, an assignment, a comment on their card. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lower-cased display name, so lookups are case-insensitive. */
    @Column(nullable = false, length = 60)
    private String recipient;

    @Column(nullable = false, length = 60)
    private String actor;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(nullable = false)
    private Long boardId;

    private Long cardId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;

    protected Notification() {
        // JPA
    }

    public Notification(String recipient, String actor, String message, long boardId, Long cardId, Instant createdAt) {
        this.recipient = key(recipient);
        this.actor = Text.require(actor, "Actor");
        this.message = Text.require(message, "Message");
        this.boardId = boardId;
        this.cardId = cardId;
        this.createdAt = requireNonNull(createdAt);
    }

    /** The lookup key for a display name. */
    public static String key(String name) {
        return Text.require(name, "Recipient").toLowerCase(Locale.ROOT);
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = requireNonNull(now);
        }
    }

    public boolean isUnread() {
        return readAt == null;
    }

    public Long getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getActor() {
        return actor;
    }

    public String getMessage() {
        return message;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getCardId() {
        return cardId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
