package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroupingTest {

    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    @Test
    void rowsFollowTheBoardAndKeepCatchAllsLast() {
        BoardSnapshot board = board(
                card(1, "Mara", Priority.LOW, Set.of("ui", "docs")),
                card(2, "jonas", Priority.URGENT, Set.of()),
                card(3, null, Priority.MEDIUM, Set.of("Backend")),
                card(4, "Jonas", Priority.HIGH, Set.of("api")));

        assertThat(Grouping.ASSIGNEE.rows(board)).containsExactly("jonas", "Mara", "Unassigned");
        assertThat(Grouping.TAG.rows(board)).containsExactly("api", "Backend", "docs", "No tag");
        assertThat(Grouping.PRIORITY.rows(board)).containsExactly("Urgent", "High", "Medium", "Low");
        assertThat(Grouping.NONE.rows(board)).isEmpty();
        assertThat(Grouping.ASSIGNEE.rows(board(card(9, " ", Priority.LOW, Set.of())))).containsExactly("Unassigned");
    }

    @Test
    void keysPlaceEachCardInOneRow() {
        CardSnapshot tagged = card(1, "Mara", Priority.LOW, Set.of("ui", "docs"));
        assertThat(Grouping.ASSIGNEE.keyOf(tagged)).isEqualTo("Mara");
        assertThat(Grouping.TAG.keyOf(tagged)).as("first tag alphabetically").isEqualTo("docs");
        assertThat(Grouping.PRIORITY.keyOf(tagged)).isEqualTo("Low");
        assertThat(Grouping.ASSIGNEE.keyOf(card(2, null, Priority.LOW, Set.of()))).isEqualTo("Unassigned");
        assertThat(Grouping.TAG.keyOf(card(2, null, Priority.LOW, Set.of()))).isEqualTo("No tag");
    }

    @Test
    void movingIntoARowChangesJustThatAttribute() {
        CardDraft draft = card(1, "Mara", Priority.LOW, Set.of("ui")).toDraft();

        assertThat(Grouping.ASSIGNEE.moveInto(draft, "Jonas").assignee()).isEqualTo("Jonas");
        assertThat(Grouping.ASSIGNEE.moveInto(draft, "Unassigned").assignee()).isNull();
        assertThat(Grouping.PRIORITY.moveInto(draft, "Urgent").priority()).isEqualTo(Priority.URGENT);
        assertThat(Grouping.TAG.moveInto(draft, "api").tags()).containsExactlyInAnyOrder("ui", "api");
        assertThat(Grouping.TAG.moveInto(draft, "No tag").tags()).isEmpty();
        assertThat(Grouping.NONE.moveInto(draft, "anything")).isSameAs(draft);
        assertThat(Grouping.ASSIGNEE.moveInto(draft, "Jonas")).extracting(CardDraft::title, CardDraft::priority).containsExactly("Card 1", Priority.LOW);
    }

    private static BoardSnapshot board(CardSnapshot... cards) {
        return new BoardSnapshot(1, "Board", "", List.of(new LaneSnapshot(10, "Todo", 0, null, false, List.of(cards))));
    }

    private static CardSnapshot card(long id, String assignee, Priority priority, Set<String> tags) {
        return new CardSnapshot(id, 10, "Card " + id, "", priority, null, null, assignee, tags, null, null, 0, Set.of(),
                List.of(), List.of(), List.of(), List.of(), 0, NOW, null, NOW);
    }
}
