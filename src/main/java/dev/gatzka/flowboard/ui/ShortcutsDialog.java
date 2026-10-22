package dev.gatzka.flowboard.ui;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** Lists the keyboard shortcuts the application understands. */
final class ShortcutsDialog extends Dialog {

    private static final String[][] SHORTCUTS = {
            {"Ctrl + K", "shortcuts.palette"},
            {"Ctrl + /", "shortcuts.thisList"},
            {"/", "shortcuts.filter"},
            {"Enter", "shortcuts.enter"},
            {"Paste", "shortcuts.paste"},
            {"!high @mara #ui ~3", "shortcuts.shorthand"},
            {"due:fri every:week", "shortcuts.due"},
            {"start:mon", "shortcuts.start"},
            {"Ctrl + Enter", "shortcuts.saveDialog"},
            {"Esc", "shortcuts.esc"},
            {"Drag", "shortcuts.drag"},
            {"Tab, \u2191 \u2193 \u2190 \u2192", "shortcuts.focus"},
            {"Enter / Space", "shortcuts.open"},
            {"Shift + \u2191 \u2193 \u2190 \u2192", "shortcuts.move"},
            {"N", "shortcuts.add"},
            {"Ctrl + click", "shortcuts.select"},
    };

    ShortcutsDialog() {
        setHeaderTitle(I18n.t("shortcuts.title"));
        setWidth("min(460px, 95vw)");
        Div list = new Div();
        list.addClassName("shortcuts");
        for (String[] shortcut : SHORTCUTS) {
            Span keys = new Span(shortcut[0]);
            keys.addClassName("shortcuts__keys");
            Span what = new Span(I18n.t(shortcut[1]));
            list.add(keys, what);
        }
        add(list);
        getFooter().add(new Button(I18n.t("common.close"), event -> close()));
    }
}
