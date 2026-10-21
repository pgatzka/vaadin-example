package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.CardFilter;
import dev.gatzka.flowboard.domain.SavedViewSnapshot;
import java.util.List;
import java.util.function.Consumer;

/**
 * The row of saved views under the toolbar: one chip per view, the active one highlighted and
 * removable, plus a button to save the current filter as a new view.
 */
final class ViewsBar extends Div {

    interface Actions {
        void applyView(SavedViewSnapshot view);

        void saveView(String name);

        void deleteView(SavedViewSnapshot view);
    }

    private final Actions actions;
    private final Div chips = new Div();
    private final Button save = new Button("Save view", VaadinIcon.STAR_O.create());

    ViewsBar(Actions actions) {
        this.actions = actions;
        addClassName("views-bar");
        chips.addClassName("views-bar__chips");
        save.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        save.addClassName("views-bar__save");
        save.setTooltipText("Save the current filter as a view everybody on the board can use");
        save.addClickListener(event -> askForName());
        add(chips, save);
    }

    /** Re-renders the chips; {@code current} decides which one is shown as active. */
    void show(List<SavedViewSnapshot> views, CardFilter current) {
        chips.removeAll();
        setVisible(!views.isEmpty() || current.isActive());
        save.setVisible(current.isActive() && views.stream().noneMatch(view -> view.filter().equals(current)));
        for (SavedViewSnapshot view : views) {
            boolean active = view.filter().equals(current);
            Button chip = new Button(view.name(), event -> actions.applyView(view));
            chip.addThemeVariants(ButtonVariant.LUMO_SMALL, active ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
            chip.addClassName("views-bar__chip");
            if (active) {
                chip.addClassName("views-bar__chip--active");
                chip.setTooltipText("This view is applied");
            }
            Div holder = new Div(chip);
            holder.addClassName("views-bar__item");
            if (active) {
                Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> actions.deleteView(view));
                remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
                remove.setAriaLabel("Delete view " + view.name());
                remove.setTooltipText("Delete this view");
                remove.addClassName("views-bar__remove");
                holder.add(remove);
            }
            chips.add(holder);
        }
    }

    private void askForName() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Save view");
        Span hint = new Span("Everyone on this board will see it as a chip above the lanes.");
        hint.addClassName("share-dialog__hint");
        TextField name = new TextField("Name");
        name.setPlaceholder("e.g. Backend bugs");
        name.setMaxLength(60);
        name.setAutofocus(true);
        name.setWidthFull();
        name.setId("view-name");
        dialog.add(hint, name);
        Button confirm = new Button("Save", event -> {
            if (name.getValue().isBlank()) {
                name.setErrorMessage("Please enter a name");
                name.setInvalid(true);
                return;
            }
            actions.saveView(name.getValue());
            dialog.close();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.addClickShortcut(Key.ENTER);
        dialog.getFooter().add(new Button("Cancel", event -> dialog.close()), confirm);
        dialog.open();
    }
}
