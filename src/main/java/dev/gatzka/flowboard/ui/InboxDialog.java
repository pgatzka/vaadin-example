package dev.gatzka.flowboard.ui;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import dev.gatzka.flowboard.domain.NotificationSnapshot;
import dev.gatzka.flowboard.service.NotificationService;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.DesktopAlerts;
import dev.gatzka.flowboard.ui.board.BoardView;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;

/** The current user's notifications. Opening a notification marks it read and jumps to the card. */
final class InboxDialog extends Dialog {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

    private final NotificationService notificationService;
    private final UserSession session;
    private final Runnable onChanged;
    private final Div list = new Div();
    private final Span empty = new Span(I18n.t("inbox.empty"));

    InboxDialog(NotificationService notificationService, UserSession session, Runnable onChanged) {
        this.notificationService = notificationService;
        this.session = session;
        this.onChanged = onChanged;
        addClassName("activity-sheet");
        setHeaderTitle(I18n.t("inbox.title"));
        setModal(false);
        setDraggable(false);
        setCloseOnOutsideClick(true);

        Button markAll = new Button(I18n.t("inbox.markAllRead"), event -> {
            notificationService.markAllRead(session.getDisplayName());
            refresh();
            onChanged.run();
        });
        markAll.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel(I18n.t("inbox.close"));
        Button alerts = new Button(VaadinIcon.BELL.create(), event -> getUI().ifPresent(ui -> DesktopAlerts.requestPermission(ui, outcome -> {
            switch (outcome) {
                case "granted" -> Toasts.success(I18n.t("inbox.alertsOn"));
                case "unsupported" -> Toasts.error(I18n.t("inbox.alertsUnsupported"));
                default -> Toasts.info(I18n.t("inbox.alertsDenied"));
            }
        })));
        alerts.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        alerts.setAriaLabel(I18n.t("inbox.alerts"));
        alerts.setTooltipText(I18n.t("inbox.alertsTooltip"));
        alerts.setId("desktop-alerts");
        getHeader().add(alerts, markAll, close);

        list.addClassName("inbox__list");
        empty.addClassName("activity-sheet__empty");
        add(list, empty);
    }

    void refresh() {
        List<NotificationSnapshot> items = notificationService.recent(session.getDisplayName());
        list.removeAll();
        items.forEach(item -> list.add(row(item)));
        empty.setVisible(items.isEmpty());
    }

    private Div row(NotificationSnapshot item) {
        Span text = new Span(item.actor() + " " + item.message());
        text.addClassName("inbox__text");
        Span when = new Span(WHEN.withLocale(I18n.locale()).format(item.createdAt().atZone(ZoneId.systemDefault())));
        when.addClassName("inbox__when");
        Div row = new Div(text, when);
        row.addClassName("inbox__row");
        if (item.unread()) {
            row.addClassName("inbox__row--unread");
        }
        if (item.cardId() != null) {
            RouterLink link = new RouterLink(I18n.t("inbox.openCard"), BoardView.class, new RouteParameters(Map.of(
                    BoardView.BOARD_ID, Long.toString(item.boardId()), BoardView.CARD_ID, Long.toString(item.cardId()))));
            link.addClassName("inbox__link");
            link.getElement().addEventListener("click", event -> {
                notificationService.markRead(item.id());
                onChanged.run();
                close();
            });
            row.add(link);
        }
        return row;
    }
}
