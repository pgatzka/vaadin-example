package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.service.Participant;
import java.util.ArrayList;
import java.util.List;

/** Side sheet with the board's recent activity, newest at the bottom like a chat log. */
final class ActivityDialog extends Dialog {

    private final MessageList messages = new MessageList();
    private final Span empty = new Span("Nothing has happened on this board yet.");

    ActivityDialog() {
        addClassName("activity-sheet");
        setHeaderTitle("Activity");
        setModal(false);
        setDraggable(false);
        setCloseOnOutsideClick(true);

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel("Close activity");
        getHeader().add(close);

        empty.addClassName("activity-sheet__empty");
        add(messages, empty);
    }

    /** Shows the given entries, expected newest first. */
    void show(List<ActivityEntry> newestFirst) {
        List<MessageListItem> items = new ArrayList<>(newestFirst.size());
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            ActivityEntry entry = newestFirst.get(i);
            MessageListItem item = new MessageListItem(entry.message(), entry.occurredAt(), entry.actor());
            item.setUserColorIndex(Participant.named(entry.actor()).colorIndex());
            items.add(item);
        }
        messages.setItems(items);
        messages.setVisible(!items.isEmpty());
        empty.setVisible(items.isEmpty());
    }
}
