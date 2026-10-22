package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;
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
    private final Button save = new Button(I18n.t("views.save"), VaadinIcon.STAR_O.create());

    ViewsBar(Actions actions) {
        this.actions = actions;
        addClassName("views-bar");
        chips.addClassName("views-bar__chips");
        save.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        save.addClassName("views-bar__save");
        save.setTooltipText(I18n.t("views.saveTooltip"));
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
                chip.setTooltipText(I18n.t("views.applied"));
            }
            Div holder = new Div(chip);
            holder.addClassName("views-bar__item");
            if (active) {
                Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> actions.deleteView(view));
                remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
                remove.setAriaLabel(I18n.t("views.deleteAria", view.name()));
                remove.setTooltipText(I18n.t("views.deleteTooltip"));
                remove.addClassName("views-bar__remove");
                holder.add(remove);
            }
            chips.add(holder);
        }
    }

    private void askForName() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(I18n.t("views.save"));
        Span hint = new Span(I18n.t("views.hint"));
        hint.addClassName("share-dialog__hint");
        TextField name = new TextField(I18n.t("common.name"));
        name.setPlaceholder(I18n.t("views.namePlaceholder"));
        name.setMaxLength(60);
        name.setAutofocus(true);
        name.setWidthFull();
        name.setId("view-name");
        dialog.add(hint, name);
        Button confirm = new Button(I18n.t("common.save"), event -> {
            if (name.getValue().isBlank()) {
                name.setErrorMessage(I18n.t("views.nameRequired"));
                name.setInvalid(true);
                return;
            }
            actions.saveView(name.getValue());
            dialog.close();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.addClickShortcut(Key.ENTER);
        dialog.getFooter().add(new Button(I18n.t("common.cancel"), event -> dialog.close()), confirm);
        dialog.open();
    }
}
