package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardFilter;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.SavedViewSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SavedViewServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private SavedViewService views;

    @Autowired
    private ActivityLog activityLog;

    @Test
    void savesReplacesListsAndDeletesViews() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Views", null), "Mara");
        CardFilter urgent = CardFilter.none().withPriority(Priority.URGENT);
        CardFilter mine = CardFilter.none().withMineOnly(true).withTag("ops");

        SavedViewSnapshot first = views.save(board.id(), "  Urgent ", urgent, "Mara");
        views.save(board.id(), "Mine", mine, "Jonas");
        assertThat(first.name()).isEqualTo("Urgent");
        assertThat(views.forBoard(board.id())).extracting(SavedViewSnapshot::name).containsExactly("Mine", "Urgent");
        assertThat(views.forBoard(board.id()).get(1).filter()).isEqualTo(urgent);
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("saved the view \"Mine\"");

        SavedViewSnapshot replaced = views.save(board.id(), "URGENT", mine, "Mara");
        assertThat(replaced.id()).isEqualTo(first.id());
        assertThat(views.forBoard(board.id())).hasSize(2);
        assertThat(views.forBoard(board.id()).get(1).filter()).isEqualTo(mine);

        views.delete(first.id(), "Mara");
        views.delete(first.id(), "Mara");
        assertThat(views.forBoard(board.id())).extracting(SavedViewSnapshot::name).containsExactly("Mine");

        boardService.deleteBoard(board.id(), "Mara");
        assertThat(views.forBoard(board.id())).isEmpty();
    }

    @Test
    void rejectsEmptyNamesAndEmptyFilters() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Views", null), "Mara");
        assertThatThrownBy(() -> views.save(board.id(), " ", CardFilter.none().withText("x"), "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> views.save(board.id(), "x".repeat(61), CardFilter.none().withText("x"), "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> views.save(board.id(), "Nothing", CardFilter.none(), "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThat(views.forBoard(board.id())).isEmpty();
    }
}
