package dev.gatzka.flowboard.domain;

public record LaneRuleSnapshot(long id, long laneId, RuleAction action, String argument) {

    public static LaneRuleSnapshot of(LaneRule rule) {
        return new LaneRuleSnapshot(rule.getId(), rule.getLaneId(), rule.getAction(), rule.getArgument());
    }

    public String describe() {
        return action.describe(argument);
    }
}
