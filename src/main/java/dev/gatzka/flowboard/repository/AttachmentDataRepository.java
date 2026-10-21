package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.AttachmentData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentDataRepository extends JpaRepository<AttachmentData, Long> {
}
