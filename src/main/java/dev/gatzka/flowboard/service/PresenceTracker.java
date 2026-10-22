package dev.gatzka.flowboard.service;

import com.vaadin.flow.shared.Registration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Keeps track of who currently has which board open, and which card each of them is editing, and
 * tells everybody on that board whenever either changes. Purely in-memory: presence is ephemeral
 * by nature.
 */
@Component
public class PresenceTracker {

    /**
     * Who is on a board right now.
     *
     * @param participants distinct, alphabetically sorted people with the board open
     * @param editors per card, the distinct people who have its editor open
     */
    public record Presence(List<Participant> participants, Map<Long, List<Participant>> editors) {

        public static final Presence EMPTY = new Presence(List.of(), Map.of());

        public Presence {
            participants = List.copyOf(participants);
            editors = Map.copyOf(editors);
        }

        public List<Participant> editorsOf(long cardId) {
            return editors.getOrDefault(cardId, List.of());
        }
    }

    /** One viewer's seat on a board: say which card is being edited, and leave by removing it. */
    public interface Membership extends Registration {

        /** Tells the others which card this viewer is editing; {@code null} when the editor closes. */
        void editing(Long cardId);
    }

    private static final class Viewer {

        private final Participant participant;
        private final Consumer<Presence> listener;
        private volatile Long editing;

        private Viewer(Participant participant, Consumer<Presence> listener) {
            this.participant = participant;
            this.listener = listener;
        }
    }

    private final Map<Long, Map<Object, Viewer>> viewersByBoard = new ConcurrentHashMap<>();
    private final Executor executor;

    PresenceTracker(Executor broadcastExecutor) {
        this.executor = broadcastExecutor;
    }

    /**
     * Registers {@code participant} as viewing the board. The listener receives the board's presence
     * now and after every change until the membership is removed.
     */
    public Membership join(long boardId, Participant participant, Consumer<Presence> listener) {
        Object token = new Object();
        Viewer viewer = new Viewer(participant, listener);
        viewersByBoard.computeIfAbsent(boardId, id -> new ConcurrentHashMap<>()).put(token, viewer);
        broadcast(boardId);
        return new Membership() {
            @Override
            public void editing(Long cardId) {
                if (!java.util.Objects.equals(viewer.editing, cardId)) {
                    viewer.editing = cardId;
                    broadcast(boardId);
                }
            }

            @Override
            public void remove() {
                viewersByBoard.computeIfPresent(boardId, (id, viewers) -> {
                    viewers.remove(token);
                    return viewers.isEmpty() ? null : viewers;
                });
                broadcast(boardId);
            }
        };
    }

    public List<Participant> participants(long boardId) {
        return presence(boardId).participants();
    }

    public Presence presence(long boardId) {
        Map<Object, Viewer> viewers = viewersByBoard.getOrDefault(boardId, Map.of());
        List<Participant> participants = sorted(viewers.values().stream().map(viewer -> viewer.participant));
        Map<Long, List<Participant>> editors = viewers.values().stream()
                .filter(viewer -> viewer.editing != null)
                .collect(Collectors.groupingBy(viewer -> viewer.editing,
                        Collectors.collectingAndThen(Collectors.toList(), group -> sorted(group.stream().map(viewer -> viewer.participant)))));
        return new Presence(participants, editors);
    }

    private static List<Participant> sorted(java.util.stream.Stream<Participant> people) {
        return people.distinct().sorted(Comparator.comparing(Participant::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private void broadcast(long boardId) {
        Map<Object, Viewer> viewers = viewersByBoard.get(boardId);
        if (viewers == null) {
            return;
        }
        Presence presence = presence(boardId);
        for (Viewer viewer : viewers.values()) {
            executor.execute(() -> viewer.listener.accept(presence));
        }
    }
}
