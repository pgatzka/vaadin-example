package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
}
