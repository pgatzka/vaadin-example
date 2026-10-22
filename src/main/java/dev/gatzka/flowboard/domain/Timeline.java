package dev.gatzka.flowboard.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A board's dated cards laid out on a window of whole weeks: one bar per card from its start date (or its due date
 * when it has none) to its due date. Cards with a start date but no due date run to the end of the window.
 */
public record Timeline(LocalDate from, int weeks, List<Group> groups, int undated, int outside) {

    public static final int DEFAULT_WEEKS = 8;

    /** How rows are grouped. */
    public enum GroupBy {
        LANE("By lane"),
        PERSON("By person");

        private final String label;

        GroupBy(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Group(String name, List<Bar> bars) {

        public Group {
            bars = List.copyOf(bars);
        }
    }

    /** A card on the axis. {@code milestone} marks a card with only a due date; {@code openEnded} one with only a start. */
    public record Bar(CardSnapshot card, String lane, boolean done, LocalDate start, LocalDate end, boolean milestone, boolean openEnded) {

        public long offsetDays(LocalDate from) {
            return ChronoUnit.DAYS.between(from, start);
        }

        public long lengthDays() {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }

        public boolean isOverdue(LocalDate today) {
            return !done && card.dueDate() != null && card.dueDate().isBefore(today);
        }
    }

    public Timeline {
        groups = List.copyOf(groups);
    }

    /** The Monday on or before the given day, minus one week of context. */
    public static LocalDate windowStart(LocalDate anchor) {
        return anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    public static Timeline of(BoardSnapshot board, GroupBy groupBy, LocalDate from, int weeks) {
        LocalDate to = from.plusWeeks(weeks).minusDays(1);
        Map<String, List<Bar>> groups = new LinkedHashMap<>();
        List<Bar> unassigned = new ArrayList<>();
        int undated = 0;
        int outside = 0;
        for (LaneSnapshot lane : board.lanes()) {
            for (CardSnapshot card : lane.cards()) {
                if (card.startDate() == null && card.dueDate() == null) {
                    undated++;
                    continue;
                }
                boolean milestone = card.startDate() == null;
                boolean openEnded = card.dueDate() == null;
                LocalDate start = milestone ? card.dueDate() : card.startDate();
                LocalDate end = openEnded ? to : card.dueDate();
                if (end.isBefore(from) || start.isAfter(to)) {
                    outside++;
                    continue;
                }
                Bar bar = new Bar(card, lane.name(), lane.done(), start.isBefore(from) ? from : start, end.isAfter(to) ? to : end,
                        milestone, openEnded);
                if (groupBy == GroupBy.LANE) {
                    groups.computeIfAbsent(lane.name(), key -> new ArrayList<>()).add(bar);
                } else if (card.assignee() == null) {
                    unassigned.add(bar);
                } else {
                    groups.computeIfAbsent(card.assignee(), key -> new ArrayList<>()).add(bar);
                }
            }
        }
        Comparator<Bar> order = Comparator.comparing(Bar::start).thenComparing(bar -> bar.card().title(), String.CASE_INSENSITIVE_ORDER);
        List<Group> ordered = new ArrayList<>();
        if (groupBy == GroupBy.PERSON) {
            List<String> names = new ArrayList<>(groups.keySet());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            names.forEach(name -> ordered.add(new Group(name, groups.get(name).stream().sorted(order).toList())));
            if (!unassigned.isEmpty()) {
                ordered.add(new Group(Grouping.UNASSIGNED, unassigned.stream().sorted(order).toList()));
            }
        } else {
            groups.forEach((name, bars) -> ordered.add(new Group(name, bars.stream().sorted(order).toList())));
        }
        return new Timeline(from, weeks, ordered, undated, outside);
    }

    public LocalDate to() {
        return from.plusWeeks(weeks).minusDays(1);
    }

    public int days() {
        return weeks * 7;
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    public int barCount() {
        return groups.stream().mapToInt(group -> group.bars().size()).sum();
    }

    public List<LocalDate> weekStarts() {
        List<LocalDate> starts = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            starts.add(from.plusWeeks(i));
        }
        return starts;
    }

    /** Day offset of {@code day} inside the window, or -1 when it falls outside. */
    public int offsetOf(LocalDate day) {
        long offset = ChronoUnit.DAYS.between(from, day);
        return offset < 0 || offset >= days() ? -1 : (int) offset;
    }

    public static String monthLabel(LocalDate day) {
        return day.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
    }
}
