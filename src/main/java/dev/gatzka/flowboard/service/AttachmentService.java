package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Attachment;
import dev.gatzka.flowboard.domain.AttachmentData;
import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.repository.AttachmentDataRepository;
import dev.gatzka.flowboard.repository.AttachmentRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Files on cards, stored in the database. Small by design: see {@link #MAX_SIZE}. */
@Service
@Transactional
public class AttachmentService {

    public static final long MAX_SIZE = 5L * 1024 * 1024;

    private final CardRepository cards;
    private final AttachmentRepository attachments;
    private final AttachmentDataRepository contents;
    private final ActivityLog activityLog;
    private final Clock clock;

    AttachmentService(CardRepository cards, AttachmentRepository attachments, AttachmentDataRepository contents,
            ActivityLog activityLog, Clock clock) {
        this.cards = cards;
        this.attachments = attachments;
        this.contents = contents;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public CardSnapshot attach(long cardId, String fileName, String contentType, byte[] data, String actor) {
        if (data.length == 0) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (data.length > MAX_SIZE) {
            throw new IllegalArgumentException("Files are limited to %d MB".formatted(MAX_SIZE / (1024 * 1024)));
        }
        Card card = requireCard(cardId);
        Attachment attachment = card.addAttachment(fileName, contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                data.length, actor, clock.instant());
        attachments.saveAndFlush(attachment);
        contents.save(new AttachmentData(attachment.getId(), data));
        activityLog.record(BoardChangedEvent.content(boardIdOf(card), actor,
                "attached %s to \"%s\"".formatted(attachment.getFileName(), card.getTitle())).forCard(cardId));
        return CardSnapshot.of(card);
    }

    public CardSnapshot remove(long attachmentId, String actor) {
        Attachment attachment = attachments.findById(attachmentId).orElseThrow(() -> new NotFoundException("Attachment", attachmentId));
        Card card = attachment.getCard();
        card.removeAttachment(attachment, clock.instant());
        attachments.flush();
        activityLog.record(BoardChangedEvent.content(boardIdOf(card), actor,
                "removed %s from \"%s\"".formatted(attachment.getFileName(), card.getTitle())).forCard(card.getId()));
        return CardSnapshot.of(card);
    }

    /** Chooses the card's cover image; {@code null} goes back to the default (the first image). */
    public CardSnapshot setCover(long cardId, Long attachmentId, String actor) {
        Card card = requireCard(cardId);
        card.setCover(attachmentId, clock.instant());
        activityLog.record(BoardChangedEvent.content(boardIdOf(card), actor,
                (attachmentId == null ? "removed the cover of \"%s\"" : "set the cover of \"%s\"").formatted(card.getTitle())).forCard(cardId));
        return CardSnapshot.of(card);
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> content(long attachmentId) {
        return contents.findById(attachmentId).map(AttachmentData::getData);
    }

    private Card requireCard(long cardId) {
        return cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
    }

    private static long boardIdOf(Card card) {
        return card.getLane().getBoard().getId();
    }
}
