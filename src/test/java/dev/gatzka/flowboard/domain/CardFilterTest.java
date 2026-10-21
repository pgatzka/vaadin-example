package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CardFilterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final LaneSnapshot OPEN_LANE = new LaneSnapshot(1, "Doing", 0, null, false, List.of());
    private static final LaneSnapshot DONE_LANE = new LaneSnapshot(2, "Done", 1, null, true, List.of());

    @Test
    void emptyFilterMatchesEverything() {
        assertThat(CardFilter.none().isActive()).isFalse();
        assertThat(CardFilter.none().matches(card("Anything", null, null, Priority.LOW), OPEN_LANE, TODAY, "Mara")).isTrue();
    }

    @Test
    void textSearchesTitleDescriptionAssigneeAndTags() {
        CardSnapshot card = card("Ship it", "Needs QA", "Mara", Priority.MEDIUM, "release");
        assertThat(CardFilter.none().withText("ship").matches(card, OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(CardFilter.none().withText("qa").matches(card, OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(CardFilter.none().withText("mara").matches(card, OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(CardFilter.none().withText("RELEASE").matches(card, OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(CardFilter.none().withText("nope").matches(card, OPEN_LANE, TODAY, "x")).isFalse();
    }

    @Test
    void priorityMustMatchExactly() {
        CardFilter high = CardFilter.none().withPriority(Priority.HIGH);
        assertThat(high.matches(card("a", null, null, Priority.HIGH), OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(high.matches(card("a", null, null, Priority.URGENT), OPEN_LANE, TODAY, "x")).isFalse();
    }

    @Test
    void overdueIgnoresCardsInDoneLanes() {
        CardFilter overdue = CardFilter.none().withOverdueOnly(true);
        CardSnapshot late = card("late", TODAY.minusDays(1));
        assertThat(overdue.matches(late, OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(overdue.matches(late, DONE_LANE, TODAY, "x")).isFalse();
        assertThat(overdue.matches(card("today", TODAY), OPEN_LANE, TODAY, "x")).isFalse();
        assertThat(overdue.matches(card("undated", (LocalDate) null), OPEN_LANE, TODAY, "x")).isFalse();
    }

    @Test
    void mineComparesAssigneeCaseInsensitively() {
        CardFilter mine = CardFilter.none().withMineOnly(true);
        assertThat(mine.matches(card("a", null, "mara", Priority.LOW), OPEN_LANE, TODAY, "Mara")).isTrue();
        assertThat(mine.matches(card("a", null, "Jonas", Priority.LOW), OPEN_LANE, TODAY, "Mara")).isFalse();
        assertThat(mine.matches(card("a", null, null, Priority.LOW), OPEN_LANE, TODAY, "Mara")).isFalse();
    }

    @Test
    void unassignedOnlyMatchesCardsWithoutAssignee() {
        CardFilter unassigned = CardFilter.none().withUnassignedOnly(true);
        assertThat(unassigned.matches(card("a", null, null, Priority.LOW), OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(unassigned.matches(card("a", null, "Mara", Priority.LOW), OPEN_LANE, TODAY, "x")).isFalse();
    }

    @Test
    void criteriaCombineAndCountToggles() {
        CardFilter filter = CardFilter.none().withText("x").withPriority(Priority.HIGH).withOverdueOnly(true).withMineOnly(true);
        assertThat(filter.toggleCount()).isEqualTo(3);
        assertThat(filter.isActive()).isTrue();
        assertThat(filter.withoutToggles()).isEqualTo(CardFilter.none().withText("x"));
        assertThat(filter.withoutToggles().toggleCount()).isZero();

        CardSnapshot match = new CardSnapshot(1, 1, "x marks", null, Priority.HIGH, TODAY.minusDays(3), "Me", Set.of(), List.of(), 0, Instant.EPOCH);
        assertThat(filter.matches(match, OPEN_LANE, TODAY, "me")).isTrue();
        assertThat(filter.matches(match, DONE_LANE, TODAY, "me")).isFalse();
    }

    private static CardSnapshot card(String title, String description, String assignee, Priority priority, String... tags) {
        return new CardSnapshot(1, 1, title, description, priority, null, assignee, Set.of(tags), List.of(), 0, Instant.EPOCH);
    }

    private static CardSnapshot card(String title, LocalDate dueDate) {
        return new CardSnapshot(1, 1, title, null, Priority.MEDIUM, dueDate, null, Set.of(), List.of(), 0, Instant.EPOCH);
    }
}
