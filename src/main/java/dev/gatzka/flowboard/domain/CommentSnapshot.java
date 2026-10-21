package dev.gatzka.flowboard.domain;

import java.time.Instant;

public record CommentSnapshot(long id, String author, String text, Instant createdAt) {

    public static CommentSnapshot of(Comment comment) {
        return new CommentSnapshot(comment.getId(), comment.getAuthor(), comment.getText(), comment.getCreatedAt());
    }
}
