package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.MilestoneDraft;
import dev.gatzka.flowboard.domain.MilestoneProgress;
import dev.gatzka.flowboard.domain.MilestoneSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MilestoneServiceTest {

    private static final String ACTOR = "Tester";

    @Autowired
    private BoardService boardService;

    @Autowired
    private MilestoneService milestones;

    @Autowired
    private ActivityLog activityLog;

    private BoardSnapshot board;
    private long backlog;
    private long done;

    @BeforeEach
    void createBoard() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), ACTOR));
        board = boardService.createBoard(new BoardDraft("Roadmap", null), ACTOR);
        backlog = board.lanes().get(0).id();
        done = board.lanes().get(3).id();
    }

    @Test
    void milestonesAreOrderedByDateThenNameAndCanBeChanged() {
        milestones.create(board.id(), new MilestoneDraft("Someday", null), ACTOR);
        milestones.create(board.id(), new MilestoneDraft("Beta", LocalDate.of(2030, 3, 1)), ACTOR);
        MilestoneSnapshot alpha = milestones.create(board.id(), new MilestoneDraft("  Alpha  ", LocalDate.of(2030, 1, 15)), ACTOR);

        assertThat(milestones.forBoard(board.id())).extracting(MilestoneSnapshot::name).containsExactly("Alpha", "Beta", "Someday");
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("added the milestone \"Alpha\"");

        milestones.update(alpha.id(), new MilestoneDraft("Alpha 2", LocalDate.of(2030, 4, 1)), ACTOR);
        assertThat(milestones.forBoard(board.id())).extracting(MilestoneSnapshot::name).containsExactly("Beta", "Alpha 2", "Someday");
        assertThatThrownBy(() -> new MilestoneDraft("   ", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cardsJoinAndLeaveMilestonesAndDeletionUnlinksThem() {
        MilestoneSnapshot launch = milestones.create(board.id(), new MilestoneDraft("Launch", LocalDate.now().plusDays(14)), ACTOR);
        CardSnapshot card = boardService.createCard(backlog, CardDraft.titled("Ship it"), ACTOR);

        milestones.assign(card.id(), launch.id(), ACTOR);
        assertThat(cardById(card.id()).milestoneId()).isEqualTo(launch.id());
        assertThat(activityLog.recentForCard(card.id()).getFirst().message()).isEqualTo("put \"Ship it\" into the milestone \"Launch\"");

        milestones.assign(card.id(), launch.id(), ACTOR);
        assertThat(activityLog.recentForCard(card.id())).as("assigning again is a no-op").hasSize(2);

        milestones.assign(card.id(), null, ACTOR);
        assertThat(cardById(card.id()).milestoneId()).isNull();

        milestones.assign(card.id(), launch.id(), ACTOR);
        milestones.delete(launch.id(), ACTOR);
        assertThat(milestones.forBoard(board.id())).isEmpty();
        assertThat(cardById(card.id()).milestoneId()).as("the card survives its milestone").isNull();
        assertThatThrownBy(() -> milestones.assign(card.id(), launch.id(), ACTOR)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void refusesMilestonesOfOtherBoards() {
        BoardSnapshot other = boardService.createBoard(new BoardDraft("Other", null), ACTOR);
        MilestoneSnapshot foreign = milestones.create(other.id(), new MilestoneDraft("Theirs", null), ACTOR);
        CardSnapshot card = boardService.createCard(backlog, CardDraft.titled("Mine"), ACTOR);

        assertThatThrownBy(() -> milestones.assign(card.id(), foreign.id(), ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void progressCountsCardsAndForecastsTheTarget() {
        MilestoneSnapshot launch = milestones.create(board.id(), new MilestoneDraft("Launch", LocalDate.now().plusDays(30)), ACTOR);
        MilestoneSnapshot empty = milestones.create(board.id(), new MilestoneDraft("Empty", null), ACTOR);
        for (String title : List.of("a", "b", "c", "d")) {
            CardSnapshot finished = boardService.createCard(backlog, CardDraft.titled(title), ACTOR);
            milestones.assign(finished.id(), launch.id(), ACTOR);
            boardService.moveCard(finished.id(), done, 0, ACTOR);
        }
        CardSnapshot open = boardService.createCard(backlog, CardDraft.titled("still open"), ACTOR);
        milestones.assign(open.id(), launch.id(), ACTOR);
        boardService.createCard(backlog, CardDraft.titled("unrelated"), ACTOR);

        List<MilestoneProgress> progress = milestones.progress(board.id());

        assertThat(progress).extracting(entry -> entry.milestone().name()).containsExactly("Launch", "Empty");
        MilestoneProgress launchProgress = progress.getFirst();
        assertThat(launchProgress.done()).isEqualTo(4);
        assertThat(launchProgress.open()).isEqualTo(1);
        assertThat(launchProgress.ratio()).isEqualTo(0.8);
        assertThat(launchProgress.isComplete()).isFalse();
        assertThat(launchProgress.isLate(LocalDate.now())).isFalse();
        assertThat(launchProgress.chance()).as("four completions on one of sixty days").hasValueSatisfying(chance -> assertThat(chance).isBetween(0.1, 0.9));
        assertThat(launchProgress.forecast().chanceBy(LocalDate.now().plusDays(400))).isGreaterThan(0.99);
        assertThat(launchProgress.forecast().target()).isEqualTo(launch.targetDate());

        MilestoneProgress emptyProgress = progress.get(1);
        assertThat(emptyProgress.total()).isZero();
        assertThat(emptyProgress.chance()).isEmpty();
        assertThat(emptyProgress.forecast().reason()).contains(dev.gatzka.flowboard.domain.Forecast.Reason.NOTHING_OPEN);

        boardService.moveCard(open.id(), done, 0, ACTOR);
        assertThat(milestones.progress(board.id()).getFirst().isComplete()).isTrue();
    }

    @Test
    void deletingTheBoardRemovesItsMilestones() {
        milestones.create(board.id(), new MilestoneDraft("Gone with the board", null), ACTOR);

        boardService.deleteBoard(board.id(), ACTOR);

        assertThat(milestones.forBoard(board.id())).isEmpty();
    }

    private CardSnapshot cardById(long cardId) {
        return boardService.findBoard(board.id()).orElseThrow().lanes().stream().flatMap(lane -> lane.cards().stream())
                .filter(card -> card.id() == cardId).findFirst().orElseThrow();
    }
}
