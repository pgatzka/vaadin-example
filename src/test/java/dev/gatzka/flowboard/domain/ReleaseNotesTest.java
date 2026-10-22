package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReleaseNotesTest {

    private static final LocalDate SINCE = LocalDate.of(2026, 10, 9);
    private static final LocalDate UNTIL = LocalDate.of(2026, 10, 22);

    @Test
    void groupsByMilestoneThenTagThenOther() {
        List<CardSnapshot> completed = List.of(
                card(1, "Login page", "Mara", Set.of("ui", "auth"), 3, 7L),
                card(2, "Health endpoint", null, Set.of("ops"), null, null),
                card(3, "Logo", "Priya", Set.of(), 1, null),
                card(4, "Docker image", "Jonas", Set.of("Ops"), 2, null),
                card(5, "Password hashing", "Mara", Set.of("auth"), 2, 7L),
                card(6, "Orphan", null, Set.of("x"), null, 99L));

        ReleaseNotes notes = ReleaseNotes.of("Launch", SINCE, UNTIL, completed, Map.of(7L, "Foundation"));

        assertThat(notes.sections()).extracting(ReleaseNotes.Section::title).containsExactly("Foundation", "ops", "x", "Other");
        assertThat(notes.sections().get(0).milestone()).isTrue();
        assertThat(notes.sections().get(0).cards()).extracting(CardSnapshot::title).containsExactly("Login page", "Password hashing");
        assertThat(notes.sections().get(1).cards()).extracting(CardSnapshot::title).as("tags match case-insensitively").containsExactly("Health endpoint", "Docker image");
        assertThat(notes.sections().get(2).cards()).extracting(CardSnapshot::title).as("an unknown milestone falls back to the tag").containsExactly("Orphan");
        assertThat(notes.sections().get(3).cards()).extracting(CardSnapshot::title).containsExactly("Logo");
        assertThat(notes.cardCount()).isEqualTo(6);
        assertThat(notes.points()).isEqualTo(8);
        assertThat(notes.contributors()).containsExactly("Jonas", "Mara", "Priya");

        String markdown = notes.toMarkdown();
        assertThat(markdown).startsWith("# Launch \u00b7 Release notes \u00b7 Oct 9, 2026 \u2013 Oct 22, 2026\n\n_6 cards completed, 8 points, by Jonas, Mara, Priya._\n\n## \uD83C\uDFC1 Foundation (2)\n\n- Login page \u2014 Mara\n- Password hashing \u2014 Mara\n\n## ops (2)\n\n- Health endpoint\n");
        assertThat(markdown).endsWith("## Other (1)\n\n- Logo \u2014 Priya\n");
    }

    @Test
    void emptyPeriodSaysSo() {
        ReleaseNotes notes = ReleaseNotes.of("Quiet", SINCE, UNTIL, List.of(), Map.of());

        assertThat(notes.isEmpty()).isTrue();
        assertThat(notes.toMarkdown()).isEqualTo("# Quiet \u00b7 Release notes \u00b7 Oct 9, 2026 \u2013 Oct 22, 2026\n\n_Nothing was completed in this period._\n");
        assertThat(ReleaseNotes.defaultSince(UNTIL)).isEqualTo(SINCE);
    }

    private static CardSnapshot card(long id, String title, String assignee, Set<String> tags, Integer estimate, Long milestoneId) {
        return new CardSnapshot(id, 1, title, null, Priority.MEDIUM, null, null, assignee, tags, estimate, null, 0, Set.of(),
                List.of(), List.of(), List.of(), List.of(), 0, Instant.EPOCH, null, Instant.EPOCH, null, milestoneId);
    }
}
