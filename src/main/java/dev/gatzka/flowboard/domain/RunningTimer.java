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

    /** Set for a focus session: the timer stops itself after this many minutes. */
    @Column(name = "focus_minutes")
    private Integer focusMinutes;

    protected RunningTimer() {
    }

    public RunningTimer(String person, Card card, Instant startedAt) {
        this(person, card, startedAt, null);
    }

    public RunningTimer(String person, Card card, Instant startedAt, Integer focusMinutes) {
        this.person = person;
        this.card = card;
        this.startedAt = startedAt;
        this.focusMinutes = focusMinutes;
    }

    public Integer getFocusMinutes() {
        return focusMinutes;
    }

    public boolean isFocusSession() {
        return focusMinutes != null;
    }

    /** When a focus session ends; null for an open-ended timer. */
    public Instant getEndsAt() {
        return focusMinutes == null ? null : startedAt.plusSeconds(focusMinutes * 60L);
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
