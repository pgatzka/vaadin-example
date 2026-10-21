package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BlockerSnapshot;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DependencyServiceTest {

    private static final String ACTOR = "Tester";

    @Autowired
    private BoardService boardService;

    @Autowired
    private DependencyService dependencies;

    @Autowired
    private ActivityLog activityLog;

    private BoardSnapshot board;
    private CardSnapshot design;
    private CardSnapshot build;
    private CardSnapshot ship;

    @BeforeEach
    void seed() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), ACTOR));
        board = boardService.createBoard(new BoardDraft("Deps", null), ACTOR);
        long backlog = board.lanes().get(0).id();
        design = boardService.createCard(backlog, CardDraft.titled("Design"), ACTOR);
        build = boardService.createCard(backlog, CardDraft.titled("Build"), ACTOR);
        ship = boardService.createCard(backlog, CardDraft.titled("Ship"), ACTOR);
    }

    @Test
    void blockersAreListedAndResolveWhenTheBlockerIsDone() {
        CardSnapshot blocked = dependencies.addBlocker(build.id(), design.id(), ACTOR);

        assertThat(blocked.isBlocked()).isTrue();
        assertThat(blocked.blockers()).extracting(BlockerSnapshot::title).containsExactly("Design");
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("marked \"Build\" as blocked by \"Design\"");

        boardService.moveCard(design.id(), board.lanes().get(3).id(), 0, ACTOR);
        CardSnapshot afterDone = reload(build.id());
        assertThat(afterDone.isBlocked()).isFalse();
        assertThat(afterDone.blockers().getFirst().resolved()).isTrue();

        boardService.archiveCard(design.id(), ACTOR);
        boardService.moveCard(build.id(), board.lanes().get(1).id(), 0, ACTOR);
        assertThat(reload(build.id()).isBlocked()).isFalse();
    }

    @Test
    void cyclesSelfLinksAndOtherBoardsAreRejected() {
        dependencies.addBlocker(build.id(), design.id(), ACTOR);
        dependencies.addBlocker(ship.id(), build.id(), ACTOR);

        assertThatThrownBy(() -> dependencies.addBlocker(design.id(), ship.id(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycle");
        assertThatThrownBy(() -> dependencies.addBlocker(design.id(), design.id(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("itself");

        BoardSnapshot other = boardService.createBoard(new BoardDraft("Other", null), ACTOR);
        CardSnapshot foreign = boardService.createCard(other.lanes().get(0).id(), CardDraft.titled("Foreign"), ACTOR);
        assertThatThrownBy(() -> dependencies.addBlocker(design.id(), foreign.id(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same board");
    }

    @Test
    void linksAreRemovedExplicitlyOrWithTheBlockerCard() {
        dependencies.addBlocker(build.id(), design.id(), ACTOR);
        dependencies.addBlocker(ship.id(), design.id(), ACTOR);

        assertThat(dependencies.removeBlocker(build.id(), design.id(), ACTOR).blockers()).isEmpty();

        boardService.deleteCard(design.id(), ACTOR);
        assertThat(reload(ship.id()).blockers()).isEmpty();

        dependencies.addBlocker(ship.id(), build.id(), ACTOR);
        boardService.deleteBoard(board.id(), ACTOR);
        assertThat(boardService.findBoard(board.id())).isEmpty();
    }

    private CardSnapshot reload(long cardId) {
        return boardService.findBoard(board.id()).orElseThrow().lanes().stream()
                .flatMap(lane -> lane.cards().stream()).filter(card -> card.id() == cardId).findFirst().orElseThrow();
    }
}
