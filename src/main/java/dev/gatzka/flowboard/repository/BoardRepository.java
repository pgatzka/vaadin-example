package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.Board;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, Long> {
}
