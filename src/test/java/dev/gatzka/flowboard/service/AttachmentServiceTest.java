package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.AttachmentSnapshot;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.repository.AttachmentDataRepository;
import dev.gatzka.flowboard.repository.AttachmentRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AttachmentServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private AttachmentService attachments;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentDataRepository dataRepository;

    @Autowired
    private ActivityLog activityLog;

    private BoardSnapshot board;
    private CardSnapshot card;

    @BeforeEach
    void seed() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        board = boardService.createBoard(new BoardDraft("Files", null), "Seeder");
        card = boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("With files"), "Seeder");
    }

    @Test
    void filesAreStoredListedAndDownloadable() {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        CardSnapshot updated = attachments.attach(card.id(), "notes.txt", "text/plain", bytes, "Mara");

        assertThat(updated.attachments()).hasSize(1);
        AttachmentSnapshot file = updated.attachments().getFirst();
        assertThat(file.fileName()).isEqualTo("notes.txt");
        assertThat(file.contentType()).isEqualTo("text/plain");
        assertThat(file.size()).isEqualTo(5);
        assertThat(file.humanSize()).isEqualTo("5 B");
        assertThat(file.uploadedBy()).isEqualTo("Mara");
        assertThat(attachments.content(file.id())).contains(bytes);
        assertThat(activityLog.recent(board.id()).getFirst().message()).isEqualTo("attached notes.txt to \"With files\"");

        CardSnapshot fromBoard = boardService.findBoard(board.id()).orElseThrow().lanes().get(0).cards().getFirst();
        assertThat(fromBoard.hasAttachments()).isTrue();
    }

    @Test
    void removingAnAttachmentDropsItsBytesToo() {
        AttachmentSnapshot file = attachments.attach(card.id(), "a.bin", null, new byte[] {1, 2, 3}, "Mara").attachments().getFirst();
        assertThat(file.contentType()).isEqualTo("application/octet-stream");

        CardSnapshot after = attachments.remove(file.id(), "Mara");

        assertThat(after.attachments()).isEmpty();
        assertThat(attachmentRepository.count()).isZero();
        assertThat(dataRepository.count()).isZero();
    }

    @Test
    void attachmentsGoWithTheirCard() {
        attachments.attach(card.id(), "a.txt", "text/plain", new byte[] {1}, "Mara");
        attachments.attach(card.id(), "b.txt", "text/plain", new byte[] {2}, "Mara");

        boardService.deleteCard(card.id(), "Seeder");

        assertThat(attachmentRepository.count()).isZero();
        assertThat(dataRepository.count()).isZero();
    }

    @Test
    void emptyAndOversizedFilesAreRejected() {
        assertThatThrownBy(() -> attachments.attach(card.id(), "empty.txt", "text/plain", new byte[0], "Mara"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> attachments.attach(card.id(), "big.bin", "application/octet-stream", new byte[(int) AttachmentService.MAX_SIZE + 1], "Mara"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5 MB");
        assertThat(new AttachmentSnapshot(1, "x", "image/png", 3 * 1024 * 1024 + 500_000, "m", java.time.Instant.EPOCH).humanSize()).isEqualTo("3.5 MB");
        assertThat(new AttachmentSnapshot(1, "x", "image/png", 20 * 1024, "m", java.time.Instant.EPOCH).isImage()).isTrue();
    }
}
