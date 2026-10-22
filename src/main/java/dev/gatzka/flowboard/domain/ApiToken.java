package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A personal access token for the API. Only a hash is stored; the secret is shown once when it is issued. */
@Entity
@Table(name = "api_tokens")
public class ApiToken {

    public static final int MAX_NAME_LENGTH = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 12)
    private String prefix;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ApiToken() {
    }

    public ApiToken(String username, String name, String tokenHash, String prefix, Instant createdAt) {
        this.username = username;
        this.name = name;
        this.tokenHash = tokenHash;
        this.prefix = prefix;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getPrefix() {
        return prefix;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
    }
}
