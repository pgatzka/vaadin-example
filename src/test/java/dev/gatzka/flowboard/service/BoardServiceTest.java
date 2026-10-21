package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.BoardTemplate;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneDraft;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import java.time.Duration;
import java.time.LocalDate;
import dev.gatzka.flowboard.domain.Recurrence;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BoardServiceTest {

    private static final String ACTOR = "Tester";

    @Autowired
    private BoardService service;

    @Autowired
    private ActivityLog activityLog;

    @Autowired
    private DependencyService dependencies;

    @Autowired
    private ChecklistService checklistService;

    @Autowired
    private BoardChangeBroadcaster broadcaster;

    private BoardSnapshot board;
    private long backlog;
    private long inProgress;

    @BeforeEach
    void createBoard() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), ACTOR));
        board = service.createBoard(new BoardDraft("Launch", "Ship it"), ACTOR);
        backlog = board.lanes().get(0).id();
        inProgress = board.lanes().get(1).id();
    }

    @Test
    void newBoardComesWithDefaultLanes() {
        assertThat(board.lanes()).extracting(LaneSnapshot::name)
                .containsExactly("Backlog", "In progress", "Review", "Done");
        assertThat(board.lanes()).extracting(LaneSnapshot::position).containsExactly(0, 1, 2, 3);
        assertThat(board.lanes().get(1).wipLimit()).isEqualTo(3);
        assertThat(board.lanes().get(3).done()).isTrue();
    }

    @Test
    void boardsCanBeCreatedFromTemplates() {
        BoardSnapshot scrum = service.createBoard(new BoardDraft("Sprint 1", null), BoardTemplate.SCRUM, ACTOR);
        assertThat(scrum.lanes()).extracting(LaneSnapshot::name)
                .containsExactly("Product backlog", "Sprint backlog", "In progress", "Review", "Done");
        assertThat(scrum.lanes().getLast().done()).isTrue();

        BoardSnapshot blank = service.createBoard(new BoardDraft("Empty", null), BoardTemplate.BLANK, ACTOR);
        assertThat(blank.lanes()).isEmpty();
        assertThat(activityLog.recent(blank.id()).getFirst().message()).isEqualTo("created board \"Empty\" from the Blank template");
    }

    @Test
    void boardSummaryCountsLanesCardsAndOverdueWork() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        createCard(backlog, "Late", yesterday);
        createCard(backlog, "On time", LocalDate.now().plusDays(1));
        createCard(board.lanes().get(3).id(), "Finished late but finished", yesterday);

        BoardSummary summary = service.listBoards().getFirst();

        assertThat(summary.name()).isEqualTo("Launch");
        assertThat(summary.laneCount()).isEqualTo(4);
        assertThat(summary.cardCount()).isEqualTo(3);
        assertThat(summary.overdueCount()).as("cards in a done lane are never overdue").isEqualTo(1);
    }

    @Test
    void cardsAreAppendedInOrder() {
        createCard(backlog, "One");
        createCard(backlog, "Two");
        createCard(backlog, "Three");

        assertThat(titles(backlog)).containsExactly("One", "Two", "Three");
        assertThat(lane(backlog).cards()).extracting(CardSnapshot::position).containsExactly(0, 1, 2);
    }

    @Test
    void moveCardIntoAnotherLaneAtGivenIndex() {
        createCard(backlog, "a0");
        CardSnapshot a1 = createCard(backlog, "a1");
        createCard(backlog, "a2");
        createCard(inProgress, "b0");
        createCard(inProgress, "b1");

        service.moveCard(a1.id(), inProgress, 1, ACTOR);

        assertThat(titles(backlog)).containsExactly("a0", "a2");
        assertThat(titles(inProgress)).containsExactly("b0", "a1", "b1");
        assertThat(lane(backlog).cards()).extracting(CardSnapshot::position).containsExactly(0, 1);
        assertThat(lane(inProgress).cards()).extracting(CardSnapshot::position).containsExactly(0, 1, 2);
    }

    @Test
    void moveCardDownWithinLaneDropsItBeforeTheTargetCard() {
        CardSnapshot x = createCard(backlog, "x");
        createCard(backlog, "y");
        CardSnapshot z = createCard(backlog, "z");

        service.moveCard(x.id(), backlog, z.position(), ACTOR);

        assertThat(titles(backlog)).containsExactly("y", "x", "z");
    }

    @Test
    void moveCardUpWithinLaneDropsItBeforeTheTargetCard() {
        CardSnapshot x = createCard(backlog, "x");
        createCard(backlog, "y");
        CardSnapshot z = createCard(backlog, "z");

        service.moveCard(z.id(), backlog, x.position(), ACTOR);

        assertThat(titles(backlog)).containsExactly("z", "x", "y");
    }

    @Test
    void moveCardToTheEndOfItsLane() {
        CardSnapshot x = createCard(backlog, "x");
        createCard(backlog, "y");
        createCard(backlog, "z");

        service.moveCard(x.id(), backlog, lane(backlog).cards().size(), ACTOR);

        assertThat(titles(backlog)).containsExactly("y", "z", "x");
    }

    @Test
    void laneEntryTimeResetsOnlyWhenTheLaneChanges() {
        CardSnapshot card = createCard(backlog, "mover");
        createCard(backlog, "other");
        java.time.Instant entered = card.laneEnteredAt();
        assertThat(entered).isNotNull();

        service.moveCard(card.id(), backlog, 2, ACTOR);
        assertThat(lane(backlog).cards().getLast().laneEnteredAt())
                .isCloseTo(entered, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));

        service.moveCard(card.id(), inProgress, 0, ACTOR);
        assertThat(lane(inProgress).cards().getFirst().laneEnteredAt()).isAfterOrEqualTo(entered);
        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(1).cards().getFirst().age(java.time.Instant.now()).level())
                .isEqualTo(dev.gatzka.flowboard.domain.CardAge.Level.FRESH);
    }

    @Test
    void movingCardOntoItsOwnPositionChangesNothing() {
        CardSnapshot x = createCard(backlog, "x");
        createCard(backlog, "y");
        int activityBefore = activityLog.recent(board.id()).size();

        service.moveCard(x.id(), backlog, x.position(), ACTOR);

        assertThat(titles(backlog)).containsExactly("x", "y");
        assertThat(activityLog.recent(board.id())).hasSize(activityBefore);
    }

    @Test
    void cardsCannotLeaveTheirBoard() {
        CardSnapshot card = createCard(backlog, "Stays here");
        BoardSnapshot other = service.createBoard(new BoardDraft("Other", null), ACTOR);

        assertThatThrownBy(() -> service.moveCard(card.id(), other.lanes().get(0).id(), 0, ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingCardClosesTheGap() {
        createCard(backlog, "a");
        CardSnapshot b = createCard(backlog, "b");
        createCard(backlog, "c");

        service.deleteCard(b.id(), ACTOR);

        assertThat(titles(backlog)).containsExactly("a", "c");
        assertThat(lane(backlog).cards()).extracting(CardSnapshot::position).containsExactly(0, 1);
    }

    @Test
    void updateCardNormalisesInput() {
        CardSnapshot card = createCard(backlog, "Draft");

        CardSnapshot updated = service.updateCard(card.id(),
                new CardDraft("  Final  ", "  ", Priority.URGENT, LocalDate.of(2030, 1, 1), " Mara ", Set.of("zeta", " Alpha ", ""), null),
                ACTOR);

        assertThat(updated.title()).isEqualTo("Final");
        assertThat(updated.description()).isNull();
        assertThat(updated.priority()).isEqualTo(Priority.URGENT);
        assertThat(updated.assignee()).isEqualTo("Mara");
        assertThat(updated.tags()).containsExactly("Alpha", "zeta");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(card.updatedAt());
    }

    @Test
    void lanesCanBeAddedMovedAndRemoved() {
        LaneSnapshot blocked = service.addLane(board.id(), new LaneDraft("Blocked", 1, false), ACTOR);
        assertThat(blocked.position()).isEqualTo(4);

        service.moveLane(blocked.id(), 1, ACTOR);
        assertThat(laneNames()).containsExactly("Backlog", "Blocked", "In progress", "Review", "Done");

        createCard(blocked.id(), "Stuck");
        service.deleteLane(blocked.id(), ACTOR);
        assertThat(laneNames()).containsExactly("Backlog", "In progress", "Review", "Done");
        assertThat(reload().lanes()).extracting(LaneSnapshot::position).containsExactly(0, 1, 2, 3);
        assertThat(reload().cardCount()).isZero();
    }

    @Test
    void deletingLaneCanMoveItsCardsToAnotherLane() {
        createCard(inProgress, "existing");
        createCard(backlog, "first");
        createCard(backlog, "second");

        service.deleteLaneMovingCards(backlog, inProgress, ACTOR);

        assertThat(laneNames()).containsExactly("In progress", "Review", "Done");
        assertThat(titles(inProgress)).containsExactly("existing", "first", "second");
        assertThat(lane(inProgress).cards()).extracting(CardSnapshot::position).containsExactly(0, 1, 2);
        assertThat(activityLog.recent(board.id()).getFirst().message())
                .isEqualTo("removed lane \"Backlog\" and moved 2 cards to In progress");
    }

    @Test
    void cardsCannotBeMovedIntoTheLaneBeingDeleted() {
        assertThatThrownBy(() -> service.deleteLaneMovingCards(backlog, backlog, ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void finishingARecurringCardSchedulesTheNextOne() {
        BoardSnapshot board = service.createBoard(new BoardDraft("Chores", null), ACTOR);
        long backlog = board.lanes().get(0).id();
        long doing = board.lanes().get(1).id();
        long done = board.lanes().get(3).id();
        LocalDate lastMonday = LocalDate.now().minusDays(3);
        CardSnapshot weekly = service.createCard(backlog,
                new CardDraft("Water the plants", "Both rooms", Priority.LOW, lastMonday, "Mara", Set.of("home"), 1), ACTOR);
        service.setRecurrence(weekly.id(), Recurrence.WEEKLY, ACTOR);
        service.setRecurrence(weekly.id(), Recurrence.WEEKLY, ACTOR);
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("set \"Water the plants\" to repeat (every week)");
        service.moveCard(weekly.id(), doing, 0, ACTOR);
        assertThat(service.findBoard(board.id()).orElseThrow().lane(backlog).orElseThrow().cards()).as("not finished yet").isEmpty();

        service.moveCard(weekly.id(), done, 0, ACTOR);

        BoardSnapshot after = service.findBoard(board.id()).orElseThrow();
        CardSnapshot finished = after.lane(done).orElseThrow().cards().getFirst();
        assertThat(finished.id()).isEqualTo(weekly.id());
        assertThat(finished.isRecurring()).as("the finished copy stops repeating").isFalse();
        CardSnapshot next = after.lane(doing).orElseThrow().cards().getFirst();
        assertThat(next.id()).isNotEqualTo(weekly.id());
        assertThat(next.title()).isEqualTo("Water the plants");
        assertThat(next.description()).isEqualTo("Both rooms");
        assertThat(next.assignee()).isEqualTo("Mara");
        assertThat(next.tags()).containsExactly("home");
        assertThat(next.estimate()).isEqualTo(1);
        assertThat(next.recurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(next.dueDate()).isEqualTo(lastMonday.plusWeeks(1));
        assertThat(activityLog.recent(board.id()).stream().map(entry -> entry.message()))
                .anyMatch(message -> message.startsWith("scheduled the next \"Water the plants\" in In progress, due " + lastMonday.plusWeeks(1)));

        service.setRecurrence(next.id(), Recurrence.NONE, ACTOR);
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("stopped \"Water the plants\" from repeating");
        service.moveCard(next.id(), done, 0, ACTOR);
        assertThat(service.findBoard(board.id()).orElseThrow().lane(doing).orElseThrow().cards()).isEmpty();
    }

    @Test
    void cardsMoveAndCopyAcrossBoards() {
        BoardSnapshot source = service.createBoard(new BoardDraft("Source", null), ACTOR);
        BoardSnapshot target = service.createBoard(new BoardDraft("Target", null), ACTOR);
        long from = source.lanes().get(0).id();
        long to = target.lanes().get(1).id();
        CardSnapshot blocker = service.createCard(from, CardDraft.titled("Blocker"), ACTOR);
        CardSnapshot moving = service.createCard(from, new CardDraft("Travelling", "Notes", Priority.HIGH, null, "Mara", Set.of("x"), 2), ACTOR);
        service.createCard(to, CardDraft.titled("Already there"), ACTOR);
        service.setRecurrence(moving.id(), Recurrence.WEEKLY, ACTOR);
        dependencies.addBlocker(moving.id(), blocker.id(), ACTOR);

        CardSnapshot moved = service.transferCard(moving.id(), to, ACTOR);

        assertThat(moved.id()).isEqualTo(moving.id());
        assertThat(moved.laneId()).isEqualTo(to);
        assertThat(moved.position()).isEqualTo(1);
        assertThat(moved.blockers()).as("dependencies are board-local").isEmpty();
        assertThat(moved.recurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(service.findBoard(source.id()).orElseThrow().lane(from).orElseThrow().cards()).extracting(CardSnapshot::title).containsExactly("Blocker");
        assertThat(service.findBoard(target.id()).orElseThrow().lane(to).orElseThrow().cards()).extracting(CardSnapshot::title)
                .containsExactly("Already there", "Travelling");
        assertThat(activityLog.recent(source.id()).getFirst().message()).isEqualTo("moved \"Travelling\" to the board \"Target\"");
        assertThat(activityLog.recent(target.id()).getFirst().message()).isEqualTo("moved \"Travelling\" here from the board \"Source\"");

        checklistService.addItem(moved.id(), "Step", ACTOR);
        CardSnapshot copy = service.copyCard(moved.id(), from, ACTOR);
        assertThat(copy.id()).isNotEqualTo(moved.id());
        assertThat(copy.laneId()).isEqualTo(from);
        assertThat(copy.title()).isEqualTo("Travelling");
        assertThat(copy.assignee()).isEqualTo("Mara");
        assertThat(copy.estimate()).isEqualTo(2);
        assertThat(copy.recurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(copy.checklist()).extracting(item -> item.text()).containsExactly("Step");
        assertThat(service.findBoard(target.id()).orElseThrow().lane(to).orElseThrow().cards()).as("the original stays").hasSize(2);

        service.transferCard(copy.id(), source.lanes().get(2).id(), ACTOR);
        assertThat(service.findBoard(source.id()).orElseThrow().lanes().get(2).cards()).extracting(CardSnapshot::id).containsExactly(copy.id());
        service.archiveCard(copy.id(), ACTOR);
        assertThatThrownBy(() -> service.transferCard(copy.id(), to, ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void severalArchivedCardsCanBeRestoredAtOnce() {
        BoardSnapshot board = service.createBoard(new BoardDraft("Restore", null), ACTOR);
        long backlog = board.lanes().get(0).id();
        CardSnapshot a = service.createCard(backlog, CardDraft.titled("A"), ACTOR);
        CardSnapshot b = service.createCard(backlog, CardDraft.titled("B"), ACTOR);
        CardSnapshot c = service.createCard(backlog, CardDraft.titled("C"), ACTOR);
        service.archiveLane(backlog, ACTOR);

        assertThat(service.restoreCards(List.of(a.id(), c.id(), 999_999L), ACTOR)).isEqualTo(2);
        assertThat(service.restoreCards(List.of(a.id()), ACTOR)).as("already live").isZero();

        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("A", "C");
        assertThat(service.archivedCards(board.id())).extracting(CardSnapshot::title).containsExactly("B");
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("restored 2 cards to Backlog");
    }

    @Test
    void archivedCardsLeaveTheBoardButCanBeRestored() {
        createCard(backlog, "keep");
        CardSnapshot gone = createCard(backlog, "archive me");
        createCard(backlog, "also keep");

        service.archiveCard(gone.id(), ACTOR);

        assertThat(titles(backlog)).containsExactly("keep", "also keep");
        assertThat(lane(backlog).cards()).extracting(CardSnapshot::position).containsExactly(0, 1);
        assertThat(service.archivedCards(board.id())).extracting(CardSnapshot::title).containsExactly("archive me");
        assertThat(service.archivedCards(board.id()).getFirst().isArchived()).isTrue();
        assertThat(service.listBoards().getFirst().cardCount()).isEqualTo(2);
        assertThat(service.listBoards().getFirst().archivedCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.moveCard(gone.id(), inProgress, 0, ACTOR)).isInstanceOf(IllegalArgumentException.class);

        service.restoreCard(gone.id(), ACTOR);

        assertThat(titles(backlog)).containsExactly("keep", "also keep", "archive me");
        assertThat(service.archivedCards(board.id())).isEmpty();
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("restored \"archive me\" to Backlog");
    }

    @Test
    void wholeLaneCanBeArchived() {
        createCard(backlog, "a");
        createCard(backlog, "b");

        assertThat(service.archiveLane(backlog, ACTOR)).isEqualTo(2);
        assertThat(service.archiveLane(backlog, ACTOR)).isZero();

        assertThat(titles(backlog)).isEmpty();
        assertThat(service.archivedCards(board.id())).hasSize(2);
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("archived 2 cards from Backlog");
    }

    @Test
    void laneReportsWipLimitBreaches() {
        createCard(inProgress, "1");
        createCard(inProgress, "2");
        assertThat(lane(inProgress).isOverWipLimit()).isFalse();

        createCard(inProgress, "3");
        createCard(inProgress, "4");
        assertThat(lane(inProgress).isOverWipLimit()).isTrue();
    }

    @Test
    void deletingBoardRemovesEverything() {
        createCard(backlog, "Gone soon");

        service.deleteBoard(board.id(), ACTOR);

        assertThat(service.findBoard(board.id())).isEmpty();
        assertThat(service.listBoards()).isEmpty();
        assertThat(activityLog.recent(board.id())).isEmpty();
    }

    @Test
    void unknownIdsAreReportedAsNotFound() {
        assertThatThrownBy(() -> service.updateBoard(999_999, new BoardDraft("x", null), ACTOR)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateLane(999_999, new LaneDraft("x", null, false), ACTOR)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteCard(999_999, ACTOR)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.createCard(999_999, draft("x"), ACTOR)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void activityIsRecordedNewestFirst() {
        createCard(backlog, "First");
        createCard(backlog, "Second");

        List<ActivityEntry> recent = activityLog.recent(board.id());

        assertThat(recent).extracting(ActivityEntry::message).containsExactly(
                "added \"Second\" to Backlog",
                "added \"First\" to Backlog",
                "created board \"Launch\" from the Kanban template");
        assertThat(recent).extracting(ActivityEntry::actor).containsOnly(ACTOR);
    }

    @Test
    void changesAreBroadcastToSubscribersAfterCommit() {
        List<BoardChangedEvent> received = new CopyOnWriteArrayList<>();
        Registration subscription = broadcaster.subscribe(received::add);
        try {
            service.createCard(backlog, draft("Announce me"), ACTOR);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received)
                    .anySatisfy(event -> {
                        assertThat(event.boardId()).isEqualTo(board.id());
                        assertThat(event.kind()).isEqualTo(BoardChangedEvent.Kind.CONTENT);
                        assertThat(event.actor()).isEqualTo(ACTOR);
                        assertThat(event.message()).isEqualTo("added \"Announce me\" to Backlog");
                    }));
        } finally {
            subscription.remove();
        }
    }

    // ---------------------------------------------------------------- helpers

    private CardSnapshot createCard(long laneId, String title) {
        return createCard(laneId, title, null);
    }

    private CardSnapshot createCard(long laneId, String title, LocalDate dueDate) {
        return service.createCard(laneId, new CardDraft(title, null, Priority.MEDIUM, dueDate, null, Set.of(), null), ACTOR);
    }

    private static CardDraft draft(String title) {
        return new CardDraft(title, null, null, null, null, null, null);
    }

    private BoardSnapshot reload() {
        return service.findBoard(board.id()).orElseThrow();
    }

    private LaneSnapshot lane(long laneId) {
        return reload().lane(laneId).orElseThrow();
    }

    private List<String> titles(long laneId) {
        return lane(laneId).cards().stream().map(CardSnapshot::title).toList();
    }

    private List<String> laneNames() {
        return reload().lanes().stream().map(LaneSnapshot::name).toList();
    }
}
