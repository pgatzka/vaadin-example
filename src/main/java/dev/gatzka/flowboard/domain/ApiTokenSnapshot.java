package dev.gatzka.flowboard.domain;

import java.time.Instant;

/** What the owner sees of a token: never the secret, only its first characters. */
public record ApiTokenSnapshot(long id, String name, String prefix, Instant createdAt, Instant lastUsedAt) {

    public static ApiTokenSnapshot of(ApiToken token) {
        return new ApiTokenSnapshot(token.getId(), token.getName(), token.getPrefix(), token.getCreatedAt(), token.getLastUsedAt());
    }
}
