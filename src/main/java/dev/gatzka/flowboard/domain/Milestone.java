package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** A goal on a board that cards work towards, with an optional target date. */
@Entity
@Table(name = "milestones")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(nullable = false, length = MilestoneDraft.MAX_NAME)
    private String name;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Milestone() {
        // JPA
    }

    public Milestone(long boardId, MilestoneDraft draft, Instant createdAt) {
        this.boardId = boardId;
        this.createdAt = requireNonNull(createdAt);
        update(draft);
    }

    public void update(MilestoneDraft draft) {
        this.name = draft.name();
        this.targetDate = draft.targetDate();
    }

    public Long getId() {
        return id;
    }

    public long getBoardId() {
        return boardId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
