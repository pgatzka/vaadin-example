package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurrenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void advancesFromThePreviousDueDate() {
        assertThat(Recurrence.DAILY.next(TODAY, TODAY)).isEqualTo(TODAY.plusDays(1));
        assertThat(Recurrence.WEEKLY.next(TODAY.minusDays(2), TODAY)).as("keeps the weekday").isEqualTo(TODAY.plusDays(5));
        assertThat(Recurrence.EVERY_TWO_WEEKS.next(TODAY, TODAY)).isEqualTo(TODAY.plusWeeks(2));
        assertThat(Recurrence.MONTHLY.next(LocalDate.of(2026, 1, 31), TODAY)).as("skips forward past today")
                .isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void nothingToComputeWithoutADueDateOrPeriod() {
        assertThat(Recurrence.WEEKLY.next(null, TODAY)).isNull();
        assertThat(Recurrence.NONE.next(TODAY, TODAY)).isNull();
        assertThat(Recurrence.NONE.repeats()).isFalse();
        assertThat(Recurrence.DAILY.repeats()).isTrue();
    }
}
