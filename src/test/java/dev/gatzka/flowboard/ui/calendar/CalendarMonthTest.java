package dev.gatzka.flowboard.ui.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CalendarMonthTest {

    @Test
    void gridCoversWholeWeeksAroundTheMonth() {
        CalendarMonth october = CalendarMonth.of(YearMonth.of(2026, 10));

        assertThat(october.days().getFirst()).isEqualTo(LocalDate.of(2026, 9, 28)).matches(d -> d.getDayOfWeek() == DayOfWeek.MONDAY);
        assertThat(october.days().getLast()).isEqualTo(LocalDate.of(2026, 11, 1)).matches(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY);
        assertThat(october.days()).hasSize(35);
        assertThat(october.weeks()).isEqualTo(5);
        assertThat(october.contains(LocalDate.of(2026, 10, 31))).isTrue();
        assertThat(october.contains(LocalDate.of(2026, 11, 1))).isFalse();
    }

    @Test
    void monthStartingOnMondayNeedsNoLeadingDays() {
        CalendarMonth june = CalendarMonth.of(YearMonth.of(2026, 6));
        assertThat(june.days().getFirst()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(june.weeks()).isEqualTo(5);
    }

    @Test
    void pathSegmentRoundTrips() {
        CalendarMonth month = CalendarMonth.of(YearMonth.of(2027, 2));
        assertThat(month.pathSegment()).isEqualTo("2027-02");
        assertThat(month.title(java.util.Locale.ENGLISH)).isEqualTo("February 2027");
        assertThat(CalendarMonth.parse("2027-02")).contains(YearMonth.of(2027, 2));
        assertThat(CalendarMonth.parse("nope")).isEmpty();
        assertThat(CalendarMonth.parse(null)).isEmpty();
    }
}
