package dev.gatzka.flowboard.ui.account;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.ui.FlowboardUiTest;
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
class AccountViewsTest extends FlowboardUiTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private dev.gatzka.flowboard.security.ApiTokenService tokenService;

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
    @org.springframework.security.test.context.support.WithMockUser(username = "mara")
    void apiTokensDialogMintsAndRevokesTokens() {
        if (accounts.find("mara").isEmpty()) {
            accounts.register("mara", "Mara", "longenough");
        }
        navigate(dev.gatzka.flowboard.ui.boards.BoardsView.class);
        test($(Button.class).withCondition(b -> "Search all boards".equals(b.getAriaLabel().orElse(null))).single()).click();
        test($(TextField.class).withPlaceholder("Search boards and cards, or type a command").single()).setValue("API tokens");
        test($(Button.class).withClassName("palette__command").withText("API tokens").single()).click();

        ApiTokensDialog dialog = $(ApiTokensDialog.class).single();
        assertThat($(Span.class, dialog).withClassName("tokens__empty").all()).hasSize(1);

        test($(TextField.class).withId("token-name").single()).setValue("Laptop script");
        test($(Button.class).withId("create-token").single()).click();

        String secret = $(TextField.class).withId("token-secret").single().getValue();
        assertThat(secret).startsWith("fb_");
        assertThat($(com.vaadin.flow.component.html.Div.class, dialog).withClassName("tokens__row").all()).hasSize(1);
        assertThat($(Span.class, dialog).withClassName("tokens__name").single().getText()).isEqualTo("Laptop script");
        assertThat(tokenService.authenticate(secret)).isPresent();

        test($(Button.class, dialog).withClassName("tokens__revoke").single()).click();

        assertThat($(Span.class, dialog).withClassName("tokens__empty").all()).hasSize(1);
        assertThat(tokenService.authenticate(secret)).isEmpty();
    }

    @Test
    void signInPageIsOpenToEverybody() {
        navigate(LoginView.class);
        assertThat(getCurrentView()).isInstanceOf(LoginView.class);
    }
}
