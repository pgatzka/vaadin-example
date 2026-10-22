package dev.gatzka.flowboard.ui.account;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.gatzka.flowboard.ui.support.Themes;
import dev.gatzka.flowboard.security.AccountService;
import dev.gatzka.flowboard.security.RegistrationException;
import java.util.Map;

/** Creates an account and sends the new user to the sign-in page. */
@Route(value = RegisterView.PATH, autoLayout = false)
@AnonymousAllowed
public class RegisterView extends Div implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return I18n.t("register.title") + " \u00b7 Flowboard";
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        Themes.followSystem(attachEvent.getUI());
    }

    public static final String PATH = "register";

    private final AccountService accounts;
    private final TextField username = new TextField(I18n.t("register.username"));
    private final TextField displayName = new TextField(I18n.t("profile.displayName"));
    private final PasswordField password = new PasswordField(I18n.t("register.password"));
    private final PasswordField confirmation = new PasswordField(I18n.t("register.repeatPassword"));
    private final Span error = new Span();

    public RegisterView(AccountService accounts) {
        this.accounts = accounts;
        addClassName("account-page");

        username.setHelperText(I18n.t("register.usernameHelper"));
        username.setMaxLength(AccountService.MAX_NAME_LENGTH);
        username.setAutofocus(true);
        username.setRequired(true);
        displayName.setHelperText(I18n.t("register.displayNameHelper"));
        displayName.setMaxLength(AccountService.MAX_NAME_LENGTH);
        displayName.setRequired(true);
        password.setHelperText(I18n.t("register.passwordHelper", AccountService.MIN_PASSWORD_LENGTH));
        password.setRequired(true);
        confirmation.setRequired(true);

        FormLayout fields = new FormLayout(username, displayName, password, confirmation);
        fields.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        error.addClassName("account-page__error");
        error.setVisible(false);

        Button submit = new Button(I18n.t("register.submit"), event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.addClickShortcut(Key.ENTER);
        submit.setWidthFull();
        submit.setId("register-submit");

        Div footer = new Div(new Span(I18n.t("register.haveAccount") + " "), new Anchor(LoginView.PATH, I18n.t("login.signIn")));
        footer.addClassName("account-page__footer");

        Div card = new Div(AccountPages.brand(), new H2(I18n.t("register.title")), fields, error, submit, footer);
        card.addClassName("account-page__card");
        add(card);
    }

    private void submit() {
        error.setVisible(false);
        if (!password.getValue().equals(confirmation.getValue())) {
            showError(I18n.t("register.mismatch"));
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
