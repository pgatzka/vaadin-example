package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import dev.gatzka.flowboard.domain.ActivityPulse;
import dev.gatzka.flowboard.domain.ActivityPulse.DayCount;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** A calendar heatmap: one small square per day, columns are weeks, darker means busier. */
final class HeatmapChart {

    static final int CELL = 12;
    static final int GAP = 3;
    private static final int LABEL_WIDTH = 28;
    private static final int LABEL_HEIGHT = 16;
    private static DateTimeFormatter day() {
        return I18n.formatter("EEE, MMM d");
    }
    private static final double[] LEVELS = {0.25, 0.5, 0.75, 1.0};

    private HeatmapChart() {
    }

    static String svg(ActivityPulse pulse) {
        if (pulse.days().isEmpty()) {
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"0\" height=\"0\"></svg>";
        }
        int firstColumnOffset = pulse.days().getFirst().day().getDayOfWeek().getValue() - 1; // Monday = 0
        int columns = (firstColumnOffset + pulse.days().size() + 6) / 7;
        int width = LABEL_WIDTH + columns * (CELL + GAP);
        int height = LABEL_HEIGHT + 7 * (CELL + GAP);
        int max = Math.max(1, pulse.busiestDayCount());
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" width=\"").append(width).append("\" role=\"img\" aria-label=\"").append(I18n.t("heatmap.aria")).append("\" class=\"heatmap\">");
        for (DayOfWeek weekday : new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY}) {
            int row = weekday.getValue() - 1;
            svg.append("<text class=\"heatmap__label\" x=\"0\" y=\"").append(LABEL_HEIGHT + row * (CELL + GAP) + CELL - 2)
                    .append("\">").append(weekday.getDisplayName(TextStyle.SHORT, I18n.locale())).append("</text>");
        }
        int lastMonth = -1;
        for (int i = 0; i < pulse.days().size(); i++) {
            DayCount day = pulse.days().get(i);
            int slot = firstColumnOffset + i;
            int column = slot / 7;
            int row = slot % 7;
            int x = LABEL_WIDTH + column * (CELL + GAP);
            int y = LABEL_HEIGHT + row * (CELL + GAP);
            if (row == 0 && day.day().getMonthValue() != lastMonth) {
                lastMonth = day.day().getMonthValue();
                svg.append("<text class=\"heatmap__label\" x=\"").append(x).append("\" y=\"").append(LABEL_HEIGHT - 5).append("\">")
                        .append(day.day().getMonth().getDisplayName(TextStyle.SHORT, I18n.locale())).append("</text>");
            }
            svg.append("<rect class=\"heatmap__day\" data-level=\"").append(level(day.count(), max)).append("\" x=\"").append(x)
                    .append("\" y=\"").append(y).append("\" width=\"").append(CELL).append("\" height=\"").append(CELL)
                    .append("\" rx=\"2\"><title>").append(day().format(day.day())).append(": ")
                    .append(day.count()).append(I18n.t(day.count() == 1 ? "heatmap.change" : "heatmap.changes")).append("</title></rect>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    /** 0 for a quiet day, 1 to 4 for increasingly busy ones relative to the busiest day. */
    static int level(int count, int max) {
        if (count <= 0) {
            return 0;
        }
        double share = (double) count / max;
        for (int i = 0; i < LEVELS.length; i++) {
            if (share <= LEVELS[i]) {
                return i + 1;
            }
        }
        return LEVELS.length;
    }
}
