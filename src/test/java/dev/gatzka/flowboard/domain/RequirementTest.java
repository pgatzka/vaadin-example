package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequirementTest {

    @Test
    void parsesEverydaySpellings() {
        assertThat(Requirement.parse("Due Date")).isEqualTo(Requirement.DUE_DATE);
        assertThat(Requirement.parse("deadline")).isEqualTo(Requirement.DUE_DATE);
        assertThat(Requirement.parse("points")).isEqualTo(Requirement.ESTIMATE);
        assertThat(Requirement.parse("OWNER")).isEqualTo(Requirement.ASSIGNEE);
        assertThat(Requirement.parse("checklist")).isEqualTo(Requirement.CHECKLIST);
        assertThat(Requirement.parse("DESCRIPTION")).isEqualTo(Requirement.DESCRIPTION);
        assertThatThrownBy(() -> Requirement.parse("mood")).isInstanceOf(IllegalArgumentException.class);
        assertThat(RuleAction.REQUIRE.normalise(" due ")).isEqualTo("DUE_DATE");
        assertThat(RuleAction.REQUIRE.describe("ESTIMATE")).isEqualTo("Requires an estimate");
        assertThat(RuleAction.REQUIRE.runsOnEnter()).isFalse();
        assertThat(RuleAction.REQUIRE.isPolicy()).isTrue();
        assertThat(RuleAction.ASSIGN.isPolicy()).isFalse();
    }

    @Test
    void checksWhatACardHas() {
        CardSnapshot bare = card(null, null, null, null, List.of());
        CardSnapshot ready = card("Mara", 3, LocalDate.of(2030, 1, 1), "Some text",
                List.of(new ChecklistItemSnapshot(1, "done", true, 0)));
        CardSnapshot halfway = card("", null, null, " ", List.of(new ChecklistItemSnapshot(1, "open", false, 0)));

        for (Requirement requirement : Requirement.values()) {
            assertThat(requirement.isMetBy(bare)).as(requirement.name()).isFalse();
            assertThat(requirement.isMetBy(ready)).as(requirement.name()).isTrue();
            assertThat(requirement.isMetBy(halfway)).as(requirement.name()).isFalse();
        }
    }

    private static CardSnapshot card(String assignee, Integer estimate, LocalDate due, String description, List<ChecklistItemSnapshot> checklist) {
        return new CardSnapshot(1, 1, "t", description, Priority.MEDIUM, null, due, assignee, Set.of(), estimate, null, 0, Set.of(),
                List.of(), List.of(), checklist, List.of(), 0, Instant.EPOCH, null, Instant.EPOCH, null, null);
    }
}
