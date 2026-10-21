package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One automation on a lane. */
@Entity
@Table(name = "lane_rules")
public class LaneRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lane_id", nullable = false)
    private Long laneId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private RuleAction action;

    @Column(length = 120)
    private String argument;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LaneRule() {
    }

    public LaneRule(long laneId, RuleAction action, String argument, Instant createdAt) {
        this.laneId = laneId;
        this.action = action;
        this.argument = argument;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getLaneId() {
        return laneId;
    }

    public RuleAction getAction() {
        return action;
    }

    public String getArgument() {
        return argument;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
