package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "boards")
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Token of the public read-only link, or {@code null} while the board is not shared. */
    @Column(name = "share_token", length = 40)
    private String shareToken;

    protected Board() {
        // JPA
    }

    public Board(BoardDraft draft, Instant createdAt) {
        this.createdAt = requireNonNull(createdAt);
        update(draft);
    }

    public void update(BoardDraft draft) {
        this.name = draft.name();
        this.description = draft.description();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getShareToken() {
        return shareToken;
    }

    public boolean isShared() {
        return shareToken != null;
    }

    public void share(String token) {
        this.shareToken = requireNonNull(token);
    }

    public void unshare() {
        this.shareToken = null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
