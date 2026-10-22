package dev.gatzka.flowboard.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** Lists the keyboard shortcuts the application understands. */
final class ShortcutsDialog extends Dialog {

    private static final String[][] SHORTCUTS = {
            {"Ctrl + K", "Command palette: search all boards, open a board, run an action"},
            {"Ctrl + /", "Show this list"},
            {"/", "Focus the card filter on a board"},
            {"Enter", "Add the card typed into a lane's composer"},
            {"Paste", "Several lines pasted into a composer become several cards"},
            {"!high @mara #ui ~3", "In a composer: priority, assignee, tag and points for the new card"},
            {"due:fri every:week", "In a composer: due date (today, tomorrow, a weekday, +3, a date) and repetition"},
            {"Ctrl + Enter", "Save the open dialog"},
            {"Esc", "Close a dialog or the composer"},
            {"Drag", "Move cards between lanes, drag a lane header to reorder lanes"},
            {"Tab, ↑ ↓ ← →", "Move the keyboard focus between cards"},
            {"Enter / Space", "Open the focused card"},
            {"Shift + ↑ ↓ ← →", "Move the focused card within its lane or to the next lane"},
            {"N", "Add a card to the focused card's lane"},
            {"Ctrl + click", "Select several cards, then move, assign, tag, prioritise or archive them together"},
    };

    ShortcutsDialog() {
        setHeaderTitle("Keyboard shortcuts");
        setWidth("min(460px, 95vw)");
        Div list = new Div();
        list.addClassName("shortcuts");
        for (String[] shortcut : SHORTCUTS) {
            Span keys = new Span(shortcut[0]);
            keys.addClassName("shortcuts__keys");
            Span what = new Span(shortcut[1]);
            list.add(keys, what);
        }
        add(list);
        getFooter().add(new Button("Close", event -> close()));
    }
}
