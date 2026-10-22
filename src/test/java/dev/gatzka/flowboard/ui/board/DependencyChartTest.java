package dev.gatzka.flowboard.ui.board;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.DependencyGraph;
import dev.gatzka.flowboard.domain.DependencyGraph.Edge;
import dev.gatzka.flowboard.domain.DependencyGraph.Node;
import dev.gatzka.flowboard.domain.DependencyGraph.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyChartTest {

    @Test
    void drawsNodesPerLayerAndArrowsBetweenThem() {
        DependencyGraph graph = new DependencyGraph(List.of(
                new Node(1, "Design <the> \"thing\"", "Done", Status.DONE, 0, 0),
                new Node(2, "Build", "Doing", Status.OPEN, 1, 0),
                new Node(3, "A very long card title that needs trimming", "Doing", Status.BLOCKED, 2, 0)),
                List.of(new Edge(1, 2, true), new Edge(2, 3, false)),
                List.of(2L, 3L));

        String svg = DependencyChart.svg(graph);

        assertThat(svg).startsWith("<svg").endsWith("</svg>");
        assertThat(svg.split("<g class=\"depgraph__node")).hasSize(4);
        assertThat(svg).contains("depgraph__node--done").contains("depgraph__node--open depgraph__node--critical")
                .contains("depgraph__node--blocked depgraph__node--critical")
                .contains("data-card-id=\"3\"")
                .contains("Design &lt;the&gt; &quot;thing&quot;")
                .contains(">A very long card title…</text>")
                .contains("depgraph__edge depgraph__edge--resolved\"")
                .contains("depgraph__edge depgraph__edge--critical\"")
                .contains("marker-end=\"url(#depgraph-arrow)\"");
        int width = 2 * DependencyChart.PADDING + 3 * DependencyChart.NODE_WIDTH + 2 * DependencyChart.COLUMN_GAP;
        assertThat(svg).contains("viewBox=\"0 0 " + width + " " + (2 * DependencyChart.PADDING + DependencyChart.NODE_HEIGHT) + "\"");
    }

    @Test
    void longLinksAreRoutedThroughTheirReservedRows() {
        Node from = new Node(1, "A", "Todo", Status.OPEN, 0, 0);
        Node to = new Node(3, "C", "Todo", Status.OPEN, 2, 0);

        String path = DependencyChart.route(from, to, new Edge(1, 3, false, List.of(1)));

        int slotX = DependencyChart.PADDING + DependencyChart.NODE_WIDTH + DependencyChart.COLUMN_GAP;
        int slotY = DependencyChart.PADDING + DependencyChart.NODE_HEIGHT + DependencyChart.ROW_GAP + DependencyChart.NODE_HEIGHT / 2;
        assertThat(path).startsWith("M " + (DependencyChart.PADDING + DependencyChart.NODE_WIDTH) + " " + (DependencyChart.PADDING + DependencyChart.NODE_HEIGHT / 2))
                .contains(", " + slotX + " " + slotY + " L " + (slotX + DependencyChart.NODE_WIDTH) + " " + slotY)
                .endsWith(", " + (DependencyChart.PADDING + 2 * (DependencyChart.NODE_WIDTH + DependencyChart.COLUMN_GAP)) + " "
                        + (DependencyChart.PADDING + DependencyChart.NODE_HEIGHT / 2));
    }

    @Test
    void emptyGraphDrawsNothing() {
        assertThat(DependencyChart.svg(new DependencyGraph(List.of(), List.of(), List.of()))).contains("width=\"0\"");
        assertThat(DependencyChart.truncate("short")).isEqualTo("short");
    }
}
