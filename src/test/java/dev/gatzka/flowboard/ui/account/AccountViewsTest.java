package dev.gatzka.flowboard.ui.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.security.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The sign-in and registration pages are the only ones reachable without an account. */
@SpringBootTest
@ActiveProfiles("test")
class AccountViewsTest extends SpringBrowserlessTest {

    @Autowired
    private AccountService accounts;

    @Test
    void registrationCreatesTheAccountAndLeadsToSignIn() {
        navigate(RegisterView.class);
        $(TextField.class).withLabel("Username").single().setValue("New.User");
        $(TextField.class).withLabel("Display name").single().setValue("Newcomer");
        $(PasswordField.class).withLabel("Password").single().setValue("longenough");
        $(PasswordField.class).withLabel("Repeat password").single().setValue("longenough");
        test($(Button.class).withId("register-submit").single()).click();

        assertThat(accounts.displayNameOf("new.user")).isEqualTo("Newcomer");
        assertThat(getCurrentView()).isInstanceOf(LoginView.class);
        assertThat($(Span.class).withClassName("account-page__notice").single().getText()).contains("ready");
    }

    @Test
    void registrationShowsWhatIsWrong() {
        navigate(RegisterView.class);
        $(TextField.class).withLabel("Username").single().setValue("someone");
        $(TextField.class).withLabel("Display name").single().setValue("Someone");
        $(PasswordField.class).withLabel("Password").single().setValue("longenough");
        $(PasswordField.class).withLabel("Repeat password").single().setValue("different");
        test($(Button.class).withId("register-submit").single()).click();
        assertThat($(Span.class).withClassName("account-page__error").single().getText()).contains("match");

        $(PasswordField.class).withLabel("Password").single().setValue("short");
        $(PasswordField.class).withLabel("Repeat password").single().setValue("short");
        test($(Button.class).withId("register-submit").single()).click();
        assertThat($(Span.class).withClassName("account-page__error").single().getText()).contains("8 characters");
        assertThat(getCurrentView()).isInstanceOf(RegisterView.class);
        assertThat(accounts.find("someone")).isEmpty();
    }

    @Test
    void signInPageIsOpenToEverybody() {
        navigate(LoginView.class);
        assertThat(getCurrentView()).isInstanceOf(LoginView.class);
    }
}
