package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.NotificationSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.repository.NotificationRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WatchServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private VoteService voteService;

    @Autowired
    private WatchService watches;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private NotificationRepository notificationRepository;

    private BoardSnapshot board;
    private long backlog;

    @BeforeEach
    void seed() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        notificationRepository.deleteAll();
        board = boardService.createBoard(new BoardDraft("Watched", null), "Seeder");
        backlog = board.lanes().get(0).id();
    }

    @Test
    void watchersHearAboutNoteworthyChangesButNotTheirOwn() {
        CardSnapshot card = boardService.createCard(backlog, CardDraft.titled("Followed"), "Seeder");
        assertThat(watches.toggle(card.id(), "Priya")).isTrue();
        assertThat(watches.isWatching(card.id(), "priya")).isTrue();
        assertThat(watches.watch(card.id(), "PRIYA")).as("already watching").isFalse();
        assertThat(watches.watchedOn(board.id(), "Priya")).containsExactly(card.id());

        boardService.moveCard(card.id(), board.lanes().get(1).id(), 0, "Jonas");
        voteService.toggle(card.id(), "Jonas");
        boardService.updateCard(card.id(), new CardDraft("Followed!", null, Priority.HIGH, null, null, Set.of(), null), "Priya");

        assertThat(notifications.recent("Priya")).extracting(NotificationSnapshot::message, NotificationSnapshot::actor)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("moved \"Followed\" to In progress", "Jonas"));

        assertThat(watches.toggle(card.id(), "Priya")).isFalse();
        boardService.moveCard(card.id(), backlog, 0, "Jonas");
        assertThat(notifications.recent("Priya")).hasSize(1);
        assertThat(watches.watchedOn(board.id(), "Priya")).isEmpty();
    }

    @Test
    void assigneesAndCommentersWatchAutomaticallyWithoutDoubleNotifications() {
        CardSnapshot card = boardService.createCard(backlog, new CardDraft("Team card", null, Priority.LOW, null, "Mara", Set.of(), null), "Seeder");
        assertThat(watches.watchers(card.id())).containsExactly("mara");

        commentService.add(card.id(), "Jonas", "Looking at it");
        assertThat(watches.watchers(card.id())).containsExactly("jonas", "mara");
        assertThat(notifications.recent("Mara")).extracting(NotificationSnapshot::message)
                .as("the assignee is told once, as the assignee, not again as a watcher")
                .containsExactly("commented on your card \"Team card\": Looking at it", "assigned \"Team card\" to you");
        assertThat(notifications.recent("Jonas")).isEmpty();

        commentService.add(card.id(), "Priya", "Me too");
        assertThat(notifications.recent("Jonas")).extracting(NotificationSnapshot::message)
                .containsExactly("commented on \"Team card\": Me too");

        boardService.deleteCard(card.id(), "Seeder");
        assertThat(watches.watchers(card.id())).isEmpty();
    }

    @Test
    void onlySomeMessagesAreWorthAnInterruption() {
        assertThat(WatcherNotifier.isNoteworthy("moved \"x\" to Done")).isTrue();
        assertThat(WatcherNotifier.isNoteworthy("commented on \"x\": hi")).isTrue();
        assertThat(WatcherNotifier.isNoteworthy("voted for \"x\"")).isFalse();
        assertThat(WatcherNotifier.isNoteworthy("started a timer on \"x\"")).isFalse();
        assertThat(WatcherNotifier.isNoteworthy(null)).isFalse();
        assertThat(watches.watch(1, ActivityLog.SYSTEM_ACTOR)).isFalse();
        assertThat(watches.watch(1, " ")).isFalse();
    }
}
