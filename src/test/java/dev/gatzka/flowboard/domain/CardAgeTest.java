package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CardAgeTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void bucketsByTimeInLane() {
        assertThat(CardAge.of(NOW.minus(Duration.ofHours(30)), NOW).level()).isEqualTo(CardAge.Level.FRESH);
        assertThat(CardAge.of(NOW.minus(Duration.ofDays(2)), NOW).level()).isEqualTo(CardAge.Level.AGING);
        assertThat(CardAge.of(NOW.minus(Duration.ofDays(6)), NOW).level()).isEqualTo(CardAge.Level.AGING);
        CardAge stale = CardAge.of(NOW.minus(Duration.ofDays(9)), NOW);
        assertThat(stale.level()).isEqualTo(CardAge.Level.STALE);
        assertThat(stale.days()).isEqualTo(9);
        assertThat(stale.isNoteworthy()).isTrue();
        assertThat(CardAge.of(NOW, NOW).isNoteworthy()).isFalse();
    }

    @Test
    void clockSkewNeverYieldsNegativeAge() {
        assertThat(CardAge.of(NOW.plus(Duration.ofMinutes(5)), NOW).inLane()).isEqualTo(Duration.ZERO);
    }
}
