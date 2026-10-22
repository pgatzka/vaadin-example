package dev.gatzka.flowboard.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.Recurrence;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CalendarFeedTest {

    @Test
    void rendersOneAllDayEventPerOpenDueCard() {
        CardSnapshot due = card(1, "Ship it; now, really", LocalDate.of(2026, 10, 31), "Mara", Set.of("ops", "release"), 5);
        CardSnapshot undated = card(2, "Whenever", null, null, Set.of(), null);
        CardSnapshot finished = card(3, "Done already", LocalDate.of(2026, 10, 1), null, Set.of(), null);
        BoardSnapshot board = new BoardSnapshot(7, "Launch, phase 1", null, List.of(
                new LaneSnapshot(1, "Backlog", 0, null, false, List.of(due, undated)),
                new LaneSnapshot(2, "Done", 1, null, true, List.of(finished))));

        String ics = CalendarFeed.render(board, Instant.parse("2026-10-21T10:15:30Z"), "https://flow.example/board/7/card/");

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("X-WR-CALNAME:Launch\\, phase 1\r\n");
        assertThat(ics.split("BEGIN:VEVENT")).hasSize(2);
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("UID:card-1@flowboard\r\n")
                .contains("DTSTAMP:20261021T101530Z\r\n")
                .contains("DTSTART;VALUE=DATE:20261031\r\n")
                .contains("DTEND;VALUE=DATE:20261101\r\n")
                .contains("SUMMARY:Ship it\\; now\\, really\r\n")
                .contains("DESCRIPTION:Launch\\, phase 1 · Backlog\\nPriority: High\\nAssignee: Mara\\nEstimate: 5 pt\r\n")
                .contains("CATEGORIES:ops,release\r\n")
                .contains("URL:https://flow.example/board/7/card/1\r\n");
        assertThat(ics).doesNotContain("Whenever").doesNotContain("Done already");
    }

    @Test
    void foldsLongLines() {
        String longTitle = "A".repeat(200);
        BoardSnapshot board = new BoardSnapshot(1, "B", null, List.of(
                new LaneSnapshot(1, "L", 0, null, false, List.of(card(1, longTitle, LocalDate.of(2026, 1, 1), null, Set.of(), null)))));
        String ics = CalendarFeed.render(board, Instant.EPOCH, null);
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:" + longTitle + "\r\n");
        assertThat(ics).doesNotContain("URL:");
    }

    private static CardSnapshot card(long id, String title, LocalDate due, String assignee, Set<String> tags, Integer estimate) {
        return new CardSnapshot(id, 1, title, null, Priority.HIGH, null, due, assignee, tags, estimate, Recurrence.NONE, 0, Set.of(),
                List.of(), List.of(), List.of(), List.of(), 0, Instant.EPOCH, null, Instant.EPOCH);
    }
}
