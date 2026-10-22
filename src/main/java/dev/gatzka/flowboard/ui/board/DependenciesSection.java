package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.BlockerSnapshot;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardSnapshot;
import java.util.List;
import java.util.function.Consumer;

/** "Blocked by" links of a card, edited in place, plus the cards this one blocks. */
final class DependenciesSection extends Div {

    private final Div blockers = new Div();
    private final Div blocks = new Div();
    private final ComboBox<CardSnapshot> picker = new ComboBox<>();
    private final CardEditorActions actions;
    private final BoardSnapshot board;
    private final Consumer<CardSnapshot> onChanged;
    private CardSnapshot card;

    DependenciesSection(CardSnapshot card, BoardSnapshot board, CardEditorActions actions, Consumer<CardSnapshot> onChanged) {
        this.card = card;
        this.board = board;
        this.actions = actions;
        this.onChanged = onChanged;
        addClassName("dependencies");

        Span label = new Span(I18n.t("dependencies.blockedBy"));
        label.addClassName("field-label");
        blockers.addClassName("dependencies__list");

        picker.setPlaceholder(I18n.t("dependencies.pickerPlaceholder"));
        picker.setItemLabelGenerator(CardSnapshot::title);
        picker.setClearButtonVisible(true);
        picker.setWidthFull();
        picker.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                update(actions.addBlocker(card.id(), event.getValue().id()));
                picker.clear();
            }
        });

        Span blocksLabel = new Span(I18n.t("dependencies.blocks"));
        blocksLabel.addClassName("field-label");
        blocks.addClassName("dependencies__list");

        add(label, blockers, picker, blocksLabel, blocks);
        show(card);
    }

    void show(CardSnapshot card) {
        this.card = card;
        blockers.removeAll();
        if (card.blockers().isEmpty()) {
            blockers.add(hint(I18n.t("dependencies.noBlockers")));
        }
        for (BlockerSnapshot blocker : card.blockers()) {
            blockers.add(blockerRow(blocker));
        }
        List<CardSnapshot> candidates = board.lanes().stream().flatMap(lane -> lane.cards().stream())
                .filter(other -> other.id() != card.id())
                .filter(other -> card.blockers().stream().noneMatch(blocker -> blocker.id() == other.id()))
                .toList();
        picker.setItems(candidates);

        blocks.removeAll();
        List<CardSnapshot> blocked = board.lanes().stream().flatMap(lane -> lane.cards().stream())
                .filter(other -> other.blockers().stream().anyMatch(blocker -> blocker.id() == card.id()))
                .toList();
        if (blocked.isEmpty()) {
            blocks.add(hint(I18n.t("dependencies.blocksNothing")));
        }
        blocked.forEach(other -> blocks.add(new Span(other.title())));
    }

    private Div blockerRow(BlockerSnapshot blocker) {
        Span title = new Span(blocker.title());
        Span state = new Span(I18n.t(blocker.resolved() ? "dependencies.done" : "dependencies.open"));
        state.addClassName("dependencies__state");
        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> update(actions.removeBlocker(card.id(), blocker.id())));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        remove.setAriaLabel(I18n.t("dependencies.removeAria", blocker.title()));
        Div row = new Div(title, state, remove);
        row.addClassName("dependencies__row");
        if (blocker.resolved()) {
            row.addClassName("dependencies__row--resolved");
        }
        return row;
    }

    private static Span hint(String text) {
        Span span = new Span(text);
        span.addClassName("dependencies__hint");
        return span;
    }

    private void update(CardSnapshot refreshed) {
        onChanged.accept(refreshed);
    }
}
