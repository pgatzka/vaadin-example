package dev.gatzka.flowboard.security;

import dev.gatzka.flowboard.domain.Account;
import dev.gatzka.flowboard.domain.ApiToken;
import dev.gatzka.flowboard.domain.ApiTokenSnapshot;
import dev.gatzka.flowboard.repository.ApiTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal access tokens for the API. A token is {@code fb_} followed by 32 random characters; only its SHA-256 is
 * stored, so a leaked database does not leak tokens, and the secret can be shown to its owner exactly once.
 */
@Service
@Transactional
public class ApiTokenService {

    public static final String PREFIX = "fb_";
    public static final int MAX_TOKENS = 20;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_LENGTH = 32;

    /** A freshly issued token: the only moment the secret exists in the clear. */
    public record Issued(ApiTokenSnapshot token, String secret) {
    }

    private final ApiTokenRepository tokens;
    private final AccountService accounts;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    ApiTokenService(ApiTokenRepository tokens, AccountService accounts, Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.clock = clock;
    }

    public Issued issue(String username, String name) {
        Account account = accounts.find(username).orElseThrow(() -> new IllegalArgumentException("No such account"));
        String label = name == null ? "" : name.strip();
        if (label.isEmpty() || label.length() > ApiToken.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Give the token a name of 1 to " + ApiToken.MAX_NAME_LENGTH + " characters");
        }
        if (tokens.findAllByUsernameOrderByCreatedAtDesc(account.getUsername()).size() >= MAX_TOKENS) {
            throw new IllegalArgumentException("You already have " + MAX_TOKENS + " tokens; revoke one first");
        }
        String secret = generate();
        ApiToken token = tokens.save(new ApiToken(account.getUsername(), label, hash(secret), secret.substring(0, PREFIX.length() + 6),
                clock.instant()));
        return new Issued(ApiTokenSnapshot.of(token), secret);
    }

    @Transactional(readOnly = true)
    public List<ApiTokenSnapshot> forUser(String username) {
        return accounts.find(username).map(account -> tokens.findAllByUsernameOrderByCreatedAtDesc(account.getUsername()).stream()
                .map(ApiTokenSnapshot::of).toList()).orElse(List.of());
    }

    /** Deletes the token if it belongs to the user; somebody else's tokens are untouchable. */
    public boolean revoke(long tokenId, String username) {
        Optional<ApiToken> token = accounts.find(username).flatMap(account -> tokens.findByIdAndUsername(tokenId, account.getUsername()));
        token.ifPresent(tokens::delete);
        return token.isPresent();
    }

    /** The account behind a presented secret, if it is a live token; records the use. */
    public Optional<Account> authenticate(String secret) {
        if (secret == null || !secret.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash(secret)).flatMap(token -> {
            token.touch(clock.instant());
            return accounts.find(token.getUsername());
        });
    }

    private String generate() {
        StringBuilder secret = new StringBuilder(PREFIX);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            secret.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return secret.toString();
    }

    static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
