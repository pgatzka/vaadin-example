package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.BoardMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardMemberRepository extends JpaRepository<BoardMember, Long> {

    List<BoardMember> findAllByBoardIdOrderByUsernameAsc(long boardId);

    List<BoardMember> findAllByUsername(String username);

    boolean existsByBoardIdAndUsername(long boardId, String username);

    long countByBoardId(long boardId);

    void deleteByBoardIdAndUsername(long boardId, String username);

    void deleteAllByBoardId(long boardId);
}
