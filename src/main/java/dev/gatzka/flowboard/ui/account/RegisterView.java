package dev.gatzka.flowboard.ui.account;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.gatzka.flowboard.security.AccountService;
import dev.gatzka.flowboard.security.RegistrationException;
import java.util.Map;

/** Creates an account and sends the new user to the sign-in page. */
@Route(value = RegisterView.PATH, autoLayout = false)
@PageTitle("Create an account · Flowboard")
@AnonymousAllowed
public class RegisterView extends Div {

    public static final String PATH = "register";

    private final AccountService accounts;
    private final TextField username = new TextField("Username");
    private final TextField displayName = new TextField("Display name");
    private final PasswordField password = new PasswordField("Password");
    private final PasswordField confirmation = new PasswordField("Repeat password");
    private final Span error = new Span();

    public RegisterView(AccountService accounts) {
        this.accounts = accounts;
        addClassName("account-page");

        username.setHelperText("Letters, digits, dots, dashes or underscores. Used to sign in.");
        username.setMaxLength(AccountService.MAX_NAME_LENGTH);
        username.setAutofocus(true);
        username.setRequired(true);
        displayName.setHelperText("What others see next to your changes.");
        displayName.setMaxLength(AccountService.MAX_NAME_LENGTH);
        displayName.setRequired(true);
        password.setHelperText("At least " + AccountService.MIN_PASSWORD_LENGTH + " characters.");
        password.setRequired(true);
        confirmation.setRequired(true);

        FormLayout fields = new FormLayout(username, displayName, password, confirmation);
        fields.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        error.addClassName("account-page__error");
        error.setVisible(false);

        Button submit = new Button("Create account", event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.addClickShortcut(Key.ENTER);
        submit.setWidthFull();
        submit.setId("register-submit");

        Div footer = new Div(new Span("Already have an account? "), new Anchor(LoginView.PATH, "Sign in"));
        footer.addClassName("account-page__footer");

        Div card = new Div(AccountPages.brand(), new H2("Create an account"), fields, error, submit, footer);
        card.addClassName("account-page__card");
        add(card);
    }

    private void submit() {
        error.setVisible(false);
        if (!password.getValue().equals(confirmation.getValue())) {
            showError("The passwords don't match");
            return;
        }
        try {
            accounts.register(username.getValue(), displayName.getValue(), password.getValue());
        } catch (RegistrationException e) {
            showError(e.getMessage());
            return;
        }
        getUI().ifPresent(ui -> ui.navigate(LoginView.PATH,
                new QueryParameters(Map.of(LoginView.REGISTERED_PARAMETER, java.util.List.of()))));
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisible(true);
    }
}
