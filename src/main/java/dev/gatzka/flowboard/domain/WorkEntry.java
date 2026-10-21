package dev.gatzka.flowboard.domain;

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

/** Time somebody spent on a card, logged by hand or by stopping a timer. */
@Entity
@Table(name = "work_entries")
public class WorkEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(nullable = false, length = 60)
    private String person;

    @Column(nullable = false)
    private int minutes;

    @Column(length = 200)
    private String note;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    protected WorkEntry() {
    }

    WorkEntry(Card card, String person, int minutes, String note, Instant loggedAt) {
        this.card = card;
        this.person = person;
        this.minutes = minutes;
        this.note = note;
        this.loggedAt = loggedAt;
    }

    public Long getId() {
        return id;
    }

    public Card getCard() {
        return card;
    }

    public String getPerson() {
        return person;
    }

    public int getMinutes() {
        return minutes;
    }

    public String getNote() {
        return note;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }
}
