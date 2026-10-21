package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** When a user last opened a board; what "new since your last visit" is measured against. */
@Entity
@Table(name = "board_visits")
public class BoardVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "visited_at", nullable = false)
    private Instant visitedAt;

    protected BoardVisit() {
    }

    public BoardVisit(String username, long boardId, Instant visitedAt) {
        this.username = username;
        this.boardId = boardId;
        this.visitedAt = visitedAt;
    }

    public String getUsername() {
        return username;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Instant getVisitedAt() {
        return visitedAt;
    }

    public void visitedAt(Instant visitedAt) {
        this.visitedAt = visitedAt;
    }
}
