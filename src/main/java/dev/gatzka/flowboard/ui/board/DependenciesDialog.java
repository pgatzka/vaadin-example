package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.Svg;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.DependencyGraph;
import dev.gatzka.flowboard.domain.DependencyGraph.Node;
import java.util.function.LongConsumer;

/** The board's "blocked by" links as a graph, with the longest open chain and the card holding up the most work. */
final class DependenciesDialog extends Dialog {

    private static final String CLICKED_CARD =
            "event.target.closest('[data-card-id]') ? event.target.closest('[data-card-id]').getAttribute('data-card-id') : ''";

    private final Div content = new Div();
    private final LongConsumer onOpenCard;

    DependenciesDialog(LongConsumer onOpenCard) {
        this.onOpenCard = onOpenCard;
        addClassName("dependencies");
        setHeaderTitle(I18n.t("board.menu.dependencies"));
        setWidth("min(900px, 95vw)");
        setModal(false);
        setDraggable(true);

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel(I18n.t("depgraph.close"));
        getHeader().add(close);

        content.addClassName("dependencies__content");
        add(content);
    }

    void show(DependencyGraph graph) {
        content.removeAll();
        if (graph.isEmpty()) {
            Span empty = new Span(I18n.t("depgraph.empty"));
            empty.addClassName("dependencies__empty");
            content.add(empty);
            return;
        }
        content.add(summary(graph));
        graph.keyBlocker().ifPresent(blocker -> content.add(keyBlocker(graph, blocker)));
        if (!graph.criticalPath().isEmpty()) {
            content.add(criticalPath(graph));
        }
        Svg chart = new Svg(DependencyChart.svg(graph));
        chart.getElement().addEventListener("click", event -> cardClicked(event.getEventData().path(CLICKED_CARD).asString("")))
                .addEventData(CLICKED_CARD);
        Div scroller = new Div(chart);
        scroller.addClassName("dependencies__chart");
        content.add(scroller, legend());
    }

    /** Opens the card a graph node stands for; ignores clicks between nodes. */
    void cardClicked(String cardId) {
        if (cardId != null && !cardId.isBlank()) {
            onOpenCard.accept(Long.parseLong(cardId));
        }
    }

    private static Div summary(DependencyGraph graph) {
        Span text = new Span(I18n.t("depgraph.summary", graph.edges().size(), graph.nodes().size(), graph.openLinkCount(), graph.blockedCount()));
        Div summary = new Div(text);
        summary.addClassName("dependencies__summary");
        return summary;
    }

    private Div keyBlocker(DependencyGraph graph, Node blocker) {
        int count = graph.holdsUp(blocker.id());
        Span label = new Span(I18n.t("depgraph.clearing"));
        Button card = new Button(blocker.title(), event -> onOpenCard.accept(blocker.id()));
        card.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        card.addClassName("dependencies__key-blocker");
        Span tail = new Span(I18n.t("depgraph.unblocks", count));
        Div row = new Div(VaadinIcon.LIGHTBULB.create(), label, card, tail);
        row.addClassName("dependencies__hint");
        return row;
    }

    private Div criticalPath(DependencyGraph graph) {
        Div chain = new Div();
        chain.addClassName("dependencies__chain");
        Span label = new Span(I18n.t("depgraph.longestChain"));
        label.addClassName("dependencies__chain-label");
        chain.add(label);
        for (int i = 0; i < graph.criticalPath().size(); i++) {
            long id = graph.criticalPath().get(i);
            if (i > 0) {
                Span arrow = new Span("→");
                arrow.addClassName("dependencies__chain-arrow");
                chain.add(arrow);
            }
            Button step = new Button(graph.node(id).map(Node::title).orElse("?"), event -> onOpenCard.accept(id));
            step.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
            step.addClassName("dependencies__chain-step");
            chain.add(step);
        }
        return chain;
    }

    private static Div legend() {
        Div legend = new Div(swatch("done", I18n.t("depgraph.done")), swatch("open", I18n.t("dependencies.open")), swatch("blocked", I18n.t("card.blocked")), swatch("critical", I18n.t("depgraph.chain")));
        legend.addClassName("dependencies__legend");
        return legend;
    }

    private static Span swatch(String kind, String label) {
        Span dot = new Span();
        dot.addClassName("dependencies__swatch");
        dot.addClassName("dependencies__swatch--" + kind);
        Span item = new Span(dot, new Span(label));
        item.addClassName("dependencies__legend-item");
        return item;
    }

}
