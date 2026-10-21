package dev.gatzka.flowboard.ui.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void picksTheMostReadableUnit() {
        assertThat(Durations.humanize(Duration.ofMinutes(5))).isEqualTo("5 min");
        assertThat(Durations.humanize(Duration.ofHours(3))).isEqualTo("3 h");
        assertThat(Durations.humanize(Duration.ofHours(47))).isEqualTo("47 h");
        assertThat(Durations.humanize(Duration.ofHours(60))).isEqualTo("2.5 d");
        assertThat(Durations.humanize(Duration.ofDays(12))).isEqualTo("12 d");
    }
}
