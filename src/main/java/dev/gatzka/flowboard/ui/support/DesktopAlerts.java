package dev.gatzka.flowboard.ui.support;

import com.vaadin.flow.component.UI;
import java.util.function.Consumer;

/**
 * Desktop notifications through the browser's Notification API. They are only shown while the
 * tab is in the background; in the foreground the inbox badge already tells the story.
 */
public final class DesktopAlerts {

    private DesktopAlerts() {
    }

    /** Asks the browser for permission and reports the outcome: {@code granted}, {@code denied}, {@code default} or {@code unsupported}. */
    public static void requestPermission(UI ui, Consumer<String> outcome) {
        ui.getPage().executeJs("""
                if (!('Notification' in window)) return 'unsupported';
                return Notification.requestPermission();
                """).then(String.class, outcome::accept);
    }

    public static void show(UI ui, String title, String body) {
        ui.getPage().executeJs("""
                if ('Notification' in window && Notification.permission === 'granted' && document.visibilityState !== 'visible') {
                  new Notification($0, { body: $1, tag: 'flowboard-inbox' });
                }
                """, title, body);
    }
}
