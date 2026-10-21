package dev.gatzka.flowboard.ui;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import com.vaadin.flow.spring.security.AuthenticationContext;
import dev.gatzka.flowboard.security.AccountService;
import dev.gatzka.flowboard.service.Participant;
import java.io.Serializable;

/**
 * Per-browser-session user state. The display name comes from the signed-in account and can be
 * changed at any time; a principal without an account (as in tests) is shown by its login.
 */
@SpringComponent
@VaadinSessionScope
public class UserSession implements Serializable {

    static final int MAX_NAME_LENGTH = AccountService.MAX_NAME_LENGTH;

    private final transient AuthenticationContext authentication;
    private final transient AccountService accounts;

    private String displayName;
    /** {@code null} until the user or their browser preference decided. */
    private Boolean darkMode;

    public UserSession(AuthenticationContext authentication, AccountService accounts) {
        this.authentication = authentication;
        this.accounts = accounts;
    }

    public String getDisplayName() {
        if (displayName == null) {
            displayName = authentication.getPrincipalName().map(accounts::displayNameOf).orElseGet(UserNames::random);
        }
        return displayName;
    }

    public void setDisplayName(String displayName) {
        String cleaned = displayName == null ? "" : displayName.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Display name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        this.displayName = cleaned;
        authentication.getPrincipalName().ifPresent(login -> accounts.rename(login, cleaned));
    }

    /** The login of the signed-in account, or {@code null} when nobody is signed in. */
    public String login() {
        return authentication.getPrincipalName().orElse(null);
    }

    public Participant participant() {
        return Participant.named(getDisplayName());
    }

    /** Whether dark mode is on; false until a preference has been established. */
    public boolean isDarkMode() {
        return Boolean.TRUE.equals(darkMode);
    }

    /** True once either the user toggled the theme or the browser preference was applied. */
    public boolean hasThemePreference() {
        return darkMode != null;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    public void signOut() {
        authentication.logout();
    }
}
