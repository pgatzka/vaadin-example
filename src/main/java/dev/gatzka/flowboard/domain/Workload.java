package dev.gatzka.flowboard.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Who has how much on their plate, week by week: every open card across the visible boards, bucketed by assignee and
 * due week. Load is measured in story points when the team estimates (an unestimated card counts as one point),
 * otherwise in cards.
 */
public record Workload(LocalDate weekStart, List<Person> people, boolean pointsBased) {

    /** How many weeks ahead the view looks; later due dates share a "Later" column. */
    public static final int WEEKS = 4;

    /** The name under which cards without an assignee are listed. */
    public static final String UNASSIGNED = "Unassigned";

    /** The cards of one person in one column. */
    public record Slot(List<CardLocation> cards) {

        public Slot {
            cards = List.copyOf(cards);
        }

        static final Slot EMPTY = new Slot(List.of());

        public int count() {
            return cards.size();
        }

        public int points() {
            return cards.stream().mapToInt(location -> pointsOf(location.card())).sum();
        }

        public boolean isEmpty() {
            return cards.isEmpty();
        }
    }

    /** One row: a person with a slot per upcoming week, plus later and undated cards. */
    public record Person(String name, List<Slot> weeks, Slot later, Slot undated, int overdue) {

        public Person {
            weeks = List.copyOf(weeks);
        }

        public boolean isUnassigned() {
            return UNASSIGNED.equals(name);
        }

        public int openCards() {
            return weeks.stream().mapToInt(Slot::count).sum() + later.count() + undated.count();
        }

        public int openPoints() {
            return weeks.stream().mapToInt(Slot::points).sum() + later.points() + undated.points();
        }

        public Slot week(int index) {
            return weeks.get(index);
        }
    }

    /** A proposed reassignment that brings somebody under capacity; {@code from} is null for an unassigned card. */
    public record Suggestion(String from, String to, CardLocation card, int weekIndex, int fromAfter, int toAfter) {

        public boolean isAssignment() {
            return from == null;
        }
    }

    public Workload {
        people = List.copyOf(people);
    }

    /** Buckets the given open cards; {@code today} decides which week is the current one. Overdue cards count as this week. */
    public static Workload of(List<CardLocation> openCards, LocalDate today) {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean pointsBased = openCards.stream().anyMatch(location -> location.card().isEstimated());
        Map<String, List<List<CardLocation>>> weeks = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<CardLocation>> later = new HashMap<>();
        Map<String, List<CardLocation>> undated = new HashMap<>();
        Map<String, Integer> overdue = new HashMap<>();
        for (CardLocation location : openCards) {
            String name = location.card().assignee() == null || location.card().assignee().isBlank()
                    ? UNASSIGNED : location.card().assignee().strip();
            weeks.computeIfAbsent(name, key -> {
                List<List<CardLocation>> slots = new ArrayList<>();
                for (int i = 0; i < WEEKS; i++) {
                    slots.add(new ArrayList<>());
                }
                return slots;
            });
            LocalDate due = location.card().dueDate();
            if (due == null) {
                undated.computeIfAbsent(name, key -> new ArrayList<>()).add(location);
                continue;
            }
            if (due.isBefore(today)) {
                overdue.merge(name, 1, Integer::sum);
            }
            int index = due.isBefore(weekStart) ? 0 : (int) ((due.toEpochDay() - weekStart.toEpochDay()) / 7);
            if (index >= WEEKS) {
                later.computeIfAbsent(name, key -> new ArrayList<>()).add(location);
            } else {
                weeks.get(name).get(index).add(location);
            }
        }
        List<Person> people = new ArrayList<>();
        Person unassigned = null;
        for (Map.Entry<String, List<List<CardLocation>>> entry : weeks.entrySet()) {
            String name = entry.getKey();
            Person person = new Person(name, entry.getValue().stream().map(Slot::new).toList(),
                    new Slot(later.getOrDefault(name, List.of())), new Slot(undated.getOrDefault(name, List.of())),
                    overdue.getOrDefault(name, 0));
            if (person.isUnassigned()) {
                unassigned = person;
            } else {
                people.add(person);
            }
        }
        if (unassigned != null) {
            people.add(unassigned);
        }
        return new Workload(weekStart, people, pointsBased);
    }

