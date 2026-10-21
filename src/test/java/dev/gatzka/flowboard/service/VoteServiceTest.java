package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

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
class VoteServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private VoteService voteService;

    @Autowired
    private ActivityLog activityLog;

    private BoardSnapshot board;
    private long backlog;

    @BeforeEach
    void seed() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        board = boardService.createBoard(new BoardDraft("Votes", null), "Seeder");
        backlog = board.lanes().get(0).id();
    }

    @Test
    void votesToggleOncePerPersonAndStayOutOfTheActivityFeed() {
        CardSnapshot card = boardService.createCard(backlog, CardDraft.titled("Idea"), "Seeder");
        int activityBefore = activityLog.recent(board.id()).size();

        assertThat(voteService.toggle(card.id(), "Mara").votes()).isEqualTo(1);
        assertThat(voteService.toggle(card.id(), "Jonas").votes()).isEqualTo(2);
        CardSnapshot withdrawn = voteService.toggle(card.id(), "Mara");

        assertThat(withdrawn.votes()).isEqualTo(1);
        assertThat(withdrawn.hasVoteFrom("jonas")).isTrue();
        assertThat(withdrawn.hasVoteFrom("Mara")).isFalse();
        assertThat(boardService.findBoard(board.id()).orElseThrow().lanes().get(0).cards().getFirst().voters()).containsExactly("Jonas");
        assertThat(activityLog.recent(board.id())).hasSize(activityBefore);
    }

    @Test
    void laneCanBeSortedByVotesKeepingTiesInPlace() {
        CardSnapshot a = boardService.createCard(backlog, CardDraft.titled("a"), "Seeder");
        CardSnapshot b = boardService.createCard(backlog, CardDraft.titled("b"), "Seeder");
        CardSnapshot c = boardService.createCard(backlog, CardDraft.titled("c"), "Seeder");
        voteService.toggle(c.id(), "Mara");
        voteService.toggle(c.id(), "Jonas");
        voteService.toggle(b.id(), "Mara");

        boardService.sortLaneByVotes(backlog, "Seeder");

        assertThat(boardService.findBoard(board.id()).orElseThrow().lanes().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("c", "b", "a");
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("sorted Backlog by votes");
        assertThat(a.votes()).isZero();
    }
}
