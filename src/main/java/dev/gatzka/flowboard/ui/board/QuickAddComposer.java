package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.util.List;
import java.util.function.Consumer;

/**
 * The "Add card" affordance at the bottom of a lane. Collapsed it is a single button; expanded it
 * is a title field that adds a card on Enter and stays open for the next one, plus a shortcut to
 * the full editor for cards that need more than a title.
 */
final class QuickAddComposer extends Div {

    /** Upper bound on cards created from one paste. */
    static final int MAX_PASTED_LINES = 50;

    private static final String MULTILINE_PASTE_JS = """
            const field = $0;
            field.addEventListener('paste', event => {
              const text = (event.clipboardData || window.clipboardData).getData('text');
              if (text && text.includes('\\n') && text.trim().split('\\n').filter(l => l.trim()).length > 1) {
                event.preventDefault();
                field.dispatchEvent(new CustomEvent('multiline-paste', { detail: { text } }));
              }
            });
            """;

    private final Button openButton = new Button("Add card", VaadinIcon.PLUS.create());
    private final Div editor = new Div();
    private final TextField title = new TextField();
    private final Runnable onOpen;
    private final Runnable onClose;
    private final Consumer<List<String>> onQuickAddMany;

    /**
     * @param onQuickAdd called with the entered title
     * @param onMoreOptions called with whatever was typed so far when the full editor is requested
     * @param onOpen called when the composer expands, so the board can keep it open across reloads
     * @param onClose called when the composer collapses
     */
    /**
     * @param onQuickAddMany called with every non-blank line of a multi-line paste
     */
    QuickAddComposer(Consumer<String> onQuickAdd, Consumer<List<String>> onQuickAddMany, Consumer<String> onMoreOptions,
            Runnable onOpen, Runnable onClose) {
        this.onOpen = onOpen;
        this.onClose = onClose;
        this.onQuickAddMany = onQuickAddMany;
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
        title.setHelperText("Shorthand: !high @name #tag ~3 start:mon due:fri every:week. Paste several lines to add several cards.");
        title.getElement().addEventListener("multiline-paste", event -> pasteLines(event.getEventData().path("event.detail.text").asString()))
                .addEventData("event.detail.text");
        title.addAttachListener(event -> title.getElement().executeJs(MULTILINE_PASTE_JS, title.getElement()));

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

    /** Creates one card per non-blank line, up to {@link #MAX_PASTED_LINES}. */
    void pasteLines(String text) {
        List<String> lines = splitLines(text);
        if (lines.isEmpty()) {
            return;
        }
        title.clear();
        onQuickAddMany.accept(lines);
        title.focus();
    }

    static List<String> splitLines(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines()
                .map(line -> line.strip().replaceFirst("^[-*•\\d.)\\s]+", "").strip())
                .filter(line -> !line.isEmpty())
                .limit(MAX_PASTED_LINES)
                .toList();
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
