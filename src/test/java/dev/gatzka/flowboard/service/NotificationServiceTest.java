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
class NotificationServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private NotificationRepository repository;

    private BoardSnapshot board;
    private long backlog;

    @BeforeEach
    void seed() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        repository.deleteAll();
        board = boardService.createBoard(new BoardDraft("Inbox", null), "Seeder");
        backlog = board.lanes().get(0).id();
    }

    @Test
    void mentionsFindNamesAndIgnoreEmailsAndTrailingPunctuation() {
        assertThat(Mentions.in("Ping @Mara, @jonas.k and @priya_s. Not mail@example.com; @Mara again"))
                .containsExactly("Mara", "jonas.k", "priya_s");
        assertThat(Mentions.in(null)).isEmpty();
        assertThat(Mentions.in("no mentions here")).isEmpty();
    }

    @Test
    void mentionsAndCommentsOnOwnCardNotifyTheRightPeopleOnce() {
        CardSnapshot card = boardService.createCard(backlog, new CardDraft("Review", null, Priority.MEDIUM, null, "Priya", Set.of(), null), "Mara");

        commentService.add(card.id(), "Jonas", "@mara can you look? @Priya fyi");

        assertThat(notifications.recent("mara")).extracting(NotificationSnapshot::message)
                .containsExactly("mentioned you on \"Review\": @mara can you look? @Priya fyi");
        assertThat(notifications.recent("Priya")).extracting(NotificationSnapshot::message)
                .containsExactly("mentioned you on \"Review\": @mara can you look? @Priya fyi", "assigned \"Review\" to you");
        assertThat(notifications.unreadCount("priya")).isEqualTo(2);

        commentService.add(card.id(), "Jonas", "Also, done with the review.");
        assertThat(notifications.recent("Priya").getFirst().message()).isEqualTo("commented on your card \"Review\": Also, done with the review.");
        assertThat(notifications.recent("Jonas")).isEmpty();
    }

    @Test
    void nobodyIsNotifiedAboutTheirOwnActionsAndReassignmentNotifiesTheNewPerson() {
        CardSnapshot card = boardService.createCard(backlog, new CardDraft("Mine", null, Priority.LOW, null, "Mara", Set.of(), null), "Mara");
        assertThat(notifications.recent("Mara")).isEmpty();

        commentService.add(card.id(), "Mara", "@Mara note to self");
        assertThat(notifications.recent("Mara")).isEmpty();

        boardService.updateCard(card.id(), new CardDraft("Mine", null, Priority.LOW, null, "Jonas", Set.of(), null), "Mara");
        assertThat(notifications.recent("Jonas")).extracting(NotificationSnapshot::message).containsExactly("assigned \"Mine\" to you");

        boardService.updateCard(card.id(), new CardDraft("Mine", "edited", Priority.LOW, null, "jonas", Set.of(), null), "Mara");
        assertThat(notifications.recent("Jonas")).as("same assignee spelled differently is no new assignment").hasSize(1);
    }

    @Test
    void readingClearsTheCounterAndDeletingTheBoardClearsTheInbox() {
        CardSnapshot card = boardService.createCard(backlog, CardDraft.titled("x"), "Seeder");
        commentService.add(card.id(), "Jonas", "@Mara one");
        commentService.add(card.id(), "Jonas", "@Mara two");
        assertThat(notifications.unreadCount("Mara")).isEqualTo(2);

        notifications.markRead(notifications.recent("Mara").getFirst().id());
        assertThat(notifications.unreadCount("Mara")).isEqualTo(1);
        notifications.markAllRead("MARA");
        assertThat(notifications.unreadCount("Mara")).isZero();
        assertThat(notifications.recent("Mara")).extracting(NotificationSnapshot::unread).containsOnly(false);

        boardService.deleteBoard(board.id(), "Seeder");
        assertThat(notifications.recent("Mara")).isEmpty();
    }
}
