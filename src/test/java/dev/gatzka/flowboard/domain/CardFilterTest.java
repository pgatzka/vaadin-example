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

        CardSnapshot match = new CardSnapshot(1, 1, "x marks", null, Priority.HIGH, TODAY.minusDays(3), "Me", Set.of(), null, Recurrence.NONE, Set.of(), List.of(), List.of(), List.of(), List.of(), 0, Instant.EPOCH, null, Instant.EPOCH);
        assertThat(filter.matches(match, OPEN_LANE, TODAY, "me")).isTrue();
        assertThat(filter.matches(match, DONE_LANE, TODAY, "me")).isFalse();
    }

    @Test
    void tagAndAssigneeNarrowFurther() {
        CardSnapshot backend = card("Index", null, "Mara", Priority.LOW, "backend", "perf");
        CardSnapshot frontend = card("Button", null, "Jonas", Priority.LOW, "ui");
        CardFilter byTag = CardFilter.none().withTag("Backend");
        assertThat(byTag.matches(backend, OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(byTag.matches(frontend, OPEN_LANE, TODAY, "x")).isFalse();
        CardFilter byPerson = CardFilter.none().withAssignee("jonas");
        assertThat(byPerson.matches(frontend, OPEN_LANE, TODAY, "x")).isTrue();
        assertThat(byPerson.matches(backend, OPEN_LANE, TODAY, "x")).isFalse();
        assertThat(byPerson.matches(card("Nobody", null, null, Priority.LOW), OPEN_LANE, TODAY, "x")).isFalse();
        assertThat(byTag.withAssignee("Mara").toggleCount()).isEqualTo(2);
        assertThat(CardFilter.none().withTag("  ").withAssignee("")).isEqualTo(CardFilter.none());
    }

    @Test
    void codecRoundTripsAndToleratesGarbage() {
        CardFilter filter = CardFilter.none().withText("login & sync").withPriority(Priority.URGENT).withOverdueOnly(true)
                .withMineOnly(true).withUnassignedOnly(true).withTag("back end").withAssignee("Lena Kovač");
        String encoded = CardFilterCodec.encode(filter);
        assertThat(encoded).isEqualTo("q=login+%26+sync&p=urgent&overdue&mine&unassigned&tag=back+end&who=Lena+Kova%C4%8D");
        assertThat(CardFilterCodec.decode(encoded)).isEqualTo(filter);
        assertThat(CardFilterCodec.encode(CardFilter.none())).isEmpty();
        assertThat(CardFilterCodec.decode(null)).isEqualTo(CardFilter.none());
        assertThat(CardFilterCodec.decode("p=nope&bogus=1&who=%E0%A4%A")).isEqualTo(CardFilter.none());
        assertThat(CardFilterCodec.decode("tag=ops")).isEqualTo(CardFilter.none().withTag("ops"));
    }

    private static CardSnapshot card(String title, String description, String assignee, Priority priority, String... tags) {
        return new CardSnapshot(1, 1, title, description, priority, null, assignee, Set.of(tags), null, Recurrence.NONE, Set.of(), List.of(), List.of(), List.of(), List.of(), 0, Instant.EPOCH, null, Instant.EPOCH);
    }

    private static CardSnapshot card(String title, LocalDate dueDate) {
        return new CardSnapshot(1, 1, title, null, Priority.MEDIUM, dueDate, null, Set.of(), null, Recurrence.NONE, Set.of(), List.of(), List.of(), List.of(), List.of(), 0, Instant.EPOCH, null, Instant.EPOCH);
    }
}
