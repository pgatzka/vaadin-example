package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An account allowed into a members-only board. */
@Entity
@Table(name = "board_members")
public class BoardMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected BoardMember() {
    }

    public BoardMember(long boardId, String username, Instant addedAt) {
        this.boardId = boardId;
        this.username = username;
        this.addedAt = addedAt;
    }

    public Long getBoardId() {
        return boardId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
