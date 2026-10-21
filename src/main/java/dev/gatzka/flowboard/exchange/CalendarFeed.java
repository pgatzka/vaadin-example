package dev.gatzka.flowboard.exchange;

import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A board's due dates as an iCalendar file: one all-day event per open card with a due date, so
 * the board can be subscribed to from any calendar application.
 */
public final class CalendarFeed {

    public static final String CONTENT_TYPE = "text/calendar;charset=UTF-8";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final int LINE_LIMIT = 75;

    private CalendarFeed() {
    }

    /**
     * @param cardUrlPrefix absolute prefix for card links, e.g. {@code https://host/board/3/card/}; {@code null} for none
     */
    public static String render(BoardSnapshot board, Instant now, String cardUrlPrefix) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Flowboard//Board calendar//EN");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:" + escape(board.name()));
        for (LaneSnapshot lane : board.lanes()) {
            if (lane.done()) {
                continue;
            }
            for (CardSnapshot card : lane.cards()) {
                if (card.dueDate() == null) {
                    continue;
                }
                line(ics, "BEGIN:VEVENT");
                line(ics, "UID:card-" + card.id() + "@flowboard");
                line(ics, "DTSTAMP:" + STAMP.format(now));
                line(ics, "DTSTART;VALUE=DATE:" + DATE.format(card.dueDate()));
                line(ics, "DTEND;VALUE=DATE:" + DATE.format(card.dueDate().plusDays(1)));
                line(ics, "SUMMARY:" + escape(card.title()));
                line(ics, "DESCRIPTION:" + escape(description(board, lane, card)));
                if (!card.tags().isEmpty()) {
                    line(ics, "CATEGORIES:" + String.join(",", card.tags().stream()
                            .sorted(String.CASE_INSENSITIVE_ORDER).map(CalendarFeed::escape).toList()));
                }
                if (cardUrlPrefix != null) {
                    line(ics, "URL:" + cardUrlPrefix + card.id());
                }
                line(ics, "END:VEVENT");
            }
        }
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private static String description(BoardSnapshot board, LaneSnapshot lane, CardSnapshot card) {
        List<String> parts = new java.util.ArrayList<>();
        parts.add(board.name() + " · " + lane.name());
        parts.add("Priority: " + card.priority().label());
        if (card.assignee() != null) {
            parts.add("Assignee: " + card.assignee());
        }
        if (card.isEstimated()) {
            parts.add("Estimate: " + card.estimate() + " pt");
        }
        return String.join("\n", parts);
    }

    /** Writes one content line, folded at 75 octets as RFC 5545 asks, with CRLF line ends. */
    private static void line(StringBuilder ics, String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= LINE_LIMIT) {
            ics.append(content).append("\r\n");
            return;
        }
        int start = 0;
        boolean first = true;
        while (start < content.length()) {
            int end = start;
            int width = 0;
            int limit = first ? LINE_LIMIT : LINE_LIMIT - 1;
            while (end < content.length()) {
                int cp = content.codePointAt(end);
                int size = new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (width + size > limit) {
                    break;
                }
                width += size;
                end += Character.charCount(cp);
            }
            ics.append(first ? "" : " ").append(content, start, end).append("\r\n");
            first = false;
            start = end;
        }
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n");
    }
}
