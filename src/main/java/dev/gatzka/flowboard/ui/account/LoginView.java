package dev.gatzka.flowboard.ui.account;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.gatzka.flowboard.ui.support.Themes;
import org.springframework.beans.factory.annotation.Value;

/** Sign-in page; the form posts to Spring Security, which sends the user back to where they wanted to go. */
@Route(value = LoginView.PATH, autoLayout = false)
@AnonymousAllowed
public class LoginView extends Div implements BeforeEnterObserver, HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return I18n.t("login.signIn") + " \u00b7 Flowboard";
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        Themes.followSystem(attachEvent.getUI());
    }

    public static final String PATH = "login";
    static final String ERROR_PARAMETER = "error";
    static final String REGISTERED_PARAMETER = "registered";

    private final LoginForm form = new LoginForm();
    private final Span notice = new Span();

    public LoginView(@Value("${flowboard.sample-data:true}") boolean sampleData) {
        addClassName("account-page");

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle(I18n.t("login.signIn"));
        i18n.getForm().setSubmit(I18n.t("login.signIn"));
        i18n.getForm().setUsername(I18n.t("register.username"));
        i18n.getForm().setPassword(I18n.t("register.password"));
        i18n.getErrorMessage().setTitle(I18n.t("login.errorTitle"));
        i18n.getErrorMessage().setMessage(I18n.t("login.errorText"));
        form.setI18n(i18n);
        form.setAction(PATH);
        form.setForgotPasswordButtonVisible(false);

        notice.addClassName("account-page__notice");
        notice.setVisible(false);

        Div footer = new Div(new Span(I18n.t("login.newHere") + " "), new Anchor(RegisterView.PATH, I18n.t("register.title")));
        footer.addClassName("account-page__footer");
        if (sampleData) {
            Span hint = new Span(I18n.t("login.demoHint"));
            hint.addClassName("account-page__hint");
            footer.add(hint);
        }

        Div card = new Div(AccountPages.brand(), notice, form, footer);
        card.addClassName("account-page__card");
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var parameters = event.getLocation().getQueryParameters().getParameters();
        form.setError(parameters.containsKey(ERROR_PARAMETER));
        boolean registered = parameters.containsKey(REGISTERED_PARAMETER);
        notice.setText(registered ? I18n.t("login.registered") : "");
        notice.setVisible(registered);
    }
}
