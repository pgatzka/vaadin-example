package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.ChecklistItemSnapshot;
import dev.gatzka.flowboard.repository.ChecklistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ChecklistServiceTest {

    private static final String ACTOR = "Tester";

    @Autowired
    private BoardService boardService;

    @Autowired
    private ChecklistService checklistService;

    @Autowired
    private ChecklistItemRepository items;

    @Autowired
    private ActivityLog activityLog;

    private BoardSnapshot board;
    private CardSnapshot card;

    @BeforeEach
    void createCard() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), ACTOR));
        board = boardService.createBoard(new BoardDraft("Checklists", null), ACTOR);
        card = boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Release"), ACTOR);
    }

    @Test
    void itemsAreAppendedInOrderAndStartOpen() {
        checklistService.addItem(card.id(), "Tag the build", ACTOR);
        CardSnapshot updated = checklistService.addItem(card.id(), "Write release notes", ACTOR);

        assertThat(updated.checklist()).extracting(ChecklistItemSnapshot::text).containsExactly("Tag the build", "Write release notes");
        assertThat(updated.checklist()).extracting(ChecklistItemSnapshot::position).containsExactly(0, 1);
        assertThat(updated.checklist()).extracting(ChecklistItemSnapshot::done).containsOnly(false);
        assertThat(updated.hasChecklist()).isTrue();
        assertThat(updated.isChecklistComplete()).isFalse();
    }

    @Test
    void tickingItemsTracksProgress() {
        CardSnapshot withItems = checklistService.addItem(card.id(), "One", ACTOR);
        withItems = checklistService.addItem(card.id(), "Two", ACTOR);
        long first = withItems.checklist().get(0).id();

        CardSnapshot ticked = checklistService.setDone(first, true, ACTOR);
        assertThat(ticked.checklistDone()).isEqualTo(1);
        assertThat(ticked.isChecklistComplete()).isFalse();

        CardSnapshot complete = checklistService.setDone(withItems.checklist().get(1).id(), true, ACTOR);
        assertThat(complete.isChecklistComplete()).isTrue();

        CardSnapshot reopened = checklistService.setDone(first, false, ACTOR);
        assertThat(reopened.checklistDone()).isEqualTo(1);
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("reopened \"One\" on \"Release\"");
    }

    @Test
    void tickingAnItemThatIsAlreadyDoneRecordsNothing() {
        CardSnapshot withItem = checklistService.addItem(card.id(), "Only", ACTOR);
        long itemId = withItem.checklist().getFirst().id();
        checklistService.setDone(itemId, true, ACTOR);
        int activityBefore = activityLog.recent(board.id()).size();

        checklistService.setDone(itemId, true, ACTOR);

        assertThat(activityLog.recent(board.id())).hasSize(activityBefore);
    }

    @Test
    void removingAnItemClosesTheGap() {
        checklistService.addItem(card.id(), "a", ACTOR);
        CardSnapshot withItems = checklistService.addItem(card.id(), "b", ACTOR);
        withItems = checklistService.addItem(card.id(), "c", ACTOR);

        CardSnapshot trimmed = checklistService.removeItem(withItems.checklist().get(1).id(), ACTOR);

        assertThat(trimmed.checklist()).extracting(ChecklistItemSnapshot::text).containsExactly("a", "c");
        assertThat(trimmed.checklist()).extracting(ChecklistItemSnapshot::position).containsExactly(0, 1);
        assertThat(items.count()).isEqualTo(2);
    }

    @Test
    void checklistIsPartOfTheBoardSnapshot() {
        checklistService.addItem(card.id(), "Visible", ACTOR);

        CardSnapshot fromBoard = boardService.findBoard(board.id()).orElseThrow().lanes().get(0).cards().getFirst();

        assertThat(fromBoard.checklist()).extracting(ChecklistItemSnapshot::text).containsExactly("Visible");
    }

    @Test
    void checklistDiesWithItsCardLaneAndBoard() {
        checklistService.addItem(card.id(), "Goes away", ACTOR);
        boardService.deleteCard(card.id(), ACTOR);
        assertThat(items.count()).isZero();

        CardSnapshot another = boardService.createCard(board.lanes().get(1).id(), CardDraft.titled("Other"), ACTOR);
        checklistService.addItem(another.id(), "Also gone", ACTOR);
        boardService.deleteLane(board.lanes().get(1).id(), ACTOR);
        assertThat(items.count()).isZero();

        CardSnapshot third = boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Third"), ACTOR);
        checklistService.addItem(third.id(), "Board goes", ACTOR);
        boardService.deleteBoard(board.id(), ACTOR);
        assertThat(items.count()).isZero();
    }

    @Test
    void blankItemsAndUnknownIdsAreRejected() {
        assertThatThrownBy(() -> checklistService.addItem(card.id(), "   ", ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> checklistService.addItem(999_999, "x", ACTOR)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> checklistService.setDone(999_999, true, ACTOR)).isInstanceOf(NotFoundException.class);
    }
}
