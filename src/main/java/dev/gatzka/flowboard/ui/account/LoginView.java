package dev.gatzka.flowboard.ui.account;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.gatzka.flowboard.ui.support.Themes;
import org.springframework.beans.factory.annotation.Value;

/** Sign-in page; the form posts to Spring Security, which sends the user back to where they wanted to go. */
@Route(value = LoginView.PATH, autoLayout = false)
@PageTitle("Sign in · Flowboard")
@AnonymousAllowed
public class LoginView extends Div implements BeforeEnterObserver {

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
        i18n.getForm().setTitle("Sign in");
        i18n.getForm().setSubmit("Sign in");
        i18n.getErrorMessage().setTitle("That didn't work");
        i18n.getErrorMessage().setMessage("Check your username and password and try again.");
        form.setI18n(i18n);
        form.setAction(PATH);
        form.setForgotPasswordButtonVisible(false);

        notice.addClassName("account-page__notice");
        notice.setVisible(false);

        Div footer = new Div(new Span("New here? "), new Anchor(RegisterView.PATH, "Create an account"));
        footer.addClassName("account-page__footer");
        if (sampleData) {
            Span hint = new Span("Just looking? Sign in as demo with the password demo1234.");
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
        notice.setText(registered ? "Your account is ready. Sign in to get started." : "");
        notice.setVisible(registered);
    }
}
