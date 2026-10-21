package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SharingServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private SharingService sharing;

    @Autowired
    private ActivityLog activityLog;

    @Test
    void linksAreOffByDefaultAndMintedOnDemand() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Shared", null), "Mara");
        assertThat(sharing.token(board.id())).isEmpty();
        assertThat(sharing.findShared("nope")).isEmpty();
        assertThat(sharing.findShared(null)).isEmpty();

        String token = sharing.enable(board.id(), "Mara");
        assertThat(token).hasSizeGreaterThanOrEqualTo(20).matches("[A-Za-z0-9_-]+");
        assertThat(sharing.enable(board.id(), "Mara")).as("enabling twice keeps the token").isEqualTo(token);
        assertThat(sharing.token(board.id())).contains(token);
        assertThat(sharing.findShared(token)).map(BoardSnapshot::id).contains(board.id());
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("turned the public link on");
    }

    @Test
    void turningTheLinkOffInvalidatesItForGood() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Shared", null), "Mara");
        String token = sharing.enable(board.id(), "Mara");

        sharing.disable(board.id(), "Mara");
        sharing.disable(board.id(), "Mara");

        assertThat(sharing.findShared(token)).isEmpty();
        assertThat(sharing.token(board.id())).isEmpty();
        assertThat(sharing.enable(board.id(), "Mara")).isNotEqualTo(token);
        assertThat(activityLog.recent(board.id()).stream().filter(entry -> entry.message().contains("public link"))).hasSize(3);
    }

    @Test
    void deletedBoardsAreGone() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Shared", null), "Mara");
        String token = sharing.enable(board.id(), "Mara");
        boardService.deleteBoard(board.id(), "Mara");

        assertThat(sharing.findShared(token)).isEmpty();
        assertThatThrownBy(() -> sharing.token(board.id())).isInstanceOf(NotFoundException.class);
    }
}
