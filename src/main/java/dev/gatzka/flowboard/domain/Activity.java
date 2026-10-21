package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One line in a board's activity feed, e.g. "Mara moved "Dark mode" to Done". */
@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long boardId;

    /** The card this entry is about, if any. Kept after the card is deleted. */
    private Long cardId;

    @Column(nullable = false, length = 60)
    private String actor;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(nullable = false)
    private Instant occurredAt;

    protected Activity() {
        // JPA
    }

    public Activity(long boardId, Long cardId, String actor, String message, Instant occurredAt) {
        this.boardId = boardId;
        this.cardId = cardId;
        this.actor = requireNonNull(actor);
        this.message = requireNonNull(message);
        this.occurredAt = requireNonNull(occurredAt);
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getCardId() {
        return cardId;
    }

    public String getActor() {
        return actor;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
