package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.RunningTimer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunningTimerRepository extends JpaRepository<RunningTimer, Long> {

    Optional<RunningTimer> findByPerson(String person);

    List<RunningTimer> findAllByFocusMinutesIsNotNull();
}
