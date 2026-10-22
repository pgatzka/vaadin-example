package dev.gatzka.flowboard.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The shorthand understood by a lane's quick-add field, so a card can be filled in without the
 * editor: {@code Fix login !high @mara #backend ~3 due:fri every:week}.
 *
 * <ul>
 * <li>{@code !low}, {@code !medium}, {@code !high}, {@code !urgent}: priority</li>
 * <li>{@code @name}: assignee (first one wins; use quotes in the editor for names with spaces)</li>
 * <li>{@code #tag}: tag, repeatable</li>
 * <li>{@code ~3}: estimate in points</li>
 * <li>{@code due:today}, {@code due:tomorrow}, {@code due:fri}, {@code due:+3} (days), {@code due:2026-10-31}</li>
 * <li>{@code every:day}, {@code every:week}, {@code every:2weeks}, {@code every:month}: recurrence</li>
 * </ul>
 * Tokens anywhere in the text are consumed; the rest is the title. An unrecognised value is left in
 * the title untouched so nothing typed is lost silently.
 */
public record QuickAdd(CardDraft draft, Recurrence recurrence) {

    private static final Map<String, Recurrence> PERIODS = Map.of(
            "day", Recurrence.DAILY, "daily", Recurrence.DAILY,
            "week", Recurrence.WEEKLY, "weekly", Recurrence.WEEKLY,
            "2weeks", Recurrence.EVERY_TWO_WEEKS, "fortnight", Recurrence.EVERY_TWO_WEEKS, "biweekly", Recurrence.EVERY_TWO_WEEKS,
            "month", Recurrence.MONTHLY, "monthly", Recurrence.MONTHLY);

    /** Whether the text uses any of the shorthand at all. */
    public static boolean usesShorthand(String text) {
        return text != null && text.matches("(?s).*(^|\\s)([!@#~][^\\s]|due:|start:|every:).*");
    }

    public static QuickAdd parse(String text, LocalDate today) {
        List<String> titleWords = new ArrayList<>();
        Priority priority = null;
        String assignee = null;
        Set<String> tags = new LinkedHashSet<>();
        Integer estimate = null;
        LocalDate due = null;
        LocalDate start = null;
        Recurrence recurrence = Recurrence.NONE;

        for (String word : (text == null ? "" : text).strip().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String lower = word.toLowerCase(Locale.ROOT);
            if (word.length() > 1 && word.charAt(0) == '!' && priority(word.substring(1)) != null) {
                priority = priority(word.substring(1));
            } else if (word.length() > 1 && word.charAt(0) == '@' && assignee == null) {
                assignee = word.substring(1);
            } else if (word.length() > 1 && word.charAt(0) == '#') {
                tags.add(word.substring(1));
            } else if (word.length() > 1 && word.charAt(0) == '~' && points(word.substring(1)) != null) {
                estimate = points(word.substring(1));
            } else if (lower.startsWith("due:") && date(lower.substring(4), today) != null) {
                due = date(lower.substring(4), today);
            } else if (lower.startsWith("start:") && date(lower.substring(6), today) != null) {
                start = date(lower.substring(6), today);
            } else if (lower.startsWith("every:") && PERIODS.containsKey(lower.substring(6))) {
                recurrence = PERIODS.get(lower.substring(6));
            } else {
                titleWords.add(word);
            }
        }
        String title = String.join(" ", titleWords);
        if (title.isBlank()) {
            title = text == null ? "" : text.strip();
        }
        if (start != null && due != null && start.isAfter(due)) {
            start = null;
        }
        return new QuickAdd(new CardDraft(title, null, priority, start, due, assignee, tags, estimate), recurrence);
    }

    private static Priority priority(String value) {
        try {
            return Priority.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer points(String value) {
        try {
            int points = Integer.parseInt(value);
            return points >= 0 && points <= CardDraft.MAX_ESTIMATE ? points : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDate date(String value, LocalDate today) {
        switch (value) {
            case "today" -> {
                return today;
            }
            case "tomorrow" -> {
                return today.plusDays(1);
            }
            default -> {
                // fall through to the other forms
            }
        }
        if (value.matches("\\+\\d{1,3}")) {
            return today.plusDays(Integer.parseInt(value.substring(1)));
        }
        DayOfWeek weekday = weekday(value);
        if (weekday != null) {
            int ahead = (weekday.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            return today.plusDays(ahead == 0 ? 7 : ahead);
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static DayOfWeek weekday(String value) {
        for (DayOfWeek day : DayOfWeek.values()) {
            String name = day.name().toLowerCase(Locale.ROOT);
            if (value.equals(name) || (value.length() >= 3 && name.startsWith(value))) {
                return day;
            }
        }
        return null;
    }
}
