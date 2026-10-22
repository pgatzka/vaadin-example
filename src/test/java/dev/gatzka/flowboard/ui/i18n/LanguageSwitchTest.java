package dev.gatzka.flowboard.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.sidenav.SideNavItem;
import dev.gatzka.flowboard.ui.FlowboardUiTest;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.work.MyWorkView;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "mara")
class LanguageSwitchTest extends FlowboardUiTest {

    @Test
    void theWholeShellFollowsTheChosenLanguage() {
        navigate(BoardsView.class);
        assertThat($(SideNavItem.class).all()).extracting(SideNavItem::getLabel).contains("Home", "My work", "Team");

        UI.getCurrent().setLocale(Locale.GERMAN);
        navigate(MyWorkView.class);

        assertThat($(H2.class).first().getText()).isEqualTo("Meine Aufgaben");
        assertThat($(SideNavItem.class).all()).extracting(SideNavItem::getLabel).contains("Start", "Meine Aufgaben", "Team");
        assertThat(UI.getCurrent().getTranslation("common.save")).isEqualTo("Speichern");

        UI.getCurrent().setLocale(Locale.ENGLISH);
        navigate(BoardsView.class);
        assertThat($(H2.class).first().getText()).isEqualTo("Home");
    }
}
