package dev.gatzka.flowboard.ui;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import dev.gatzka.flowboard.service.Participant;
import java.io.Serializable;

/**
 * Per-browser-session user state. There is no login: visitors get a generated name they can
 * change at any time, which is all that is needed for presence and the activity feed.
 */
@SpringComponent
@VaadinSessionScope
public class UserSession implements Serializable {

    static final int MAX_NAME_LENGTH = 40;

    private String displayName = UserNames.random();
    /** {@code null} until the user or their browser preference decided. */
    private Boolean darkMode;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        String cleaned = displayName == null ? "" : displayName.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Display name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        this.displayName = cleaned;
    }

    public Participant participant() {
        return Participant.named(displayName);
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
}
