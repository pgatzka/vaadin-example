package dev.gatzka.flowboard.ui.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.html.Div;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.SharingService;
import dev.gatzka.flowboard.ui.board.CardComponent;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The public view is anonymous: no mock user here on purpose. */
@SpringBootTest
@ActiveProfiles("test")
class SharedBoardViewTest extends SpringBrowserlessTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private SharingService sharing;

    @Test
    void showsASharedBoardLiveAndWithoutControls() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Roadmap", "What ships when"), "Mara");
        boardService.createCard(board.lanes().get(0).id(),
                new CardDraft("Public beta", null, Priority.HIGH, null, "Mara", Set.of("launch"), 5), "Mara");
        String token = sharing.enable(board.id(), "Mara");

        navigate(SharedBoardView.class, Map.of(SharedBoardView.TOKEN, token));

        assertThat($(Div.class).withClassName("lane").all()).hasSize(4);
        CardComponent card = $(CardComponent.class).single();
        assertThat(card.card().title()).isEqualTo("Public beta");
        assertThat($(Div.class).withClassName("task-card__title").single().getText()).isEqualTo("Public beta");
        assertThat(card.hasClassName("task-card--readonly")).isTrue();
        assertThat(card.getElement().getAttribute("role")).isNull();
        assertThat($(Div.class).withClassName("shared__gone").all()).as("hidden components are not found").isEmpty();

        boardService.createCard(board.lanes().get(1).id(), CardDraft.titled("Pushed later"), "Mara");
        await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            roundTrip();
            assertThat($(CardComponent.class).all()).hasSize(2);
        });

        sharing.disable(board.id(), "Mara");
        await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            roundTrip();
            assertThat($(Div.class).withClassName("shared__gone").all()).hasSize(1);
            assertThat($(Div.class).withClassName("lane").all()).isEmpty();
        });
    }

    @Test
    void unknownTokensShowThatTheLinkIsNotActive() {
        navigate(SharedBoardView.class, Map.of(SharedBoardView.TOKEN, "does-not-exist"));
        assertThat($(Div.class).withClassName("shared__gone").single().isVisible()).isTrue();
        assertThat($(Div.class).withClassName("lane").all()).isEmpty();
    }
}
