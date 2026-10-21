package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.ActivityPulse;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardInsights;
import dev.gatzka.flowboard.domain.BoardInsights.AssigneeLoad;
import dev.gatzka.flowboard.domain.BoardInsights.LaneLoad;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneDraft;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InsightsServiceTest {

    private static final String ACTOR = "Tester";

    @Autowired
    private BoardService boardService;

    @Autowired
    private InsightsService insightsService;

    @Autowired
    private CardRepository cards;

    private BoardSnapshot board;
    private long backlog;
    private long inProgress;
    private long done;

    @BeforeEach
    void createBoard() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), ACTOR));
        board = boardService.createBoard(new BoardDraft("Metrics", null), ACTOR);
        backlog = board.lanes().get(0).id();
        inProgress = board.lanes().get(1).id();
        done = board.lanes().get(3).id();
    }

    @Test
    void emptyBoardHasZeroesAndNoCycleTime() {
        BoardInsights insights = insightsService.forBoard(board.id());

        assertThat(insights.totalCards()).isZero();
        assertThat(insights.averageCycleTime()).isEmpty();
        assertThat(insights.lanes()).extracting(LaneLoad::name).containsExactly("Backlog", "In progress", "Review", "Done");
        assertThat(insights.completedPerDay()).hasSize(InsightsService.HISTORY_DAYS);
        assertThat(insights.completedPerDay().getLast().day()).isEqualTo(LocalDate.now());
    }

    @Test
    void countsOpenOverdueDueSoonAndCompletedWork() {
        LocalDate today = LocalDate.now();
        card(backlog, "late", Priority.HIGH, today.minusDays(1), "Mara");
        card(backlog, "soon", Priority.LOW, today.plusDays(3), "Mara");
        card(inProgress, "far", Priority.URGENT, today.plusDays(30), "Jonas");
        card(inProgress, "undated", Priority.MEDIUM, null, null);
        card(done, "finished", Priority.HIGH, today.minusDays(5), "Jonas");

        BoardInsights insights = insightsService.forBoard(board.id());

        assertThat(insights.totalCards()).isEqualTo(5);
        assertThat(insights.completedCards()).isEqualTo(1);
        assertThat(insights.openCards()).isEqualTo(4);
        assertThat(insights.overdueCards()).as("a finished card is never overdue").isEqualTo(1);
        assertThat(insights.dueThisWeek()).isEqualTo(1);
        assertThat(insights.completedLast7Days()).isEqualTo(1);
        assertThat(insights.averageCycleTime()).isPresent();
        assertThat(insights.completedPerDay().getLast().count()).isEqualTo(1);
        assertThat(insights.completionRatio()).isEqualTo(0.2);
        assertThat(insights.openByPriority()).containsEntry(Priority.HIGH, 1).containsEntry(Priority.LOW, 1)
                .containsEntry(Priority.URGENT, 1).containsEntry(Priority.MEDIUM, 1);
        assertThat(insights.openByAssignee()).containsExactly(new AssigneeLoad("Mara", 2), new AssigneeLoad("Jonas", 1));
        assertThat(insights.lanes()).extracting(LaneLoad::cards).containsExactly(2, 2, 0, 1);
        assertThat(insights.lanes().get(0).averageTimeInLane()).isPresent();
        assertThat(insights.lanes().get(2).averageTimeInLane()).isEmpty();
        assertThat(insights.busiestLaneCount()).isEqualTo(2);
    }

    @Test
    void pointsAndVelocityComeFromEstimates() {
        boardService.createCard(backlog, new CardDraft("five", null, null, null, null, null, 5), ACTOR);
        boardService.createCard(inProgress, new CardDraft("three", null, null, null, null, null, 3), ACTOR);
        boardService.createCard(inProgress, CardDraft.titled("unestimated"), ACTOR);
        boardService.createCard(done, new CardDraft("done eight", null, null, null, null, null, 8), ACTOR);

        BoardInsights insights = insightsService.forBoard(board.id());

        assertThat(insights.openPoints()).isEqualTo(8);
        assertThat(insights.velocityPerWeek()).isEqualTo(2.0);
        assertThat(boardService.findBoard(board.id()).orElseThrow().lane(inProgress).orElseThrow().totalEstimate()).isEqualTo(3);
        assertThat(boardService.findBoard(board.id()).orElseThrow().lane(board.lanes().get(2).id()).orElseThrow().hasEstimates()).isFalse();
    }

    @Test
    void estimatesMustBeWithinRange() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CardDraft("x", null, null, null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CardDraft("x", null, null, null, null, null, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completionTimestampFollowsTheCardIntoAndOutOfDoneLanes() {
        CardSnapshot card = card(backlog, "travels", Priority.MEDIUM, null, null);
        assertThat(cards.findById(card.id()).orElseThrow().getCompletedAt()).isNull();

        boardService.moveCard(card.id(), done, 0, ACTOR);
        assertThat(cards.findById(card.id()).orElseThrow().getCompletedAt()).isNotNull();

        boardService.moveCard(card.id(), inProgress, 0, ACTOR);
        assertThat(cards.findById(card.id()).orElseThrow().getCompletedAt()).isNull();
    }

    @Test
    void markingALaneAsDoneCompletesItsCards() {
        CardSnapshot card = card(inProgress, "in review", Priority.MEDIUM, null, null);

        boardService.updateLane(inProgress, new LaneDraft("In progress", 3, true), ACTOR);
        assertThat(cards.findById(card.id()).orElseThrow().getCompletedAt()).isNotNull();
        assertThat(insightsService.forBoard(board.id()).completedCards()).isEqualTo(1);

        boardService.updateLane(inProgress, new LaneDraft("In progress", 3, false), ACTOR);
        assertThat(cards.findById(card.id()).orElseThrow().getCompletedAt()).isNull();
    }

    private CardSnapshot card(long laneId, String title, Priority priority, LocalDate due, String assignee) {
        return boardService.createCard(laneId, new CardDraft(title, null, priority, due, assignee, Set.of(), null), ACTOR);
    }
    @Test
    void pulseCountsChangesPerDayAndPerPerson() {
        BoardSnapshot pulsed = boardService.createBoard(new BoardDraft("Pulse", null), "Mara");
        long lane = pulsed.lanes().get(0).id();
        boardService.createCard(lane, CardDraft.titled("One"), "Mara");
        boardService.createCard(lane, CardDraft.titled("Two"), "Jonas");
        boardService.createCard(lane, CardDraft.titled("Three"), "Mara");
        boardService.createCard(lane, CardDraft.titled("Robot"), ActivityLog.SYSTEM_ACTOR);

        ActivityPulse pulse = insightsService.pulse(pulsed.id());

        assertThat(pulse.days()).hasSize(7 * ActivityPulse.WEEKS);
        assertThat(pulse.days().getLast().day()).isEqualTo(LocalDate.now());
        assertThat(pulse.days().getLast().count()).as("board creation plus four cards").isEqualTo(5);
        assertThat(pulse.totalChanges()).isEqualTo(5);
        assertThat(pulse.activeDays()).isEqualTo(1);
        assertThat(pulse.currentStreak()).isEqualTo(1);
        assertThat(pulse.contributors()).extracting(ActivityPulse.Contributor::name).containsExactly("Mara", "Jonas");
        assertThat(pulse.contributors().getFirst().changes()).isEqualTo(3);
        assertThat(pulse.contributors().getFirst().lastActive()).isNotNull();
        assertThat(insightsService.pulse(999_999L).isQuiet()).isTrue();
    }
}
