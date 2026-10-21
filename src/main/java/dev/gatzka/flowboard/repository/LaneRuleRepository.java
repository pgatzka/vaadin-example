package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.LaneRule;
import dev.gatzka.flowboard.domain.RuleAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaneRuleRepository extends JpaRepository<LaneRule, Long> {

    List<LaneRule> findAllByLaneIdOrderByCreatedAtAsc(long laneId);

    List<LaneRule> findAllByAction(RuleAction action);

    long countByLaneId(long laneId);
}
