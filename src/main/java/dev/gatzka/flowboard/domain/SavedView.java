package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A named filter on a board, shared by everyone who uses the board. */
@Entity
@Table(name = "saved_views")
public class SavedView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 1000)
    private String filter;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavedView() {
    }

    public SavedView(long boardId, String name, String filter, String createdBy, Instant createdAt) {
        this.boardId = boardId;
        this.name = name;
        this.filter = filter;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getBoardId() {
        return boardId;
    }

    public String getName() {
        return name;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
