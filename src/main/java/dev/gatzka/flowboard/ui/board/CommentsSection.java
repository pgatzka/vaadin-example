package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.CommentSnapshot;
import dev.gatzka.flowboard.service.Participant;
import java.util.List;
import java.util.function.Consumer;

/** Comment thread of a card with an input to add to it. */
final class CommentsSection extends Div {

    private final MessageList messages = new MessageList();
    private final Span empty = new Span(I18n.t("comments.empty"));

    CommentsSection(CardSnapshot card, CardEditorActions actions, Consumer<CardSnapshot> onChanged) {
        addClassName("comments");
        messages.setMarkdown(true);
        empty.addClassName("comments__empty");

        MessageInput input = new MessageInput(event -> {
            // Mention people with @name; they get a notification.
            String text = event.getValue().strip();
            if (!text.isEmpty()) {
                onChanged.accept(actions.addComment(card.id(), text));
            }
        });
        input.addClassName("comments__input");

        add(messages, empty, input);
        show(card);
    }

    void show(CardSnapshot card) {
        List<MessageListItem> items = card.comments().stream().map(CommentsSection::item).toList();
        messages.setItems(items);
        messages.setVisible(!items.isEmpty());
        empty.setVisible(items.isEmpty());
    }

    private static MessageListItem item(CommentSnapshot comment) {
        MessageListItem item = new MessageListItem(comment.text(), comment.createdAt(), comment.author());
        item.setUserColorIndex(Participant.named(comment.author()).colorIndex());
        return item;
    }
}
