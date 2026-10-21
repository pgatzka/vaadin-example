package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
}
