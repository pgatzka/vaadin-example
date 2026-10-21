package dev.gatzka.flowboard.ui.support;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.notification.NotificationVariant;

/** Short, unobtrusive status messages in the bottom-left corner. */
public final class Toasts {

    private static final int DURATION_MS = 3500;
    private static final int UNDO_DURATION_MS = 7000;

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

    /** A message with an Undo button; the button closes the toast and runs the given action. */
    public static Notification undoable(String text, Runnable undo) {
        Notification notification = new Notification();
        notification.setDuration(UNDO_DURATION_MS);
        notification.setPosition(Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        notification.addClassName("toast--undoable");
        Button button = new Button("Undo", event -> {
            notification.close();
            undo.run();
        });
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.addClassName("toast__undo");
        HorizontalLayout layout = new HorizontalLayout(new Span(text), button);
        layout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        notification.add(layout);
        notification.open();
        return notification;
    }

    private static void show(String text, NotificationVariant variant) {
        Notification notification = Notification.show(text, DURATION_MS, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(variant);
    }
}
