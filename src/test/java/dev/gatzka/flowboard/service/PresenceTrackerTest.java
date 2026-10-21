package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.shared.Registration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresenceTrackerTest {

    /** Runs notifications inline so assertions can follow immediately. */
    private final PresenceTracker tracker = new PresenceTracker(Runnable::run);

    @Test
    void joiningNotifiesEveryoneOnTheBoardWithDistinctSortedParticipants() {
        List<List<Participant>> maraSaw = new ArrayList<>();
        List<List<Participant>> jonasSaw = new ArrayList<>();

        tracker.join(1, Participant.named("Mara"), maraSaw::add);
        tracker.join(1, Participant.named("jonas"), jonasSaw::add);
        tracker.join(1, Participant.named("Mara"), people -> { });

        assertThat(maraSaw.getLast()).extracting(Participant::name).containsExactly("jonas", "Mara");
        assertThat(jonasSaw.getLast()).extracting(Participant::name).containsExactly("jonas", "Mara");
        assertThat(tracker.participants(1)).hasSize(2);
    }

    @Test
    void leavingRemovesOnlyThatViewer() {
        List<List<Participant>> observerSaw = new ArrayList<>();
        tracker.join(7, Participant.named("Observer"), observerSaw::add);
        Registration firstTab = tracker.join(7, Participant.named("Priya"), people -> { });
        tracker.join(7, Participant.named("Priya"), people -> { });

        firstTab.remove();
        assertThat(tracker.participants(7)).extracting(Participant::name)
                .as("Priya still has another tab open").containsExactly("Observer", "Priya");
        assertThat(observerSaw.getLast()).extracting(Participant::name).containsExactly("Observer", "Priya");
    }

    @Test
    void boardsAreIsolatedFromEachOther() {
        tracker.join(1, Participant.named("A"), people -> { });
        tracker.join(2, Participant.named("B"), people -> { });

        assertThat(tracker.participants(1)).extracting(Participant::name).containsExactly("A");
        assertThat(tracker.participants(2)).extracting(Participant::name).containsExactly("B");
        assertThat(tracker.participants(3)).isEmpty();
    }

    @Test
    void participantColourIsStableAndWithinRange() {
        Participant first = Participant.named("Mara");
        Participant again = Participant.named("Mara");

        assertThat(first).isEqualTo(again);
        assertThat(first.colorIndex()).isBetween(0, Participant.COLOR_COUNT - 1);
    }
}
