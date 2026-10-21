package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.util.function.Consumer;

/**
 * The "Add card" affordance at the bottom of a lane. Collapsed it is a single button; expanded it
 * is a title field that adds a card on Enter and stays open for the next one, plus a shortcut to
 * the full editor for cards that need more than a title.
 */
final class QuickAddComposer extends Div {

    private final Button openButton = new Button("Add card", VaadinIcon.PLUS.create());
    private final Div editor = new Div();
    private final TextField title = new TextField();
    private final Runnable onOpen;
    private final Runnable onClose;

    /**
     * @param onQuickAdd called with the entered title
     * @param onMoreOptions called with whatever was typed so far when the full editor is requested
     * @param onOpen called when the composer expands, so the board can keep it open across reloads
     * @param onClose called when the composer collapses
     */
    QuickAddComposer(Consumer<String> onQuickAdd, Consumer<String> onMoreOptions, Runnable onOpen, Runnable onClose) {
        this.onOpen = onOpen;
        this.onClose = onClose;
        addClassName("quick-add");

        openButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        openButton.addClassName("quick-add__open");
        openButton.addClickListener(event -> open());

        title.setPlaceholder("Card title");
        title.setMaxLength(120);
        title.setWidthFull();
        title.setClearButtonVisible(true);
        title.setValueChangeMode(ValueChangeMode.EAGER);
        title.addKeyPressListener(Key.ENTER, event -> submit(onQuickAdd));
        title.addKeyDownListener(Key.ESCAPE, event -> close());

        Button add = new Button("Add", event -> submit(onQuickAdd));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        Button more = new Button(VaadinIcon.ELLIPSIS_DOTS_H.create(), event -> {
            String typed = title.getValue().strip();
            close();
            onMoreOptions.accept(typed);
        });
        more.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        more.setTooltipText("More options");
        more.setAriaLabel("Open the full card editor");

        Button cancel = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        cancel.setAriaLabel("Cancel");

        Div actions = new Div(add, more, cancel);
        actions.addClassName("quick-add__actions");
        editor.addClassName("quick-add__editor");
        editor.add(title, actions);
        editor.setVisible(false);

        add(openButton, editor);
    }

    void open() {
        openButton.setVisible(false);
        editor.setVisible(true);
        title.focus();
        onOpen.run();
    }

    boolean isOpen() {
        return editor.isVisible();
    }

    private void close() {
        title.clear();
        editor.setVisible(false);
        openButton.setVisible(true);
        onClose.run();
    }

    private void submit(Consumer<String> onQuickAdd) {
        String value = title.getValue().strip();
        if (value.isEmpty()) {
            title.focus();
            return;
        }
        title.clear();
        onQuickAdd.accept(value);
        title.focus();
    }
}
