package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A file attached to a card: metadata only, the bytes are in {@link AttachmentData}. */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 60)
    private String uploadedBy;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    protected Attachment() {
        // JPA
    }

    Attachment(Card card, String fileName, String contentType, long size, String uploadedBy, Instant uploadedAt) {
        this.card = requireNonNull(card);
        this.fileName = Text.require(fileName, "File name");
        this.contentType = Text.require(contentType, "Content type");
        this.size = size;
        this.uploadedBy = Text.require(uploadedBy, "Uploader");
        this.uploadedAt = requireNonNull(uploadedAt);
    }

    public Long getId() {
        return id;
    }

    public Card getCard() {
        return card;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
