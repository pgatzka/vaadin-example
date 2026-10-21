package dev.gatzka.flowboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void registersWithLowerCasedLoginAndHashedPassword() {
        Account account = accounts.register("  Lena.K ", " Lena Kovač ", "correct horse");

        assertThat(account.getUsername()).isEqualTo("lena.k");
        assertThat(account.getDisplayName()).isEqualTo("Lena Kovač");
        assertThat(account.getPasswordHash()).doesNotContain("correct horse");
        assertThat(passwords.matches("correct horse", account.getPasswordHash())).isTrue();

        UserDetails details = accounts.loadUserByUsername("LENA.K");
        assertThat(details.getUsername()).isEqualTo("lena.k");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(accounts.displayNameOf("lena.k")).isEqualTo("Lena Kovač");
        assertThat(accounts.displayNameOf("nobody")).isEqualTo("nobody");
    }

    @Test
    void rejectsBadInput() {
        accounts.register("taken", "Taken", "longenough");
        assertThatThrownBy(() -> accounts.register("taken", "Again", "longenough"))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("taken");
        assertThatThrownBy(() -> accounts.register("no spaces", "X", "longenough"))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Usernames");
        assertThatThrownBy(() -> accounts.register("ab", "X", "longenough"))
                .isInstanceOf(RegistrationException.class);
        assertThatThrownBy(() -> accounts.register("fine", "   ", "longenough"))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Display names");
        assertThatThrownBy(() -> accounts.register("fine", "Fine", "short"))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Passwords");
        assertThatThrownBy(() -> accounts.loadUserByUsername("missing")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void renamesExistingAccountsOnly() {
        accounts.register("renamed", "Before", "longenough");
        accounts.rename("renamed", "After");
        accounts.rename("ghost", "Nothing");

        assertThat(accounts.displayNameOf("renamed")).isEqualTo("After");
        assertThat(accounts.find("ghost")).isEmpty();
    }
}
