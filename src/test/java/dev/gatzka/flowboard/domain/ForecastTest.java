package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForecastTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void steadyPaceGivesTheSameDateAtEveryConfidence() {
        Forecast forecast = Forecast.of(10, Collections.nCopies(30, 1), TODAY, null, 1);

        assertThat(forecast.hasForecast()).isTrue();
        assertThat(forecast.outcomes()).extracting(Forecast.Outcome::days).containsExactly(10, 10, 10);
        assertThat(forecast.outcome(85).date()).isEqualTo(TODAY.plusDays(10));
        assertThat(forecast.distribution()).hasSize(1);
        assertThat(forecast.distribution().getFirst().trials()).isEqualTo(Forecast.TRIALS);
        assertThat(forecast.chanceBy(TODAY.plusDays(9))).isZero();
        assertThat(forecast.chanceBy(TODAY.plusDays(10))).isEqualTo(1.0);
    }

    @Test
    void quietDaysStretchTheForecastAndHigherConfidenceIsNeverEarlier() {
        List<Integer> history = List.of(2, 0, 0, 1, 0, 0, 0, 3, 0, 0, 0, 0, 1, 0);
        Forecast forecast = Forecast.of(12, history, TODAY, TODAY.plusDays(30), 7);

        Forecast.Outcome p50 = forecast.outcome(50);
        Forecast.Outcome p85 = forecast.outcome(85);
        Forecast.Outcome p95 = forecast.outcome(95);
        assertThat(p50.days()).isGreaterThan(12 / 3).isLessThanOrEqualTo(p85.days());
        assertThat(p85.days()).isLessThanOrEqualTo(p95.days());
        assertThat(forecast.distribution().stream().mapToInt(Forecast.Bucket::trials).sum()).isEqualTo(Forecast.TRIALS);
        assertThat(forecast.chanceBy(p50.date())).isBetween(0.5, 0.6);
        assertThat(forecast.chanceBy(p95.date())).isGreaterThanOrEqualTo(0.95);
        assertThat(forecast.targetChance()).isPresent();
        assertThat(forecast.completions()).isEqualTo(7);
        assertThat(forecast.activeDays()).isEqualTo(4);
        assertThat(forecast.perWeek()).isEqualTo(3.5);
    }

    @Test
    void sameSeedSameForecast() {
        List<Integer> history = List.of(1, 0, 2, 0, 0, 1, 0);

        assertThat(Forecast.of(9, history, TODAY, null, 42)).isEqualTo(Forecast.of(9, history, TODAY, null, 42));
    }

    @Test
    void explainsWhyThereIsNoForecast() {
        Forecast nothingOpen = Forecast.of(0, List.of(1, 1, 1), TODAY, null, 1);
        Forecast tooLittle = Forecast.of(5, List.of(1, 0, 1), TODAY, TODAY, 1);

        assertThat(nothingOpen.hasForecast()).isFalse();
        assertThat(nothingOpen.reason()).contains(Forecast.Reason.NOTHING_OPEN);
        assertThat(tooLittle.reason()).contains(Forecast.Reason.TOO_LITTLE_HISTORY);
        assertThat(tooLittle.outcomes()).isEmpty();
        assertThat(tooLittle.targetChance()).isEmpty();
        assertThat(tooLittle.chanceBy(TODAY)).isZero();
        assertThat(Forecast.of(5, List.of(), TODAY, null, 1).reason()).contains(Forecast.Reason.TOO_LITTLE_HISTORY);
    }

    @Test
    void almostNoThroughputStillTerminates() {
        List<Integer> history = new java.util.ArrayList<>(Collections.nCopies(200, 0));
        history.set(0, 3);

        Forecast forecast = Forecast.of(50, history, TODAY, null, 3);

        assertThat(forecast.hasForecast()).isTrue();
        assertThat(forecast.outcome(95).days()).isLessThanOrEqualTo(Forecast.MAX_DAYS);
    }
}
