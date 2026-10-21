package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** The number of live cards a lane held at the end of a day. */
@Entity
@Table(name = "lane_daily_counts")
public class LaneDailyCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long boardId;

    @Column(nullable = false)
    private Long laneId;

    @Column(name = "snapshot_day", nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private int cards;

    protected LaneDailyCount() {
        // JPA
    }

    public LaneDailyCount(long boardId, long laneId, LocalDate day, int cards) {
        this.boardId = boardId;
        this.laneId = laneId;
        this.day = requireNonNull(day);
        this.cards = cards;
    }

    public void setCards(int cards) {
        this.cards = cards;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getLaneId() {
        return laneId;
    }

    public LocalDate getDay() {
        return day;
    }

    public int getCards() {
        return cards;
    }
}
