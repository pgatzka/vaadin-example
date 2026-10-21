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

/** One line of a card's checklist. Owned by the card; lives and dies with it. */
@Entity
@Table(name = "checklist_items")
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "sort_order", nullable = false)
    private int position;

    protected ChecklistItem() {
        // JPA
    }

    ChecklistItem(Card card, String text, int position) {
        this.card = requireNonNull(card);
        this.text = Text.require(text, "Checklist item");
        this.position = position;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    void setPosition(int position) {
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Card getCard() {
        return card;
    }

    public String getText() {
        return text;
    }

    public boolean isDone() {
        return done;
    }

    public int getPosition() {
        return position;
    }
}
