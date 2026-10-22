package dev.gatzka.flowboard.ui.account;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.ApiToken;
import dev.gatzka.flowboard.domain.ApiTokenSnapshot;
import dev.gatzka.flowboard.security.ApiTokenService;
import dev.gatzka.flowboard.ui.support.Durations;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.time.Clock;
import java.util.List;

/** Mint and revoke personal API tokens. A new token's secret is shown exactly once. */
public final class ApiTokensDialog extends Dialog {

    private final ApiTokenService tokens;
    private final String login;
    private final Clock clock;
    private final Div list = new Div();
    private final Div reveal = new Div();
    private final TextField name = new TextField("Token name");

    public ApiTokensDialog(ApiTokenService tokens, String login, Clock clock) {
        this.tokens = tokens;
        this.login = login;
        this.clock = clock;
        setHeaderTitle("API tokens");
        setWidth("min(560px, 95vw)");
        addClassName("tokens");

        add(new Paragraph("Scripts and integrations can call the API with a token instead of your password: "
                + "send it as \"Authorization: Bearer <token>\". Tokens act as you and can be revoked any time."));

        name.setId("token-name");
        name.setPlaceholder("e.g. CI pipeline");
        name.setMaxLength(ApiToken.MAX_NAME_LENGTH);
        name.setWidthFull();
        name.addKeyPressListener(Key.ENTER, event -> create());
        Button create = new Button("Create token", VaadinIcon.KEY.create(), event -> create());
        create.setId("create-token");
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div form = new Div(name, create);
        form.addClassName("tokens__form");
        reveal.addClassName("tokens__reveal");
        reveal.setVisible(false);
        list.addClassName("tokens__list");
        add(form, reveal, list);
        getFooter().add(new Button("Close", event -> close()));
        showList();
    }

    private void create() {
        try {
            ApiTokenService.Issued issued = tokens.issue(login, name.getValue());
            name.clear();
            name.setInvalid(false);
            showSecret(issued);
            showList();
        } catch (IllegalArgumentException invalid) {
            name.setErrorMessage(invalid.getMessage());
            name.setInvalid(true);
        }
    }

    private void showSecret(ApiTokenService.Issued issued) {
        reveal.removeAll();
        Span note = new Span("Copy the token for \"" + issued.token().name() + "\" now; it will not be shown again.");
        note.addClassName("tokens__note");
        TextField secret = new TextField();
        secret.setId("token-secret");
        secret.setValue(issued.secret());
        secret.setReadOnly(true);
        secret.setWidthFull();
        Button copy = new Button(VaadinIcon.COPY_O.create(), event -> {
            secret.getElement().executeJs("navigator.clipboard && navigator.clipboard.writeText($0)", issued.secret());
            Toasts.success("Token copied");
        });
        copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        copy.setAriaLabel("Copy token");
        secret.setSuffixComponent(copy);
        reveal.add(note, secret);
        reveal.setVisible(true);
    }

    private void showList() {
        list.removeAll();
        List<ApiTokenSnapshot> mine = tokens.forUser(login);
        if (mine.isEmpty()) {
            Span empty = new Span("You have no tokens yet.");
            empty.addClassName("tokens__empty");
            list.add(empty);
            return;
        }
        for (ApiTokenSnapshot token : mine) {
            list.add(row(token));
        }
    }

    private Div row(ApiTokenSnapshot token) {
        Span label = new Span(token.name());
        label.addClassName("tokens__name");
        Span prefix = new Span(token.prefix() + "…");
        prefix.addClassName("tokens__prefix");
        Span usage = new Span(token.lastUsedAt() == null
                ? "never used · created " + Durations.ago(token.createdAt(), clock.instant())
                : "last used " + Durations.ago(token.lastUsedAt(), clock.instant()));
        usage.addClassName("tokens__usage");
        Button revoke = new Button("Revoke", event -> {
            tokens.revoke(token.id(), login);
            Toasts.info("Token \"" + token.name() + "\" revoked");
            showList();
        });
        revoke.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        revoke.addClassName("tokens__revoke");
        Div text = new Div(new Div(label, prefix), usage);
        text.addClassName("tokens__text");
        Div row = new Div(VaadinIcon.KEY_O.create(), text, revoke);
        row.addClassName("tokens__row");
        row.getElement().setAttribute("data-token-id", Long.toString(token.id()));
        return row;
    }
}
