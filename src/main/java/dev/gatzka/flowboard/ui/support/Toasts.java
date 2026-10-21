package dev.gatzka.flowboard.ui.support;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/** Short, unobtrusive status messages in the bottom-left corner. */
public final class Toasts {

    private static final int DURATION_MS = 3500;

    private Toasts() {
    }

    public static void info(String text) {
        show(text, NotificationVariant.LUMO_CONTRAST);
    }

    public static void success(String text) {
        show(text, NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String text) {
        show(text, NotificationVariant.LUMO_ERROR);
    }

    private static void show(String text, NotificationVariant variant) {
        Notification notification = Notification.show(text, DURATION_MS, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(variant);
    }
}
