package dev.gatzka.flowboard.ui.board;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.ActivityPulse;
import dev.gatzka.flowboard.domain.ActivityPulse.DayCount;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeatmapChartTest {

    @Test
    void drawsOneSquarePerDayWithLevelsRelativeToTheBusiestDay() {
        LocalDate start = LocalDate.of(2026, 8, 3); // a Monday
        List<DayCount> days = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            days.add(new DayCount(start.plusDays(i), i == 3 ? 8 : i == 10 ? 2 : 0));
        }
        String svg = HeatmapChart.svg(new ActivityPulse(days, List.of()));

        assertThat(svg).startsWith("<svg").endsWith("</svg>");
        assertThat(svg.split("<rect ")).hasSize(15);
        assertThat(svg).contains("data-level=\"4\"").contains("data-level=\"1\"").contains("<title>Thu, Aug 6: 8 changes</title>")
                .contains("<title>Thu, Aug 13: 2 changes</title>").contains(">Aug</text>").contains(">Mon</text>");
        assertThat(svg.split("data-level=\"0\"")).hasSize(13);
    }

    @Test
    void levelsAndEmptyInput() {
        assertThat(HeatmapChart.level(0, 10)).isZero();
        assertThat(HeatmapChart.level(1, 10)).isEqualTo(1);
        assertThat(HeatmapChart.level(5, 10)).isEqualTo(2);
        assertThat(HeatmapChart.level(7, 10)).isEqualTo(3);
        assertThat(HeatmapChart.level(10, 10)).isEqualTo(4);
        assertThat(HeatmapChart.svg(new ActivityPulse(List.of(), List.of()))).contains("width=\"0\"");
    }
}
