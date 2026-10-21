package dev.gatzka.flowboard.ui.board;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.CumulativeFlow;
import dev.gatzka.flowboard.domain.CumulativeFlow.Series;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CumulativeFlowChartTest {

    @Test
    void drawsOneAreaPerLaneAndLabelsTheAxes() {
        CumulativeFlow flow = new CumulativeFlow(
                List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)),
                List.of(new Series("Backlog", false, List.of(3, 2, 2)),
                        new Series("Doing <wip>", false, List.of(1, 2, 1)),
                        new Series("Done", true, List.of(0, 1, 3))),
                3);

        String svg = CumulativeFlowChart.svg(flow, 400, 120);

        assertThat(svg).startsWith("<svg").endsWith("</svg>");
        assertThat(svg.split("<path ").length - 1).isEqualTo(3);
        assertThat(svg).contains("<title>Doing &lt;wip&gt;</title>").contains("Oct 1").contains("Oct 3").contains(">6</text>");
        assertThat(flow.maxTotal()).isEqualTo(6);
    }
}
