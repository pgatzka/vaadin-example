package dev.gatzka.flowboard.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What a board's own numbers say needs attention: overdue and stale cards, lanes over their limit,
 * the card that holds up the most others, milestones in trouble, a forecast that misses the
 * latest due date, urgent work nobody owns. Each finding carries the cards it is about so the UI
 * can select them for a bulk action in one click.
 *
 * @param score 100 for a board with nothing to report, less for every finding
 */
public record Health(List<Finding> findings, int score) {

    public enum Severity {
        WARN(15), INFO(5);

        private final int penalty;

        Severity(int penalty) {
            this.penalty = penalty;
        }
    }

    /** The order here is the order findings are listed in within a severity. */
    public enum Kind {
        OVERDUE, MILESTONE_LATE, WIP_OVER, UNASSIGNED_URGENT, KEY_BLOCKER, FORECAST_LATE, MILESTONE_AT_RISK, STALE, BLOCKED
    }

    /**
     * @param count what the message counts: cards, or a percentage for the forecast findings
     * @param subject the lane, card or milestone the finding is about, or {@code null}
     * @param date the date the finding refers to, or {@code null}
     * @param cardIds the cards to select to act on the finding; empty when it is not about particular cards
     */
    public record Finding(Kind kind, Severity severity, int count, String subject, LocalDate date, List<Long> cardIds) {

        public Finding {
            cardIds = List.copyOf(cardIds);
        }

        public boolean hasCards() {
            return !cardIds.isEmpty();
        }
    }

    /** Below this chance a milestone or the board is "at risk"; below {@link #SAFE_CHANCE} it is merely worth a look. */
    public static final double RISKY_CHANCE = 0.5;
    public static final double SAFE_CHANCE = 0.85;

    public Health {
        findings = List.copyOf(findings);
    }

    public static Health of(BoardSnapshot board, LocalDate today, Instant now, Forecast forecast, List<MilestoneProgress> milestones) {
        List<Finding> findings = new ArrayList<>();
        List<CardSnapshot> open = board.lanes().stream().filter(lane -> !lane.done()).flatMap(lane -> lane.cards().stream()).toList();

        cards(findings, Kind.OVERDUE, Severity.WARN, open.stream().filter(card -> card.isOverdue(today)).toList());
        for (MilestoneProgress milestone : milestones) {
            if (milestone.isLate(today)) {
                findings.add(new Finding(Kind.MILESTONE_LATE, Severity.WARN, milestone.open(), milestone.milestone().name(),
                        milestone.milestone().targetDate(), memberIds(open, milestone.milestone().id())));
            }
        }
        for (LaneSnapshot lane : board.lanes()) {
            if (lane.isOverWipLimit()) {
                findings.add(new Finding(Kind.WIP_OVER, Severity.WARN, lane.cards().size(), lane.name(), null,
                        lane.cards().stream().map(CardSnapshot::id).toList()));
            }
        }
        cards(findings, Kind.UNASSIGNED_URGENT, Severity.WARN,
                open.stream().filter(card -> card.priority() == Priority.URGENT && card.assignee() == null).toList());
        DependencyGraph graph = DependencyGraph.of(board);
        graph.keyBlocker().ifPresent(blocker -> {
            int held = graph.holdsUp(blocker.id());
            if (held >= 2) {
                findings.add(new Finding(Kind.KEY_BLOCKER, Severity.WARN, held, blocker.title(), null, List.of(blocker.id())));
            }
        });
        forecast.targetChance().ifPresent(chance -> {
            if (chance < RISKY_CHANCE) {
                findings.add(new Finding(Kind.FORECAST_LATE, Severity.WARN, percent(chance), null, forecast.target(), List.of()));
            }
        });
        for (MilestoneProgress milestone : milestones) {
            Optional<Double> chance = milestone.chance();
            if (chance.isPresent() && chance.get() < SAFE_CHANCE && !milestone.isLate(today)) {
                findings.add(new Finding(Kind.MILESTONE_AT_RISK, chance.get() < RISKY_CHANCE ? Severity.WARN : Severity.INFO,
                        percent(chance.get()), milestone.milestone().name(), milestone.milestone().targetDate(),
                        memberIds(open, milestone.milestone().id())));
            }
        }
        cards(findings, Kind.STALE, Severity.INFO, open.stream().filter(card -> card.age(now).level() == CardAge.Level.STALE).toList());
        cards(findings, Kind.BLOCKED, Severity.INFO, open.stream().filter(CardSnapshot::isBlocked).toList());

        findings.sort(Comparator.comparing(Finding::severity).thenComparing(Finding::kind));
        int score = Math.max(0, 100 - findings.stream().mapToInt(finding -> finding.severity().penalty).sum());
        return new Health(findings, score);
    }

    private static void cards(List<Finding> findings, Kind kind, Severity severity, List<CardSnapshot> cards) {
        if (!cards.isEmpty()) {
            findings.add(new Finding(kind, severity, cards.size(), cards.size() == 1 ? cards.getFirst().title() : null, null,
                    cards.stream().map(CardSnapshot::id).toList()));
        }
    }

    private static List<Long> memberIds(List<CardSnapshot> open, long milestoneId) {
        return open.stream().filter(card -> Long.valueOf(milestoneId).equals(card.milestoneId())).map(CardSnapshot::id).toList();
    }

    private static int percent(double chance) {
        return (int) Math.round(chance * 100);
    }

    public boolean isClear() {
        return findings.isEmpty();
    }

    public long warnings() {
        return findings.stream().filter(finding -> finding.severity() == Severity.WARN).count();
    }
}
