package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.DependencyGraph.Node;
import dev.gatzka.flowboard.domain.DependencyGraph.Status;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyGraphTest {

    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    @Test
    void laysOutBlockersToTheLeftAndFindsTheLongestOpenChain() {
        // design(done) -> build -> test -> ship ; spec(archived) -> build ; docs -> ship
        CardSnapshot design = card(1, 10, "Design", List.of());
        CardSnapshot build = card(2, 20, "Build", List.of(blocker(1, "Design", true), blocker(9, "Spec", true)));
        CardSnapshot test = card(3, 20, "Test", List.of(blocker(2, "Build", false)));
        CardSnapshot docs = card(4, 20, "Docs", List.of());
        CardSnapshot ship = card(5, 20, "Ship", List.of(blocker(3, "Test", false), blocker(4, "Docs", false)));
        CardSnapshot loner = card(6, 20, "Loner", List.of());
        BoardSnapshot board = new BoardSnapshot(1, "Release", "", List.of(
                new LaneSnapshot(10, "Done", 0, null, true, List.of(design)),
                new LaneSnapshot(20, "Doing", 1, null, false, List.of(build, test, docs, ship, loner))));

        DependencyGraph graph = DependencyGraph.of(board);

        assertThat(graph.nodes()).extracting(Node::id).containsExactlyInAnyOrder(1L, 9L, 2L, 3L, 4L, 5L);
        assertThat(graph.node(9).orElseThrow()).satisfies(spec -> {
            assertThat(spec.lane()).isEqualTo("Archived");
            assertThat(spec.status()).isEqualTo(Status.DONE);
            assertThat(spec.layer()).isZero();
        });
        assertThat(graph.node(1).orElseThrow().status()).isEqualTo(Status.DONE);
        assertThat(graph.node(2).orElseThrow()).returns(Status.OPEN, Node::status).returns(1, Node::layer);
        assertThat(graph.node(3).orElseThrow()).returns(Status.BLOCKED, Node::status).returns(2, Node::layer);
        assertThat(graph.node(4).orElseThrow()).returns(Status.OPEN, Node::status).returns(0, Node::layer);
        assertThat(graph.node(5).orElseThrow()).returns(Status.BLOCKED, Node::status).returns(3, Node::layer);
        assertThat(graph.columns()).isEqualTo(4);
        assertThat(graph.rows()).isEqualTo(3);
        assertThat(graph.nodes().stream().filter(node -> node.layer() == 0).map(Node::row)).containsExactlyInAnyOrder(0, 1, 2);

        assertThat(graph.edges()).hasSize(5);
        assertThat(graph.edges()).filteredOn(edge -> edge.cardId() == 5 && edge.blockerId() == 4)
                .singleElement().satisfies(docsToShip -> assertThat(docsToShip.waypoints()).hasSize(2));
        // the long Docs -> Ship link reserves a row in the columns it crosses, so it is not drawn through Build or Test
        assertThat(graph.nodes().stream().filter(node -> node.layer() == 1).map(Node::row).toList())
                .doesNotContain(graph.edges().stream().filter(edge -> edge.blockerId() == 4).findFirst().orElseThrow().waypoints().get(0));
        assertThat(graph.openLinkCount()).isEqualTo(3);
        assertThat(graph.blockedCount()).isEqualTo(2);
        assertThat(graph.criticalTitles()).containsExactly("Build", "Test", "Ship");
        assertThat(graph.keyBlocker()).map(Node::title).hasValue("Build");
        assertThat(graph.holdsUp(2)).isEqualTo(2);
        assertThat(graph.holdsUp(4)).isEqualTo(1);
        assertThat(graph.holdsUp(5)).isZero();
    }

    @Test
    void boardsWithoutLinksProduceAnEmptyGraph() {
        BoardSnapshot board = new BoardSnapshot(1, "Empty", "", List.of(
                new LaneSnapshot(10, "Todo", 0, null, false, List.of(card(1, 10, "Alone", List.of())))));

        DependencyGraph graph = DependencyGraph.of(board);

        assertThat(graph.isEmpty()).isTrue();
        assertThat(graph.columns()).isZero();
        assertThat(graph.criticalPath()).isEmpty();
        assertThat(graph.keyBlocker()).isEmpty();
    }

    @Test
    void chainsThatAreAllDoneHaveNoCriticalPath() {
        CardSnapshot a = card(1, 10, "A", List.of());
        CardSnapshot b = card(2, 10, "B", List.of(blocker(1, "A", true)));
        BoardSnapshot board = new BoardSnapshot(1, "Finished", "", List.of(
                new LaneSnapshot(10, "Done", 0, null, true, List.of(a, b))));

        DependencyGraph graph = DependencyGraph.of(board);

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.criticalPath()).isEmpty();
        assertThat(graph.keyBlocker()).isEmpty();
        assertThat(graph.openLinkCount()).isZero();
    }

    private static BlockerSnapshot blocker(long id, String title, boolean resolved) {
        return new BlockerSnapshot(id, title, resolved);
    }

    private static CardSnapshot card(long id, long laneId, String title, List<BlockerSnapshot> blockers) {
        return new CardSnapshot(id, laneId, title, "", Priority.MEDIUM, null, null, null, Set.of(), null, null, 0, Set.of(),
                blockers, List.of(), List.of(), List.of(), 0, NOW, null, NOW);
    }
}
