package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.NotificationSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ReminderServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private ReminderService reminders;

    @Autowired
    private NotificationService notifications;

    @BeforeEach
    void clear() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
    }

    @Test
    void remindsAssigneesOnceADayAboutDueAndOverdueCards() {
        LocalDate today = LocalDate.now();
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Reminders", null), "Seeder");
        long backlog = board.lanes().get(0).id();
        long done = board.lanes().get(3).id();
        card(backlog, "Due today", today, "Mara");
        card(backlog, "Three days late", today.minusDays(3), "Mara");
        card(backlog, "One day late", today.minusDays(1), "Jonas");
        card(backlog, "Next week", today.plusDays(7), "Jonas");
        card(backlog, "Nobody's", today.minusDays(5), null);
        CardSnapshot finished = card(backlog, "Finished late", today.minusDays(2), "Priya");
        boardService.moveCard(finished.id(), done, 0, "Seeder");

        assertThat(reminders.remind(today)).isEqualTo(3);
        assertThat(reminders("Mara")).extracting(NotificationSnapshot::message)
                .containsExactlyInAnyOrder("\"Due today\" is due today", "\"Three days late\" is 3 days overdue");
        assertThat(reminders("jonas")).extracting(NotificationSnapshot::message).containsExactly("\"One day late\" is 1 day overdue");
        assertThat(reminders("Priya")).isEmpty();
        assertThat(reminders("Mara").getFirst().boardId()).isEqualTo(board.id());

        assertThat(reminders.remind(today)).as("same day again: nothing new").isZero();
        assertThat(reminders("Mara")).hasSize(2);
        assertThat(reminders.remind(today.plusDays(1))).as("a new day reminds again, including the one due tomorrow-today").isEqualTo(3);
    }

    private java.util.List<NotificationSnapshot> reminders(String person) {
        return notifications.recent(person).stream().filter(n -> ActivityLog.SYSTEM_ACTOR.equals(n.actor())).toList();
    }

    private CardSnapshot card(long laneId, String title, LocalDate due, String assignee) {
        return boardService.createCard(laneId, new CardDraft(title, null, Priority.MEDIUM, due, assignee, Set.of(), null), "Seeder");
    }
}
