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

/** A timer somebody started on a card; at most one per person. */
@Entity
@Table(name = "running_timers")
public class RunningTimer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String person;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    protected RunningTimer() {
    }

    public RunningTimer(String person, Card card, Instant startedAt) {
        this.person = person;
        this.card = card;
        this.startedAt = startedAt;
    }

    public Long getId() {
        return id;
    }

    public String getPerson() {
        return person;
    }

    public Card getCard() {
        return card;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
