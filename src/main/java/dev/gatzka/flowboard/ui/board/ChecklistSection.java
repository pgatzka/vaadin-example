package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.ChecklistItemSnapshot;
import java.util.function.Consumer;

/** Checklist of an existing card inside the editor. Changes are saved as they happen. */
final class ChecklistSection extends Div {

    private final Span summary = new Span();
    private final ProgressBar progress = new ProgressBar();
    private final Div items = new Div();
    private final TextField newItem = new TextField();
    private final CardEditorActions actions;
    private final Consumer<CardSnapshot> onChanged;
    private CardSnapshot card;

    ChecklistSection(CardSnapshot card, CardEditorActions actions, Consumer<CardSnapshot> onChanged) {
        this.actions = actions;
        this.onChanged = onChanged;
        addClassName("checklist");

        Span label = new Span("Checklist");
        label.addClassName("field-label");
        summary.addClassName("checklist__summary");
        Div header = new Div(label, summary);
        header.addClassName("checklist__header");

        progress.addClassName("checklist__progress");
        items.addClassName("checklist__items");

        newItem.setPlaceholder("Add an item and press Enter");
        newItem.setMaxLength(200);
        newItem.setWidthFull();
        newItem.setValueChangeMode(ValueChangeMode.EAGER);
        newItem.addKeyPressListener(Key.ENTER, event -> addItem());
        Button add = new Button("Add", event -> addItem());
        add.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Div composer = new Div(newItem, add);
        composer.addClassName("checklist__composer");

        add(header, progress, items, composer);
        show(card);
    }

    void show(CardSnapshot card) {
        this.card = card;
        long done = card.checklistDone();
        int total = card.checklist().size();
        summary.setText(total == 0 ? "" : done + " of " + total + " done");
        progress.setVisible(total > 0);
        progress.setValue(total == 0 ? 0 : (double) done / total);
        items.removeAll();
        for (ChecklistItemSnapshot item : card.checklist()) {
            items.add(row(item));
        }
    }

    private Div row(ChecklistItemSnapshot item) {
        Checkbox checkbox = new Checkbox(item.text(), item.done());
        checkbox.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                update(actions.setChecklistItemDone(item.id(), event.getValue()));
            }
        });
        Button remove = new Button(VaadinIcon.TRASH.create(), event -> update(actions.removeChecklistItem(item.id())));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        remove.setAriaLabel("Remove \"" + item.text() + "\"");
        Div row = new Div(checkbox, remove);
        row.addClassName("checklist__item");
        if (item.done()) {
            row.addClassName("checklist__item--done");
        }
        return row;
    }

    private void addItem() {
        String text = newItem.getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        newItem.clear();
        update(actions.addChecklistItem(card.id(), text));
        newItem.focus();
    }

    private void update(CardSnapshot refreshed) {
        show(refreshed);
        onChanged.accept(refreshed);
    }
}
