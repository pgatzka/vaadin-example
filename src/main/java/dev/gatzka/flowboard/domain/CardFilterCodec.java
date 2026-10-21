package dev.gatzka.flowboard.domain;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link CardFilter} into a short, URL-safe string and back, e.g.
 * {@code q=login&p=high&overdue&tag=backend}. Used for shareable board links and saved views.
 */
public final class CardFilterCodec {

    private CardFilterCodec() {
    }

    public static String encode(CardFilter filter) {
        List<String> parts = new ArrayList<>();
        if (!filter.text().isEmpty()) {
            parts.add("q=" + escape(filter.text()));
        }
        if (filter.priority() != null) {
            parts.add("p=" + filter.priority().name().toLowerCase(Locale.ROOT));
        }
        if (filter.overdueOnly()) {
            parts.add("overdue");
        }
        if (filter.mineOnly()) {
            parts.add("mine");
        }
        if (filter.unassignedOnly()) {
            parts.add("unassigned");
        }
        if (filter.tag() != null) {
            parts.add("tag=" + escape(filter.tag()));
        }
        if (filter.assignee() != null) {
            parts.add("who=" + escape(filter.assignee()));
        }
        return String.join("&", parts);
    }

    /** Parses what {@link #encode} produced; unknown parts are ignored, garbage yields no filter. */
    public static CardFilter decode(String encoded) {
        CardFilter filter = CardFilter.none();
        if (encoded == null || encoded.isBlank()) {
            return filter;
        }
        for (String part : encoded.split("&")) {
            int eq = part.indexOf('=');
            String key = eq < 0 ? part : part.substring(0, eq);
            String value = eq < 0 ? "" : unescape(part.substring(eq + 1));
            filter = switch (key) {
                case "q" -> filter.withText(value);
                case "p" -> filter.withPriority(priority(value));
                case "overdue" -> filter.withOverdueOnly(true);
                case "mine" -> filter.withMineOnly(true);
                case "unassigned" -> filter.withUnassignedOnly(true);
                case "tag" -> filter.withTag(value);
                case "who" -> filter.withAssignee(value);
                default -> filter;
            };
        }
        return filter;
    }

    private static Priority priority(String value) {
        try {
            return Priority.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String escape(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String unescape(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
