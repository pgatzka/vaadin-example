package dev.gatzka.flowboard.domain;

import java.time.Instant;

public record AttachmentSnapshot(long id, String fileName, String contentType, long size, String uploadedBy, Instant uploadedAt) {

    static AttachmentSnapshot of(Attachment attachment) {
        return new AttachmentSnapshot(attachment.getId(), attachment.getFileName(), attachment.getContentType(), attachment.getSize(),
                attachment.getUploadedBy(), attachment.getUploadedAt());
    }

    public boolean isImage() {
        return contentType.startsWith("image/");
    }

    /** "12 KB", "3.4 MB". */
    public String humanSize() {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", size / (1024.0 * 1024));
    }
}
