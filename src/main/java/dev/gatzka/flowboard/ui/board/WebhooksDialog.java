package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.WebhookFormat;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.ui.support.Durations;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.webhook.WebhookDispatcher;
import dev.gatzka.flowboard.webhook.WebhookService;
import dev.gatzka.flowboard.webhook.WebhookSnapshot;
import java.time.Clock;
import java.util.function.Supplier;

/** Manages the webhooks of a board: add, pause, test, inspect deliveries, remove. */
final class WebhooksDialog extends Dialog {

    private final BoardSnapshot board;
    private final WebhookService webhooks;
    private final WebhookDispatcher dispatcher;
    private final Supplier<String> actor;
    private final Clock clock;
    private final Div list = new Div();
    private final TextField url = new TextField("Endpoint URL");
    private final PasswordField secret = new PasswordField("Secret (optional)");
    private final Select<WebhookFormat> format = new Select<>();

    WebhooksDialog(BoardSnapshot board, WebhookService webhooks, WebhookDispatcher dispatcher, Supplier<String> actor, Clock clock) {
        this.board = board;
        this.webhooks = webhooks;
        this.dispatcher = dispatcher;
        this.actor = actor;
        this.clock = clock;
        setHeaderTitle("Webhooks for \"" + board.name() + "\"");
        setWidth("min(640px, 95vw)");
        addClassName("webhooks-dialog");

        Span hint = new Span("Every change on this board is posted to each endpoint: as Flowboard JSON for your own "
                + "integrations, or as a ready-made message for a Slack or Discord incoming webhook. "
                + "Set a secret and the request carries an HMAC-SHA256 signature you can verify.");
        hint.addClassName("share-dialog__hint");

        list.addClassName("webhooks__list");

        url.setPlaceholder("https://example.com/hooks/flowboard");
        url.setWidthFull();
        url.setClearButtonVisible(true);
        secret.setWidthFull();
        secret.setClearButtonVisible(true);
        format.setLabel("Format");
        format.setId("webhook-format");
        format.setItems(WebhookFormat.values());
        format.setItemLabelGenerator(WebhookFormat::label);
        format.setValue(WebhookFormat.FLOWBOARD);
        format.setWidthFull();
        url.addValueChangeListener(event -> guessFormat(event.getValue()));
        Button add = new Button("Add webhook", VaadinIcon.PLUS.create(), event -> add());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.setId("add-webhook");
        Div form = new Div(url, format, secret, add);
        form.addClassName("webhooks__form");

        add(hint, list, form);
        getFooter().add(new Button("Close", event -> close()));
        refresh();
    }

    void refresh() {
        list.removeAll();
        var hooks = webhooks.forBoard(board.id());
        if (hooks.isEmpty()) {
            Span empty = new Span("No webhooks yet.");
            empty.addClassName("insights__hint");
            list.add(empty);
            return;
        }
        hooks.forEach(hook -> list.add(row(hook)));
    }

    private Div row(WebhookSnapshot hook) {
        Span address = new Span(hook.url());
        address.addClassName("webhooks__url");
        Badge status = statusBadge(hook);
        Badge kind = new Badge(hook.format().label());
        kind.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
        kind.addClassName("webhooks__format");

        Checkbox enabled = new Checkbox("Enabled", hook.enabled());
        enabled.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                webhooks.setEnabled(hook.id(), event.getValue(), actor.get());
                refresh();
            }
        });
        Button test = new Button("Send test", event -> {
            dispatcher.sendTest(hook.id(), actor.get());
            Toasts.info("Test event sent; the delivery log updates on refresh");
        });
        test.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Button deliveries = new Button("Deliveries", event -> new DeliveriesDialog(hook, webhooks.deliveries(hook.id()), clock).open());
        deliveries.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Button remove = new Button(VaadinIcon.TRASH.create(), event -> {
            webhooks.remove(hook.id(), actor.get());
            Toasts.info("Webhook removed");
            refresh();
        });
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        remove.setAriaLabel("Remove webhook");
        remove.addClassName("remove-webhook");
        Div actions = new Div(enabled, test, deliveries, remove);
        actions.addClassName("webhooks__actions");

        Div row = new Div(new Div(address, kind, status), actions);
        row.addClassName("webhooks__row");
        row.getElement().setAttribute("data-webhook-id", Long.toString(hook.id()));
        return row;
    }

    private Badge statusBadge(WebhookSnapshot hook) {
        if (!hook.enabled()) {
            Badge paused = new Badge("Paused");
            paused.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            return paused;
        }
        return hook.lastDelivery().map(delivery -> {
            Badge badge = new Badge(delivery.summary() + " · " + Durations.ago(delivery.attemptedAt(), clock.instant()));
            badge.addThemeVariants(BadgeVariant.SMALL, delivery.succeeded() ? BadgeVariant.SUCCESS : BadgeVariant.ERROR);
            badge.addClassName("webhooks__status");
            return badge;
        }).orElseGet(() -> {
            Badge badge = new Badge("Not called yet");
            badge.addThemeVariants(BadgeVariant.SMALL);
            badge.addClassName("webhooks__status");
            return badge;
        });
    }

    private void add() {
        try {
            webhooks.add(board.id(), url.getValue(), secret.getValue(), format.getValue(), actor.get());
        } catch (IllegalArgumentException e) {
            url.setErrorMessage(e.getMessage());
            url.setInvalid(true);
            return;
        }
        url.clear();
        secret.clear();
        format.setValue(WebhookFormat.FLOWBOARD);
        url.setInvalid(false);
        Toasts.success("Webhook added");
        refresh();
    }

    /** Slack and Discord incoming-webhook URLs give their format away. */
    static WebhookFormat guess(String url) {
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("hooks.slack.com")) {
            return WebhookFormat.SLACK;
        }
        if (lower.contains("discord.com/api/webhooks") || lower.contains("discordapp.com/api/webhooks")) {
            return WebhookFormat.DISCORD;
        }
        return null;
    }

    private void guessFormat(String value) {
        WebhookFormat guessed = guess(value);
        if (guessed != null) {
            format.setValue(guessed);
        }
    }

    /** The recent delivery attempts of one webhook. */
    private static final class DeliveriesDialog extends Dialog {

        DeliveriesDialog(WebhookSnapshot hook, java.util.List<WebhookSnapshot.DeliverySnapshot> deliveries, Clock clock) {
            setHeaderTitle("Deliveries to " + hook.url());
            setWidth("min(560px, 95vw)");
            if (deliveries.isEmpty()) {
                Span empty = new Span("Nothing has been sent yet.");
                empty.addClassName("insights__hint");
                add(empty);
            }
            Div rows = new Div();
            rows.addClassName("webhooks__deliveries");
            for (WebhookSnapshot.DeliverySnapshot delivery : deliveries) {
                Badge badge = new Badge(delivery.summary());
                badge.addThemeVariants(BadgeVariant.SMALL, delivery.succeeded() ? BadgeVariant.SUCCESS : BadgeVariant.ERROR);
                Span event = new Span(delivery.event());
                event.addClassName("webhooks__event");
                Span when = new Span(Durations.ago(delivery.attemptedAt(), clock.instant()));
                when.addClassName("webhooks__when");
                Div row = new Div(badge, event, when);
                row.addClassName("webhooks__delivery");
                rows.add(row);
            }
            add(rows);
            getFooter().add(new Button("Close", event -> close()));
        }
    }
}
