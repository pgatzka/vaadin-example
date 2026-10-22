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

        tracker.join(1, Participant.named("Mara"), presence -> maraSaw.add(presence.participants()));
        tracker.join(1, Participant.named("jonas"), presence -> jonasSaw.add(presence.participants()));
        tracker.join(1, Participant.named("Mara"), people -> { });

        assertThat(maraSaw.getLast()).extracting(Participant::name).containsExactly("jonas", "Mara");
        assertThat(jonasSaw.getLast()).extracting(Participant::name).containsExactly("jonas", "Mara");
        assertThat(tracker.participants(1)).hasSize(2);
    }

    @Test
    void leavingRemovesOnlyThatViewer() {
        List<List<Participant>> observerSaw = new ArrayList<>();
        tracker.join(7, Participant.named("Observer"), presence -> observerSaw.add(presence.participants()));
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
    void editingACardIsSharedPerCardAndEndsWhenTheViewerLeaves() {
        List<PresenceTracker.Presence> maraSaw = new ArrayList<>();
        tracker.join(3, Participant.named("Mara"), maraSaw::add);
        PresenceTracker.Membership jonas = tracker.join(3, Participant.named("Jonas"), presence -> { });
        PresenceTracker.Membership priya = tracker.join(3, Participant.named("Priya"), presence -> { });

        jonas.editing(42L);
        priya.editing(42L);
        priya.editing(42L);
        assertThat(maraSaw.getLast().editorsOf(42)).extracting(Participant::name).containsExactly("Jonas", "Priya");
        assertThat(maraSaw.getLast().editorsOf(7)).isEmpty();
        int notifications = maraSaw.size();

        priya.editing(7L);
        assertThat(maraSaw.getLast().editors()).containsOnlyKeys(42L, 7L);
        jonas.editing(null);
        assertThat(maraSaw.getLast().editorsOf(42)).isEmpty();
        assertThat(maraSaw).as("the repeated call changed nothing and was not broadcast").hasSize(notifications + 2);

        priya.remove();
        assertThat(tracker.presence(3).editors()).isEmpty();
        assertThat(tracker.presence(3).participants()).extracting(Participant::name).containsExactly("Jonas", "Mara");
    }

    @Test
    void participantColourIsStableAndWithinRange() {
        Participant first = Participant.named("Mara");
        Participant again = Participant.named("Mara");

        assertThat(first).isEqualTo(again);
        assertThat(first.colorIndex()).isBetween(0, Participant.COLOR_COUNT - 1);
    }
}
