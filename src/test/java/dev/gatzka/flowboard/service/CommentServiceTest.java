package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.CommentSnapshot;
import dev.gatzka.flowboard.repository.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommentServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private ChecklistService checklistService;

    @Autowired
    private CommentRepository comments;

    @Autowired
    private ActivityLog activityLog;

    private BoardSnapshot board;
    private CardSnapshot card;

    @BeforeEach
    void createCard() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Tester"));
        board = boardService.createBoard(new BoardDraft("Comments", null), "Tester");
        card = boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Discuss"), "Tester");
    }

    @Test
    void commentsAreKeptInOrderWithAuthorAndTime() {
        commentService.add(card.id(), "Mara", "First thought");
        CardSnapshot updated = commentService.add(card.id(), "Jonas", "  Second thought  ");

        assertThat(updated.comments()).extracting(CommentSnapshot::author).containsExactly("Mara", "Jonas");
        assertThat(updated.comments()).extracting(CommentSnapshot::text).containsExactly("First thought", "Second thought");
        assertThat(updated.comments().get(0).createdAt()).isBeforeOrEqualTo(updated.comments().get(1).createdAt());
        assertThat(updated.hasComments()).isTrue();
        assertThat(updated.updatedAt()).isAfterOrEqualTo(card.updatedAt());
    }

    @Test
    void commentingShowsUpInBoardAndCardHistoryWithAnExcerpt() {
        String longText = "This comment is deliberately long so that the activity feed only shows the beginning of it and not the whole thing.";
        commentService.add(card.id(), "Mara", longText);

        ActivityEntry latest = activityLog.recent(board.id()).getFirst();
        assertThat(latest.actor()).isEqualTo("Mara");
        assertThat(latest.message()).startsWith("commented on \"Discuss\": This comment is deliberately long").endsWith("…");
        assertThat(latest.message().length()).isLessThan(longText.length());

        assertThat(activityLog.recentForCard(card.id())).extracting(ActivityEntry::message)
                .containsExactly(latest.message(), "added \"Discuss\" to Backlog");
    }

    @Test
    void commentsAreVisibleInTheBoardSnapshotAndDieWithTheCard() {
        commentService.add(card.id(), "Mara", "Hello");
        CardSnapshot fromBoard = boardService.findBoard(board.id()).orElseThrow().lanes().get(0).cards().getFirst();
        assertThat(fromBoard.comments()).extracting(CommentSnapshot::text).containsExactly("Hello");

        boardService.deleteCard(card.id(), "Tester");
        assertThat(comments.count()).isZero();
    }

    @Test
    void loadingTagsChecklistAndCommentsTogetherDoesNotMultiplyAnything() {
        boardService.updateCard(card.id(), new CardDraft("Discuss", null, null, null, null, java.util.Set.of("ui", "feature"), null), "Tester");
        checklistService.addItem(card.id(), "one", "Tester");
        checklistService.addItem(card.id(), "two", "Tester");
        checklistService.addItem(card.id(), "three", "Tester");
        commentService.add(card.id(), "Mara", "first");
        commentService.add(card.id(), "Jonas", "second");

        CardSnapshot loaded = boardService.findBoard(board.id()).orElseThrow().lanes().get(0).cards().getFirst();

        assertThat(loaded.tags()).containsExactly("feature", "ui");
        assertThat(loaded.checklist()).extracting(c -> c.text()).containsExactly("one", "two", "three");
        assertThat(loaded.comments()).extracting(CommentSnapshot::text).containsExactly("first", "second");
    }

    @Test
    void blankCommentsAndUnknownCardsAreRejected() {
        assertThatThrownBy(() -> commentService.add(card.id(), "Mara", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> commentService.add(999_999, "Mara", "x")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void excerptCollapsesWhitespaceAndTruncates() {
        assertThat(CommentService.excerpt("short   text\nhere")).isEqualTo("short text here");
        String excerpt = CommentService.excerpt("x".repeat(100));
        assertThat(excerpt).hasSize(60).endsWith("…");
    }
}
