package dev.gatzka.flowboard.ui.support;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.server.Command;

/** Runs UI updates triggered from background threads, tolerating a UI that went away meanwhile. */
public final class UiAccess {

    private UiAccess() {
    }

    public static void run(UI ui, Command command) {
        try {
            ui.access(command);
        } catch (UIDetachedException ignored) {
            // The browser tab was closed between the event and this update; nothing to show.
        }
    }
}
