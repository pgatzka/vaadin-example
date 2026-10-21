package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Comment;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.CommentRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comments on cards. */
@Service
@Transactional
public class CommentService {

    /** Longest excerpt of a comment quoted in the activity feed. */
    private static final int EXCERPT_LENGTH = 60;

    private final CardRepository cards;
    private final CommentRepository comments;
    private final ActivityLog activityLog;
    private final Clock clock;

    CommentService(CardRepository cards, CommentRepository comments, ActivityLog activityLog, Clock clock) {
        this.cards = cards;
        this.comments = comments;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public CardSnapshot add(long cardId, String author, String text) {
        Card card = cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
        Comment comment = card.addComment(author, text, clock.instant());
        comments.saveAndFlush(comment);
        activityLog.record(BoardChangedEvent.content(card.getLane().getBoard().getId(), author,
                "commented on \"%s\": %s".formatted(card.getTitle(), excerpt(comment.getText()))).forCard(cardId));
        return CardSnapshot.of(card);
    }

    static String excerpt(String text) {
        String singleLine = text.replaceAll("\\s+", " ").strip();
        return singleLine.length() <= EXCERPT_LENGTH ? singleLine : singleLine.substring(0, EXCERPT_LENGTH - 1).stripTrailing() + "…";
    }
}