    private static int pointsOf(CardSnapshot card) {
        return card.isEstimated() ? card.estimate() : 1;
    }

    public boolean isEmpty() {
        return people.isEmpty();
    }

    public int openCards() {
        return people.stream().mapToInt(Person::openCards).sum();
    }

    /** The load of a slot in the unit this workload is measured in. */
    public int load(Slot slot) {
        return pointsBased ? slot.points() : slot.count();
    }

    public int load(CardLocation location) {
        return pointsBased ? pointsOf(location.card()) : 1;
    }

    public int load(Person person) {
        return pointsBased ? person.openPoints() : person.openCards();
    }

    public String unit(int amount) {
        return pointsBased ? amount + " pt" : amount + (amount == 1 ? " card" : " cards");
    }

    /** Monday of the given week column. */
    public LocalDate weekStart(int index) {
        return weekStart.plusWeeks(index);
    }

    public Optional<Person> person(String name) {
        return people.stream().filter(person -> person.name().equalsIgnoreCase(name)).findFirst();
    }

    /**
     * Reassignments that would bring everybody at or under {@code capacity} per week where that is possible: the
     * largest card that fits goes to the colleague with the most room; unassigned cards are handed out the same way.
     * The list is capped so that it stays readable.
     */
    public List<Suggestion> suggestions(int capacity) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (int week = 0; week < WEEKS; week++) {
            Map<String, Integer> loads = new LinkedHashMap<>();
            Map<String, List<CardLocation>> remaining = new LinkedHashMap<>();
            for (Person person : people) {
                loads.put(person.name(), person.isUnassigned() ? 0 : load(person.week(week)));
                remaining.put(person.name(), new ArrayList<>(person.week(week).cards()));
            }
            List<String> givers = new ArrayList<>(loads.keySet());
            // the most loaded person first; unassigned cards are handed out once everybody is under capacity
            givers.sort(Comparator.comparingInt((String name) -> UNASSIGNED.equals(name) ? Integer.MIN_VALUE : loads.get(name)).reversed());
            for (String giver : givers) {
                boolean unassigned = UNASSIGNED.equals(giver);
                while ((unassigned && !remaining.get(giver).isEmpty()) || (!unassigned && loads.get(giver) > capacity)) {
                    Optional<Map.Entry<String, Integer>> receiver = loads.entrySet().stream()
                            .filter(entry -> !entry.getKey().equals(giver) && !UNASSIGNED.equals(entry.getKey()))
                            .filter(entry -> entry.getValue() < capacity)
                            .min(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                                    .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
                    if (receiver.isEmpty()) {
                        break;
                    }
                    int room = capacity - receiver.get().getValue();
                    Optional<CardLocation> card = remaining.get(giver).stream()
                            .filter(location -> load(location) <= room)
                            .max(Comparator.comparingInt(this::load));
                    if (card.isEmpty()) {
                        break;
                    }
                    String to = receiver.get().getKey();
                    remaining.get(giver).remove(card.get());
                    loads.merge(to, load(card.get()), Integer::sum);
                    if (!unassigned) {
                        loads.merge(giver, -load(card.get()), Integer::sum);
                    }
                    suggestions.add(new Suggestion(unassigned ? null : giver, to, card.get(), week,
                            unassigned ? 0 : loads.get(giver), loads.get(to)));
                    if (suggestions.size() >= MAX_SUGGESTIONS) {
                        return suggestions;
                    }
                }
            }
        }
        return suggestions;
    }

    private static final int MAX_SUGGESTIONS = 8;
}
