package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import dev.gatzka.flowboard.domain.DependencyGraph;
import dev.gatzka.flowboard.domain.DependencyGraph.Edge;
import dev.gatzka.flowboard.domain.DependencyGraph.Node;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Draws a {@link DependencyGraph} as SVG: one column per layer, arrows from blockers to the cards they hold up. */
final class DependencyChart {

    static final int NODE_WIDTH = 180;
    static final int NODE_HEIGHT = 46;
    static final int COLUMN_GAP = 70;
    static final int ROW_GAP = 16;
    static final int PADDING = 12;
    private static final int MAX_TITLE = 24;

    private DependencyChart() {
    }

    static String svg(DependencyGraph graph) {
        if (graph.isEmpty()) {
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"0\" height=\"0\"></svg>";
        }
        int width = PADDING * 2 + graph.columns() * NODE_WIDTH + (graph.columns() - 1) * COLUMN_GAP;
        int height = PADDING * 2 + graph.rows() * NODE_HEIGHT + (graph.rows() - 1) * ROW_GAP;
        Set<Long> critical = new HashSet<>(graph.criticalPath());
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" width=\"").append(width).append("\" height=\"").append(height)
                .append("\" role=\"img\" aria-label=\"").append(I18n.t("depgraph.aria")).append("\" class=\"depgraph\">");
        svg.append("<defs><marker id=\"depgraph-arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" markerWidth=\"7\" markerHeight=\"7\" orient=\"auto-start-reverse\">")
                .append("<path d=\"M 0 0 L 10 5 L 0 10 z\" class=\"depgraph__arrow\"/></marker>")
                .append("<marker id=\"depgraph-arrow-critical\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" markerWidth=\"6\" markerHeight=\"6\" orient=\"auto-start-reverse\">")
                .append("<path d=\"M 0 0 L 10 5 L 0 10 z\" class=\"depgraph__arrow depgraph__arrow--critical\"/></marker></defs>");
        for (Edge edge : graph.edges()) {
            Node from = graph.node(edge.blockerId()).orElseThrow();
            Node to = graph.node(edge.cardId()).orElseThrow();
            boolean onPath = critical.contains(edge.blockerId()) && critical.contains(edge.cardId())
                    && follows(graph, edge.blockerId(), edge.cardId());
            svg.append("<path class=\"depgraph__edge").append(edge.resolved() ? " depgraph__edge--resolved" : "")
                    .append(onPath ? " depgraph__edge--critical" : "").append("\" d=\"").append(route(from, to, edge))
                    .append("\" marker-end=\"url(#").append(onPath ? "depgraph-arrow-critical" : "depgraph-arrow").append(")\"/>");
        }
        for (Node node : graph.nodes()) {
            svg.append("<g class=\"depgraph__node depgraph__node--").append(node.status().name().toLowerCase(Locale.ROOT))
                    .append(critical.contains(node.id()) ? " depgraph__node--critical" : "")
                    .append("\" data-card-id=\"").append(node.id()).append("\" role=\"button\" tabindex=\"0\">")
                    .append("<title>").append(escape(node.title())).append(" · ").append(escape(node.lane())).append("</title>")
                    .append("<rect x=\"").append(x(node)).append("\" y=\"").append(y(node)).append("\" width=\"").append(NODE_WIDTH)
                    .append("\" height=\"").append(NODE_HEIGHT).append("\" rx=\"8\"/>")
                    .append("<text class=\"depgraph__title\" x=\"").append(x(node) + 12).append("\" y=\"").append(y(node) + 20).append("\">")
                    .append(escape(truncate(node.title()))).append("</text>")
                    .append("<text class=\"depgraph__lane\" x=\"").append(x(node) + 12).append("\" y=\"").append(y(node) + 36).append("\">")
                    .append(escape(truncate(node.lane()))).append("</text></g>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    /** A smooth path from the blocker's right edge to the card's left edge, through the rows reserved in between. */
    static String route(Node from, Node to, Edge edge) {
        StringBuilder path = new StringBuilder();
        int x = x(from) + NODE_WIDTH;
        int y = y(from) + NODE_HEIGHT / 2;
        path.append("M ").append(x).append(' ').append(y);
        int layer = from.layer() + 1;
        for (int row : edge.waypoints()) {
            int nextX = PADDING + layer * (NODE_WIDTH + COLUMN_GAP);
            int nextY = PADDING + row * (NODE_HEIGHT + ROW_GAP) + NODE_HEIGHT / 2;
            curveTo(path, x, y, nextX, nextY);
            path.append(" L ").append(nextX + NODE_WIDTH).append(' ').append(nextY);
            x = nextX + NODE_WIDTH;
            y = nextY;
            layer++;
        }
        curveTo(path, x, y, x(to), y(to) + NODE_HEIGHT / 2);
        return path.toString();
    }

    private static void curveTo(StringBuilder path, int x1, int y1, int x2, int y2) {
        int bend = (x2 - x1) / 2;
        path.append(" C ").append(x1 + bend).append(' ').append(y1).append(", ").append(x2 - bend).append(' ').append(y2)
                .append(", ").append(x2).append(' ').append(y2);
    }

    private static boolean follows(DependencyGraph graph, long blockerId, long cardId) {
        int index = graph.criticalPath().indexOf(blockerId);
        return index >= 0 && index + 1 < graph.criticalPath().size() && graph.criticalPath().get(index + 1) == cardId;
    }

    private static int x(Node node) {
        return PADDING + node.layer() * (NODE_WIDTH + COLUMN_GAP);
    }

    private static int y(Node node) {
        return PADDING + node.row() * (NODE_HEIGHT + ROW_GAP);
    }

    static String truncate(String text) {
        return text.length() <= MAX_TITLE ? text : text.substring(0, MAX_TITLE - 1).stripTrailing() + "…";
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
