package dev.gatzka.flowboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.Account;
import dev.gatzka.flowboard.domain.ApiTokenSnapshot;
import dev.gatzka.flowboard.repository.ApiTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApiTokenServiceTest {

    @Autowired
    private ApiTokenService tokens;

    @Autowired
    private AccountService accounts;

    @Autowired
    private ApiTokenRepository repository;

    @BeforeEach
    void account() {
        repository.deleteAll();
        if (accounts.find("tokenholder").isEmpty()) {
            accounts.register("tokenholder", "Token Holder", "longenough");
        }
    }

    @Test
    void issuesTokensThatAuthenticateUntilRevoked() {
        ApiTokenService.Issued issued = tokens.issue("TokenHolder", "  CI pipeline ");

        assertThat(issued.secret()).startsWith("fb_").hasSize(35);
        assertThat(issued.token().name()).isEqualTo("CI pipeline");
        assertThat(issued.token().prefix()).isEqualTo(issued.secret().substring(0, 9));
        assertThat(issued.token().lastUsedAt()).isNull();
        assertThat(repository.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(issued.secret());
            assertThat(stored.getUsername()).isEqualTo("tokenholder");
        });

        assertThat(tokens.authenticate(issued.secret())).map(Account::getUsername).hasValue("tokenholder");
        assertThat(tokens.forUser("tokenholder")).singleElement().extracting(ApiTokenSnapshot::lastUsedAt).isNotNull();
        assertThat(tokens.authenticate("fb_not-a-real-token")).isEmpty();
        assertThat(tokens.authenticate("robots-only")).isEmpty();
        assertThat(tokens.authenticate(null)).isEmpty();

        assertThat(tokens.revoke(issued.token().id(), "somebody-else")).isFalse();
        assertThat(tokens.revoke(issued.token().id(), "tokenholder")).isTrue();
        assertThat(tokens.authenticate(issued.secret())).isEmpty();
        assertThat(tokens.forUser("tokenholder")).isEmpty();
    }

    @Test
    void validatesNamesAndAccounts() {
        assertThatThrownBy(() -> tokens.issue("tokenholder", " ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> tokens.issue("nobody", "x")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("account");
        assertThat(tokens.forUser("nobody")).isEmpty();
        assertThat(ApiTokenService.hash("a")).isEqualTo(ApiTokenService.hash("a")).isNotEqualTo(ApiTokenService.hash("b"));
    }
}
