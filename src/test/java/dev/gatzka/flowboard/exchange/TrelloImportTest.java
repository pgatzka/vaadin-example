package dev.gatzka.flowboard.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.exchange.BoardExport.CardExport;
import dev.gatzka.flowboard.exchange.BoardExport.ChecklistItemExport;
import dev.gatzka.flowboard.exchange.BoardExport.CommentExport;
import dev.gatzka.flowboard.exchange.BoardExport.LaneExport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TrelloImportTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void convertsListsLabelsMembersChecklistsAndComments() {
        JsonNode trello = sample();
        assertThat(TrelloImport.looksLikeTrello(trello)).isTrue();

        BoardExport export = TrelloImport.convert(trello);

        assertThat(export.name()).isEqualTo("Website relaunch");
        assertThat(export.description()).isEqualTo("Everything for the new site.");
        assertThat(export.lanes()).extracting(LaneExport::name, LaneExport::done)
                .as("open lists in position order; a list called Done becomes the done lane")
                .containsExactly(tuple("To Do", false), tuple("Doing", false), tuple("Done ✓", true));
        LaneExport todo = export.lanes().getFirst();
        assertThat(todo.cards()).extracting(CardExport::title).as("archived cards are skipped, order follows pos")
                .containsExactly("Fix the header", "Write copy");
        CardExport header = todo.cards().getFirst();
        assertThat(header.description()).isEqualTo("It wraps on tablets.");
        assertThat(header.priority()).as("a label named High sets the priority instead of becoming a tag").isEqualTo(Priority.HIGH);
        assertThat(header.tags()).containsExactly("bug");
        assertThat(header.assignee()).isEqualTo("Mara K.");
        assertThat(header.dueDate()).isNull();
        assertThat(header.checklist()).extracting(ChecklistItemExport::text, ChecklistItemExport::done)
                .containsExactly(tuple("Reproduce", true), tuple("Test on iPad", false));
        assertThat(header.comments()).extracting(CommentExport::author, CommentExport::text, CommentExport::createdAt)
                .containsExactly(tuple("Jonas", "First comment", Instant.parse("2026-10-01T09:00:00Z")),
                        tuple("Mara K.", "Second comment", Instant.parse("2026-10-02T09:00:00Z")));
        CardExport copy = todo.cards().get(1);
        assertThat(copy.description()).isNull();
        assertThat(copy.dueDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(copy.assignee()).as("falls back to the username when the member has no full name").isEqualTo("jonas");
        assertThat(copy.tags()).as("a label without a name is known by its colour").containsExactly("green");
        assertThat(copy.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(export.lanes().get(2).cards()).singleElement().satisfies(launched -> {
            assertThat(launched.title()).isEqualTo("Launched");
            assertThat(launched.tags()).as("inline labels are used when idLabels is absent").containsExactly("launch");
        });
        assertThat(export.cardCount()).isEqualTo(3);
    }

    @Test
    void rejectsExportsWithoutNameOrOpenLists() {
        assertThatThrownBy(() -> TrelloImport.convert(JSON.readTree("{\"lists\": [], \"cards\": []}")))
                .isInstanceOf(InvalidBoardFileException.class).hasMessageContaining("no board name");
        assertThatThrownBy(() -> TrelloImport.convert(JSON.readTree("{\"name\": \"x\", \"lists\": [{\"id\": \"l\", \"closed\": true}], \"cards\": []}")))
                .isInstanceOf(InvalidBoardFileException.class).hasMessageContaining("no open lists");
        assertThat(TrelloImport.looksLikeTrello(JSON.readTree("{\"name\": \"x\", \"lanes\": []}"))).isFalse();
        assertThat(TrelloImport.looksLikeTrello(JSON.readTree("[]"))).isFalse();
    }

    static JsonNode sample() {
        try (var in = TrelloImportTest.class.getResourceAsStream("/trello-board.json")) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
