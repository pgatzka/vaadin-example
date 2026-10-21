package dev.gatzka.flowboard.bootstrap;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.service.BoardService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Seeds a demo board on an empty database so the application is useful from the first start. */
@Component
@ConditionalOnProperty(name = "flowboard.sample-data", havingValue = "true", matchIfMissing = true)
class SampleDataInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SampleDataInitializer.class);
    private static final String ACTOR = "Flowboard";

    private final BoardRepository boards;
    private final BoardService boardService;
    private final Clock clock;

    SampleDataInitializer(BoardRepository boards, BoardService boardService, Clock clock) {
        this.boards = boards;
        this.boardService = boardService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (boards.count() > 0) {
            return;
        }
        LOG.info("Empty database, seeding sample boards");
        seedLaunchBoard(LocalDate.now(clock));
        seedRenovationBoard(LocalDate.now(clock));
    }

    private void seedLaunchBoard(LocalDate today) {
        BoardSnapshot board = boardService.createBoard(
                new BoardDraft("Flowboard launch", "Everything we need to ship the first public version."), ACTOR);
        long backlog = lane(board, 0);
        long inProgress = lane(board, 1);
        long review = lane(board, 2);
        long done = lane(board, 3);

        card(backlog, "Write the onboarding guide", """
                A short guide for first-time users.

                - How to create a board
                - Lanes, WIP limits and what *done* means
                - Keyboard shortcuts
                """, Priority.MEDIUM, today.plusDays(10), "Mara", "docs");
        card(backlog, "Export a board as JSON", null, Priority.LOW, null, null, "feature");
        card(backlog, "Polish dark mode contrast", "Check badges and lane headers against WCAG AA.",
                Priority.LOW, null, "Priya", "design");

        card(inProgress, "Real-time sync via server push", """
                Every mutation goes through `BoardService`, which records an activity entry and
                publishes a `BoardChangedEvent` **after commit**. Open browsers subscribe through
                `BoardChangeBroadcaster` and reload the snapshot.
                """, Priority.HIGH, today.plusDays(3), "Jonas", "backend", "feature");
        card(inProgress, "Drag & drop between lanes", "Dropping on a card inserts before it, dropping on empty space appends.",
                Priority.HIGH, today.plusDays(1), "Mara", "ui", "feature");

        card(review, "Card editor with Markdown preview", "Uses the Vaadin `Markdown` component in a preview tab.",
                Priority.MEDIUM, today.minusDays(1), "Jonas", "ui");

        card(done, "Project skeleton on Spring Boot 4 and Vaadin 25", null, Priority.HIGH, today.minusDays(6), "Priya", "infra");
        card(done, "H2 persistence with Spring Data JPA", null, Priority.MEDIUM, null, "Jonas", "backend");
    }

    private void seedRenovationBoard(LocalDate today) {
        BoardSnapshot board = boardService.createBoard(
                new BoardDraft("Kitchen renovation", "Tracking contractors, orders and decisions."), ACTOR);
        card(lane(board, 0), "Pick a worktop material", "Quartz vs. oak. Get two quotes each.",
                Priority.MEDIUM, today.plusDays(7), null, "decision");
        card(lane(board, 1), "Order the tiles", null, Priority.URGENT, today, "Mara", "order");
        card(lane(board, 3), "Measure the room", null, Priority.HIGH, null, "Mara");
    }

    private static long lane(BoardSnapshot board, int index) {
        LaneSnapshot lane = board.lanes().get(index);
        return lane.id();
    }

    private void card(long laneId, String title, String description, Priority priority, LocalDate due, String assignee,
            String... tags) {
        boardService.createCard(laneId, new CardDraft(title, description, priority, due, assignee, Set.of(tags)), ACTOR);
    }
}
