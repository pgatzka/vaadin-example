package dev.gatzka.flowboard.security;

import dev.gatzka.flowboard.domain.Account;
import dev.gatzka.flowboard.repository.AccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and lookup of accounts; also what Spring Security asks when someone signs in. */
@Service
@Transactional
public class AccountService implements UserDetailsService {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_NAME_LENGTH = 40;
    static final String ROLE_USER = "USER";
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,39}");

    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Clock clock;

    AccountService(AccountRepository accounts, PasswordEncoder passwords, Clock clock) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.clock = clock;
    }

    /** Creates an account; the username is normalised to lower case. */
    public Account register(String username, String displayName, String password) {
        String login = normalise(username);
        if (!USERNAME.matcher(login).matches()) {
            throw new RegistrationException("Usernames are 3 to 40 characters: letters, digits, dots, dashes or underscores");
        }
        String shown = displayName == null ? "" : displayName.strip();
        if (shown.isEmpty() || shown.length() > MAX_NAME_LENGTH) {
            throw new RegistrationException("Display names are 1 to " + MAX_NAME_LENGTH + " characters");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new RegistrationException("Passwords need at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (accounts.existsByUsername(login)) {
            throw new RegistrationException("That username is taken");
        }
        return accounts.save(new Account(login, shown, passwords.encode(password), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Optional<Account> find(String username) {
        return username == null ? Optional.empty() : accounts.findByUsername(normalise(username));
    }

    /** Every login, alphabetically, e.g. for picking members. */
    @Transactional(readOnly = true)
    public List<String> usernames() {
        return accounts.findAll().stream().map(Account::getUsername).sorted().toList();
    }

    /** The name others see for a login, or the login itself when no account exists for it. */
    @Transactional(readOnly = true)
    public String displayNameOf(String username) {
        return find(username).map(Account::getDisplayName).orElse(username);
    }

    /** Changes the display name of an existing account; unknown logins are left alone. */
    public void rename(String username, String displayName) {
        find(username).ifPresent(account -> account.rename(displayName));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        Account account = find(username).orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return User.withUsername(account.getUsername()).password(account.getPasswordHash()).roles(ROLE_USER).build();
    }

    private static String normalise(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
