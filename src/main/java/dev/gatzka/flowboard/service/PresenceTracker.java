package dev.gatzka.flowboard.service;

import com.vaadin.flow.shared.Registration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Keeps track of who currently has which board open and tells everybody on that board whenever
 * the set of participants changes. Purely in-memory: presence is ephemeral by nature.
 */
@Component
public class PresenceTracker {

    private record Viewer(Participant participant, Consumer<List<Participant>> listener) {
    }

    private final Map<Long, Map<Object, Viewer>> viewersByBoard = new ConcurrentHashMap<>();
    private final Executor executor;

    PresenceTracker(Executor broadcastExecutor) {
        this.executor = broadcastExecutor;
    }

    /**
     * Registers {@code participant} as viewing the board. The listener receives the distinct,
     * alphabetically sorted participants of the board now and after every change until the
     * returned registration is removed.
     */
    public Registration join(long boardId, Participant participant, Consumer<List<Participant>> listener) {
        Object token = new Object();
        viewersByBoard.computeIfAbsent(boardId, id -> new ConcurrentHashMap<>()).put(token, new Viewer(participant, listener));
        broadcast(boardId);
        return () -> {
            viewersByBoard.computeIfPresent(boardId, (id, viewers) -> {
                viewers.remove(token);
                return viewers.isEmpty() ? null : viewers;
            });
            broadcast(boardId);
        };
    }

    public List<Participant> participants(long boardId) {
        Map<Object, Viewer> viewers = viewersByBoard.getOrDefault(boardId, Map.of());
        return viewers.values().stream()
                .map(Viewer::participant)
                .distinct()
                .sorted(Comparator.comparing(Participant::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void broadcast(long boardId) {
        Map<Object, Viewer> viewers = viewersByBoard.get(boardId);
        if (viewers == null) {
            return;
        }
        List<Participant> participants = participants(boardId);
        for (Viewer viewer : viewers.values()) {
            executor.execute(() -> viewer.listener().accept(participants));
        }
    }
}
