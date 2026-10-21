package dev.gatzka.flowboard.ui.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The grid of days shown for one month: whole weeks, Monday to Sunday, covering the month. */
record CalendarMonth(YearMonth month, List<LocalDate> days) {

    private static final DateTimeFormatter PATH = DateTimeFormatter.ofPattern("uuuu-MM");
    private static final DateTimeFormatter TITLE = DateTimeFormatter.ofPattern("MMMM uuuu");

    static CalendarMonth of(YearMonth month) {
        LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            days.add(day);
        }
        return new CalendarMonth(month, List.copyOf(days));
    }

    /** Parses the {@code uuuu-MM} form used in URLs. */
    static Optional<YearMonth> parse(String text) {
        try {
            return Optional.of(YearMonth.parse(text, PATH));
        } catch (DateTimeParseException | NullPointerException invalid) {
            return Optional.empty();
        }
    }

    String pathSegment() {
        return PATH.format(month);
    }

    String title() {
        return TITLE.format(month);
    }

    boolean contains(LocalDate day) {
        return YearMonth.from(day).equals(month);
    }

    int weeks() {
        return days.size() / 7;
    }
}
