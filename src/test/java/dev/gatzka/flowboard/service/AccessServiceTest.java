package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.security.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccessServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private AccessService access;

    @Autowired
    private AccountService accounts;

    @Autowired
    private OverviewService overview;

    @BeforeEach
    void clear() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        if (accounts.find("lena").isEmpty()) {
            accounts.register("lena", "Lena", "longenough");
        }
        if (accounts.find("tom").isEmpty()) {
            accounts.register("tom", "Tom", "longenough");
        }
    }

    @Test
    void boardsAreOpenUntilRestrictedToMembers() {
        BoardSnapshot open = boardService.createBoard(new BoardDraft("Open", null), "Lena");
        BoardSnapshot secret = boardService.createBoard(new BoardDraft("Secret", null), "Lena");
        CardSnapshot card = boardService.createCard(secret.lanes().get(0).id(), CardDraft.titled("Hidden"), "Lena");
        assertThat(access.canAccess(secret.id(), "tom")).isTrue();
        assertThat(access.canAccess(secret.id(), null)).isTrue();

        access.setMembersOnly(secret.id(), true, "Lena", "Lena");

        assertThat(access.access(secret.id()).membersOnly()).isTrue();
        assertThat(access.access(secret.id()).members()).containsExactly("lena");
        assertThat(access.canAccess(secret.id(), "LENA")).isTrue();
        assertThat(access.canAccess(secret.id(), "tom")).isFalse();
        assertThat(access.canAccess(secret.id(), null)).isFalse();
        assertThat(access.canAccess(open.id(), "tom")).isTrue();
        assertThat(access.canAccessCard(card.id(), "tom")).isFalse();
        assertThat(access.canAccessCard(card.id(), "lena")).isTrue();
        assertThat(access.canAccessCard(999_999L, "lena")).isFalse();
        assertThat(access.visibleBoards("tom")).extracting(BoardSummary::name).containsExactly("Open");
        assertThat(access.visibleBoards("lena")).extracting(BoardSummary::name).containsExactly("Open", "Secret");
        assertThat(access.visibleBoards(null)).extracting(BoardSummary::name).containsExactly("Open");
        assertThat(access.visibleBoards("lena").get(1).membersOnly()).isTrue();
        assertThat(overview.overview(access.visibleBoardIds("tom")).boards()).isEqualTo(1);
        assertThat(overview.overview(access.visibleBoardIds("tom")).openCards()).isZero();

        access.addMember(secret.id(), "Tom", "Lena");
        access.addMember(secret.id(), "tom", "Lena");
        assertThat(access.access(secret.id()).members()).containsExactly("lena", "tom");
        assertThat(access.canAccess(secret.id(), "tom")).isTrue();
        assertThatThrownBy(() -> access.addMember(secret.id(), "nobody", "Lena")).isInstanceOf(IllegalArgumentException.class);

        access.removeMember(secret.id(), "lena", "Tom");
        assertThat(access.access(secret.id()).members()).containsExactly("tom");
        assertThatThrownBy(() -> access.removeMember(secret.id(), "tom", "Tom")).as("the last member stays")
                .isInstanceOf(IllegalArgumentException.class);
        access.removeMember(secret.id(), "ghost", "Tom");

        access.setMembersOnly(secret.id(), false, "tom", "Tom");
        assertThat(access.canAccess(secret.id(), "lena")).isTrue();
        access.removeMember(secret.id(), "tom", "Tom");
        assertThat(access.access(secret.id()).members()).isEmpty();

        boardService.deleteBoard(secret.id(), "Tom");
        assertThat(access.canAccess(secret.id(), "tom")).isFalse();
    }
}
