package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneRuleSnapshot;
import dev.gatzka.flowboard.domain.NotificationSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.RuleAction;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AutomationServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private AutomationService automations;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private ActivityLog activityLog;

    @Test
    void rulesRunWhenCardsEnterTheLane() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Rules", null), "Mara");
        long backlog = board.lanes().get(0).id();
        long review = board.lanes().get(2).id();
        automations.addRule(review, RuleAction.ASSIGN, " Priya ", "Mara");
        automations.addRule(review, RuleAction.ADD_TAG, "qa", "Mara");
        automations.addRule(review, RuleAction.SET_PRIORITY, "High", "Mara");
        automations.addRule(review, RuleAction.DUE_IN_DAYS, "2", "Mara");
        automations.addRule(review, RuleAction.NOTIFY, "Jonas", "Mara");
        assertThat(automations.rulesFor(review)).extracting(LaneRuleSnapshot::describe)
                .containsExactly("Assign to Priya", "Add the tag qa", "Priority High", "Due in 2 days", "Notify Jonas");

        CardSnapshot card = boardService.createCard(backlog, new CardDraft("Check it", null, Priority.LOW, null, null, Set.of("ui"), null), "Mara");
        boardService.moveCard(card.id(), review, 0, "Mara");

        CardSnapshot after = boardService.findBoard(board.id()).orElseThrow().lane(review).orElseThrow().cards().getFirst();
        assertThat(after.assignee()).isEqualTo("Priya");
        assertThat(after.tags()).containsExactly("qa", "ui");
        assertThat(after.priority()).isEqualTo(Priority.HIGH);
        assertThat(after.dueDate()).isEqualTo(LocalDate.now().plusDays(2));
        assertThat(notifications.recent("Jonas")).extracting(NotificationSnapshot::message).contains("\"Check it\" entered Review");
        assertThat(notifications.recent("Priya")).extracting(NotificationSnapshot::message).contains("assigned \"Check it\" to you");
        assertThat(activityLog.recent(board.id()).getFirst().message())
                .isEqualTo("applied the rules of Review to \"Check it\": assigned to Priya, tagged qa, priority high, due " + LocalDate.now().plusDays(2) + ", notified Jonas");

        CardSnapshot direct = boardService.createCard(review, CardDraft.titled("Born here"), "Mara");
        assertThat(direct.assignee()).as("rules also run for cards created in the lane").isEqualTo("Priya");

        automations.addRule(review, RuleAction.CLEAR_DUE, null, "Mara");
        boardService.moveCard(card.id(), backlog, 0, "Mara");
        boardService.moveCard(card.id(), review, 0, "Mara");
        assertThat(boardService.findBoard(board.id()).orElseThrow().lane(review).orElseThrow().cards())
                .filteredOn(c -> c.id() == card.id()).first().extracting(CardSnapshot::dueDate).isNull();

        LaneRuleSnapshot tag = automations.rulesFor(review).get(1);
        automations.removeRule(tag.id(), "Mara");
        automations.removeRule(tag.id(), "Mara");
        assertThat(automations.rulesFor(review)).hasSize(5);
    }

    @Test
    void requireRulesChangeNothingWhenCardsEnter() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Mara"));
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Policies", null), "Mara");
        long backlog = board.lanes().get(0).id();
        long review = board.lanes().get(2).id();
        LaneRuleSnapshot rule = automations.addRule(review, RuleAction.REQUIRE, "Due date", "Mara");
        assertThat(rule.argument()).isEqualTo("DUE_DATE");
        assertThat(rule.describe()).isEqualTo("Requires a due date");
        assertThatThrownBy(() -> automations.addRule(review, RuleAction.REQUIRE, "vibes", "Mara")).isInstanceOf(IllegalArgumentException.class);

        CardSnapshot card = boardService.createCard(backlog, CardDraft.titled("Undated"), "Mara");
        boardService.moveCard(card.id(), review, 0, "Mara");

        CardSnapshot moved = boardService.findBoard(board.id()).orElseThrow().lane(review).orElseThrow().cards().getFirst();
        assertThat(moved.dueDate()).isNull();
        assertThat(activityLog.recent(board.id()).getFirst().message()).startsWith("moved");
    }

    @Test
    void validatesRulesAndArchivesStaleCards() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Tidy", null), "Mara");
        long done = board.lanes().get(3).id();
        assertThatThrownBy(() -> automations.addRule(done, RuleAction.ASSIGN, " ", "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> automations.addRule(done, RuleAction.SET_PRIORITY, "sky-high", "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> automations.addRule(done, RuleAction.DUE_IN_DAYS, "soon", "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> automations.addRule(999_999L, RuleAction.CLEAR_DUE, null, "Mara")).isInstanceOf(NotFoundException.class);
        assertThat(automations.addRule(done, RuleAction.CLEAR_DUE, "ignored", "Mara").argument()).isNull();

        automations.addRule(done, RuleAction.ARCHIVE_AFTER_DAYS, "14", "Mara");
        CardSnapshot fresh = boardService.createCard(done, CardDraft.titled("Fresh"), "Mara");
        CardSnapshot old = boardService.createCard(done, CardDraft.titled("Old"), "Mara");

        assertThat(automations.archiveStale(Instant.now())).isZero();
        assertThat(automations.archiveStale(Instant.now().plus(Duration.ofDays(15)))).as("both are older than 14 days by then").isEqualTo(2);
        assertThat(boardService.archivedCards(board.id())).extracting(CardSnapshot::title).containsExactlyInAnyOrder("Fresh", "Old");
        assertThat(boardService.findBoard(board.id()).orElseThrow().lane(done).orElseThrow().cards()).isEmpty();
        assertThat(activityLog.recent(board.id()).getFirst().actor()).isEqualTo(ActivityLog.SYSTEM_ACTOR);
        assertThat(activityLog.recent(board.id()).getFirst().message()).contains("after 14 days in Done");
        assertThat(fresh.id()).isNotEqualTo(old.id());
    }
}
