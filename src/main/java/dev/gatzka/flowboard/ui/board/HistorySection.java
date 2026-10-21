package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.service.Participant;
import java.util.ArrayList;
import java.util.List;

/** Everything that happened to one card, oldest first. */
final class HistorySection extends Div {

    private final MessageList messages = new MessageList();

    HistorySection(List<ActivityEntry> newestFirst) {
        addClassName("history");
        add(messages);
        show(newestFirst);
    }

    void show(List<ActivityEntry> newestFirst) {
        List<MessageListItem> items = new ArrayList<>(newestFirst.size());
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            ActivityEntry entry = newestFirst.get(i);
            MessageListItem item = new MessageListItem(entry.message(), entry.occurredAt(), entry.actor());
            item.setUserColorIndex(Participant.named(entry.actor()).colorIndex());
            items.add(item);
        }
        messages.setItems(items);
    }
}
