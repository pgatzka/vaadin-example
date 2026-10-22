package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.NotificationSnapshot;
import dev.gatzka.flowboard.domain.TimerSnapshot;
import dev.gatzka.flowboard.domain.WorkEntrySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TimeServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private TimeService time;

    @Autowired
    private ActivityLog activityLog;

    @Autowired
    private NotificationService notifications;

    @Test
    void logsRemovesAndSumsWork() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Time", null), "Mara");
        CardSnapshot card = boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Sync"), "Mara");

        CardSnapshot after = time.log(card.id(), "Mara", 90, "  pairing ", "Mara");
        time.log(card.id(), "Jonas", 15, null, "Jonas");
        assertThat(after.workedMinutes()).isEqualTo(90);
        assertThat(workedMinutes(board, card.id())).isEqualTo(105);
        assertThat(time.entries(card.id())).extracting(WorkEntrySnapshot::person).containsExactly("Jonas", "Mara");
        assertThat(time.entries(card.id()).get(1).note()).isEqualTo("pairing");
        assertThat(activityLog.recent(board.id()).get(1).message()).isEqualTo("logged 1 h 30 min on \"Sync\"");

        long entry = time.entries(card.id()).get(1).id();
        assertThat(time.removeEntry(entry, "Mara").workedMinutes()).isEqualTo(15);
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("removed 1 h 30 min logged on \"Sync\"");

        assertThatThrownBy(() -> time.log(card.id(), "Mara", 0, null, "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> time.log(card.id(), "Mara", TimeService.MAX_MINUTES + 1, null, "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> time.log(card.id(), "Mara", 5, "x".repeat(201), "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> time.removeEntry(entry, "Mara")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void onePersonHasOneTimerAndStoppingLogsAtLeastAMinute() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Time", null), "Mara");
        CardSnapshot first = boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("First"), "Mara");
        CardSnapshot second = boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Second"), "Mara");
        assertThat(time.running("Mara")).isEmpty();
        assertThat(time.running(null)).isEmpty();
        assertThat(time.stop("Mara", null)).isEmpty();

        TimerSnapshot timer = time.start(first.id(), "Mara");
        assertThat(timer.cardId()).isEqualTo(first.id());
        assertThat(timer.boardId()).isEqualTo(board.id());
        assertThat(timer.cardTitle()).isEqualTo("First");
        assertThat(time.running("Mara")).map(TimerSnapshot::cardId).contains(first.id());

        time.start(second.id(), "Mara");
        assertThat(time.running("Mara")).map(TimerSnapshot::cardId).as("switching cards stops the first timer").contains(second.id());
        assertThat(workedMinutes(board, first.id())).isEqualTo(1);

        CardSnapshot stopped = time.stop("Mara", "done").orElseThrow();
        assertThat(stopped.id()).isEqualTo(second.id());
        assertThat(stopped.workedMinutes()).isEqualTo(1);
        assertThat(time.entries(second.id()).getFirst().note()).isEqualTo("done");
        assertThat(time.running("Mara")).isEmpty();
    }

    private int workedMinutes(BoardSnapshot board, long cardId) {
        return boardService.findBoard(board.id()).orElseThrow().lanes().stream().flatMap(lane -> lane.cards().stream())
                .filter(card -> card.id() == cardId).findFirst().orElseThrow().workedMinutes();
    }

    @Test
    void humanizesMinutes() {
        assertThat(TimeService.humanize(45)).isEqualTo("45 min");
        assertThat(TimeService.humanize(120)).isEqualTo("2 h");
        assertThat(TimeService.humanize(150)).isEqualTo("2 h 30 min");
    }

    @Test
    void focusSessionsStopThemselvesLogTheTimeAndNotifyTheirOwner() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Focus", null), "Mara");
        CardSnapshot card = boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Deep work"), "Mara");

        TimerSnapshot session = time.startFocus(card.id(), "Mara", 25);

        assertThat(session.isFocusSession()).isTrue();
        assertThat(session.endsAt()).isEqualTo(session.startedAt().plusSeconds(25 * 60));
        assertThat(time.running("Mara")).map(TimerSnapshot::endsAt).hasValue(session.endsAt());
        assertThat(time.finishFocusSessions(session.endsAt().minusSeconds(1))).as("not due yet").isZero();
        assertThat(time.running("Mara")).isPresent();

        assertThat(time.finishFocusSessions(session.endsAt())).isEqualTo(1);

        assertThat(time.running("Mara")).isEmpty();
        assertThat(time.entries(card.id())).singleElement().satisfies(entry -> {
            assertThat(entry.minutes()).isEqualTo(25);
            assertThat(entry.note()).isEqualTo(TimeService.FOCUS_NOTE);
        });
        assertThat(time.focusSessionsToday("Mara")).isEqualTo(1);
        assertThat(notifications.recent("Mara")).extracting(NotificationSnapshot::message)
                .anyMatch(message -> message.startsWith("Focus session on \"Deep work\" done: 25 min logged, 1 session today"));

        assertThatThrownBy(() -> time.startFocus(card.id(), "Mara", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> time.startFocus(card.id(), "Mara", TimeService.MAX_FOCUS_MINUTES + 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
