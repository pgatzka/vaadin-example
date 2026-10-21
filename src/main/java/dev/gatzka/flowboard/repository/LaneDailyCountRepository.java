package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.LaneDailyCount;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaneDailyCountRepository extends JpaRepository<LaneDailyCount, Long> {

    Optional<LaneDailyCount> findByLaneIdAndDay(long laneId, LocalDate day);

    List<LaneDailyCount> findAllByBoardIdAndDayGreaterThanEqualOrderByDayAsc(long boardId, LocalDate from);

    void deleteAllByBoardId(long boardId);
}
