package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class VisitServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private VisitService visits;

    @BeforeEach
    void clear() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
    }

    @Test
    void countsOtherPeoplesActivitySinceTheLastVisit() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Team", null), "Jonas");
        boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("One"), "Jonas");
        boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Mine"), "Mara");

        assertThat(visits.unreadCounts("mara", "Mara")).as("never visited: everything by others is new")
                .isEqualTo(Map.of(board.id(), 2L));
        assertThat(visits.unreadCounts(null, "Mara")).as("anonymous still sees counts").isEqualTo(Map.of(board.id(), 2L));

        assertThat(visits.visit("mara", board.id())).isEmpty();
        assertThat(visits.unreadCounts("mara", "Mara")).isEmpty();

        boardService.createCard(board.lanes().get(1).id(), CardDraft.titled("Two"), "Jonas");
        boardService.createCard(board.lanes().get(1).id(), CardDraft.titled("Also mine"), "Mara");
        assertThat(visits.unreadCounts("mara", "Mara")).isEqualTo(Map.of(board.id(), 1L));
        assertThat(visits.unreadCounts("jonas", "Jonas")).isEqualTo(Map.of(board.id(), 2L));

        Instant previous = visits.visit("mara", board.id()).orElseThrow();
        assertThat(visits.lastVisit("mara", board.id())).hasValueSatisfying(now -> assertThat(now).isAfterOrEqualTo(previous));
        assertThat(visits.unreadCounts("mara", "Mara")).isEmpty();
    }

    @Test
    void deletingABoardForgetsItsVisits() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Gone", null), "Jonas");
        visits.visit("mara", board.id());
        boardService.deleteBoard(board.id(), "Jonas");
        assertThat(visits.lastVisit("mara", board.id())).isEmpty();
        assertThat(visits.unreadCounts("mara", "Mara")).isEmpty();
    }
}
