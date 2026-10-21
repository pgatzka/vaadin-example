package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A remark somebody left on a card. Owned by the card; lives and dies with it. */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(nullable = false, length = 60)
    private String author;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Comment() {
        // JPA
    }

    Comment(Card card, String author, String text, Instant createdAt) {
        this.card = requireNonNull(card);
        this.author = Text.require(author, "Author");
        this.text = Text.require(text, "Comment");
        this.createdAt = requireNonNull(createdAt);
    }

    public Long getId() {
        return id;
    }

    public Card getCard() {
        return card;
    }

    public String getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
