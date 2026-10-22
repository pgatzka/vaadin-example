package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import dev.gatzka.flowboard.domain.CumulativeFlow;
import dev.gatzka.flowboard.domain.CumulativeFlow.Series;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders a cumulative flow diagram as an inline SVG: stacked areas, last lane at the bottom. */
final class CumulativeFlowChart {

    private static final List<String> PALETTE = List.of(
            "hsl(252, 72%, 62%)", "hsl(205, 80%, 55%)", "hsl(32, 95%, 55%)", "hsl(340, 70%, 58%)",
            "hsl(174, 60%, 42%)", "hsl(20, 75%, 55%)", "hsl(280, 55%, 60%)", "hsl(60, 60%, 45%)");
    private static final String DONE_COLOR = "hsl(130, 50%, 45%)";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");

    private CumulativeFlowChart() {
    }

    static String svg(CumulativeFlow flow, int width, int height) {
        int left = 28;
        int bottom = 18;
        int plotWidth = width - left - 8;
        int plotHeight = height - bottom - 6;
        int days = flow.days().size();
        int max = flow.maxTotal();
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" width=\"100%\" role=\"img\" aria-label=\"").append(I18n.t("insights.flow")).append("\" class=\"cfd\">");

        // Stack from the last lane (bottom) upwards so finished work forms the base.
        int[] cumulative = new int[days];
        List<Series> series = flow.series();
        for (int s = series.size() - 1; s >= 0; s--) {
            Series lane = series.get(s);
            StringBuilder top = new StringBuilder();
            StringBuilder base = new StringBuilder();
            for (int i = 0; i < days; i++) {
                double x = left + (days == 1 ? plotWidth / 2.0 : plotWidth * i / (double) (days - 1));
                double yBase = 6 + plotHeight - plotHeight * cumulative[i] / (double) max;
                cumulative[i] += lane.counts().get(i);
                double yTop = 6 + plotHeight - plotHeight * cumulative[i] / (double) max;
                top.append(i == 0 ? "M" : "L").append(fmt(x)).append(' ').append(fmt(yTop)).append(' ');
                base.insert(0, "L" + fmt(x) + " " + fmt(yBase) + " ");
            }
            String color = lane.done() ? DONE_COLOR : PALETTE.get(s % PALETTE.size());
            svg.append("<path d=\"").append(top).append(base).append("Z\" fill=\"").append(color)
                    .append("\" fill-opacity=\"0.85\"><title>").append(escape(lane.laneName())).append("</title></path>");
        }
        svg.append("<line x1=\"").append(left).append("\" y1=\"6\" x2=\"").append(left).append("\" y2=\"").append(6 + plotHeight)
                .append("\" stroke=\"currentColor\" stroke-opacity=\"0.3\"/>");
        svg.append("<text x=\"").append(left - 4).append("\" y=\"12\" text-anchor=\"end\" font-size=\"9\" fill=\"currentColor\">")
                .append(max).append("</text>");
        svg.append("<text x=\"").append(left).append("\" y=\"").append(height - 4).append("\" font-size=\"9\" fill=\"currentColor\">")
                .append(DAY.format(flow.days().getFirst())).append("</text>");
        svg.append("<text x=\"").append(width - 8).append("\" y=\"").append(height - 4)
                .append("\" text-anchor=\"end\" font-size=\"9\" fill=\"currentColor\">")
                .append(DAY.format(flow.days().getLast())).append("</text>");
        svg.append("</svg>");
        return svg.toString();
    }

    static String colorFor(Series lane, int index) {
        return lane.done() ? DONE_COLOR : PALETTE.get(index % PALETTE.size());
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
