package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.ActivityPulse.DayCount;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityPulseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void streakCountsBackFromTodayOrYesterday() {
        assertThat(pulse(0, 3, 2, 1).currentStreak()).isEqualTo(3);
        assertThat(pulse(1, 2, 0).currentStreak()).as("quiet today so far, so count from yesterday").isEqualTo(2);
        assertThat(pulse(4, 4, 0, 5).currentStreak()).isEqualTo(1);
        assertThat(pulse(0, 0).currentStreak()).isZero();
        assertThat(pulse(1, 1, 1).currentStreak()).isEqualTo(3);
    }

    @Test
    void totalsAndActiveDays() {
        ActivityPulse pulse = pulse(0, 3, 0, 2);
        assertThat(pulse.totalChanges()).isEqualTo(5);
        assertThat(pulse.activeDays()).isEqualTo(2);
        assertThat(pulse.busiestDayCount()).isEqualTo(3);
        assertThat(pulse.isQuiet()).isFalse();
        assertThat(pulse(0, 0, 0).isQuiet()).isTrue();
    }

    private static ActivityPulse pulse(int... counts) {
        List<DayCount> days = new java.util.ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            days.add(new DayCount(TODAY.minusDays(counts.length - 1 - i), counts[i]));
        }
        return new ActivityPulse(days, List.of());
    }
}
