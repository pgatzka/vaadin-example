package dev.gatzka.flowboard.ui;

import com.vaadin.flow.component.icon.VaadinIcon;

/** Something the command palette can do: a label to match against and an action to run. */
public record Command(String label, VaadinIcon icon, Runnable action) {

    public boolean matches(String query) {
        return query == null || query.isBlank() || label.toLowerCase().contains(query.strip().toLowerCase());
    }
}
