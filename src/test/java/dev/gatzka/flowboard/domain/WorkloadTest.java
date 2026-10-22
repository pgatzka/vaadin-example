package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.Workload.Person;
import dev.gatzka.flowboard.domain.Workload.Suggestion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkloadTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22); // a Thursday
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    @Test
    void bucketsCardsByPersonAndWeekAndCountsOverdueAsThisWeek() {
        Workload workload = Workload.of(List.of(
                card(1, "Priya", TODAY.minusDays(3), 5),
                card(2, "Priya", TODAY.plusDays(1), 3),
                card(3, "priya", MONDAY.plusWeeks(1), null),
                card(4, "Jonas", MONDAY.plusWeeks(3).plusDays(6), 2),
                card(5, "Jonas", MONDAY.plusWeeks(4), 8),
                card(6, "Jonas", null, 1),
                card(7, null, TODAY, 4),
                card(8, "  ", null, 1)), TODAY);

        assertThat(workload.weekStart()).isEqualTo(MONDAY);
        assertThat(workload.pointsBased()).isTrue();
        assertThat(workload.people()).extracting(Person::name).containsExactly("Jonas", "Priya", "Unassigned");
        Person priya = workload.person("priya").orElseThrow();
        assertThat(priya.overdue()).isEqualTo(1);
        assertThat(workload.load(priya.week(0))).isEqualTo(8);
        assertThat(priya.week(0).count()).isEqualTo(2);
        assertThat(workload.load(priya.week(1))).as("an unestimated card weighs one point").isEqualTo(1);
        assertThat(priya.openCards()).isEqualTo(3);
        Person jonas = workload.person("Jonas").orElseThrow();
        assertThat(workload.load(jonas.week(3))).isEqualTo(2);
        assertThat(workload.load(jonas.later())).isEqualTo(8);
        assertThat(workload.load(jonas.undated())).isEqualTo(1);
        assertThat(workload.load(jonas)).isEqualTo(11);
        Person unassigned = workload.people().getLast();
        assertThat(unassigned.isUnassigned()).isTrue();
        assertThat(unassigned.week(0).count()).isEqualTo(1);
        assertThat(unassigned.undated().count()).isEqualTo(1);
        assertThat(workload.openCards()).isEqualTo(8);
        assertThat(workload.unit(3)).isEqualTo("3 pt");
    }

    @Test
    void countsCardsWhenNobodyEstimates() {
        Workload workload = Workload.of(List.of(card(1, "Mara", TODAY, null), card(2, "Mara", TODAY, null)), TODAY);

        assertThat(workload.pointsBased()).isFalse();
        assertThat(workload.load(workload.person("Mara").orElseThrow().week(0))).isEqualTo(2);
        assertThat(workload.unit(1)).isEqualTo("1 card");
        assertThat(workload.unit(2)).isEqualTo("2 cards");
        assertThat(Workload.of(List.of(), TODAY).isEmpty()).isTrue();
    }

    @Test
    void suggestsMovingTheLargestFittingCardToWhoeverHasTheMostRoom() {
        Workload workload = Workload.of(List.of(
                card(1, "Priya", TODAY, 5),
                card(2, "Priya", TODAY, 5),
                card(3, "Priya", TODAY.plusDays(1), 3),
                card(4, "Jonas", TODAY, 2),
                card(5, "Mara", TODAY, 6),
                card(6, null, TODAY.plusDays(2), 4)), TODAY);

        List<Suggestion> suggestions = workload.suggestions(10);

        // Priya is at 13: the largest card that fits into Jonas' room (8) is a 5-point one; then the unassigned 4 goes to Mara (6 -> 10).
        assertThat(suggestions).hasSize(2);
        Suggestion move = suggestions.get(0);
        assertThat(move.from()).isEqualTo("Priya");
        assertThat(move.to()).isEqualTo("Jonas");
        assertThat(move.card().card().estimate()).isEqualTo(5);
        assertThat(move.weekIndex()).isZero();
        assertThat(move.fromAfter()).isEqualTo(8);
        assertThat(move.toAfter()).isEqualTo(7);
        Suggestion assign = suggestions.get(1);
        assertThat(assign.isAssignment()).isTrue();
        assertThat(assign.to()).isEqualTo("Mara");
        assertThat(assign.toAfter()).isEqualTo(10);

        assertThat(workload.suggestions(20)).as("everybody fits: nothing to move, the unassigned card goes to the person with most room")
                .singleElement().satisfies(s -> assertThat(s.to()).isEqualTo("Jonas"));
        assertThat(workload.suggestions(3)).as("nobody has room").isEmpty();
    }

    private static CardLocation card(long id, String assignee, LocalDate due, Integer estimate) {
        CardSnapshot card = new CardSnapshot(id, 10, "Card " + id, "", Priority.MEDIUM, null, due, assignee, Set.of(), estimate, null, 0,
                Set.of(), List.of(), List.of(), List.of(), List.of(), 0, NOW, null, NOW, null);
        return new CardLocation(1, "Board", "Todo", false, card);
    }
}
