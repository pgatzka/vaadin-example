package dev.gatzka.flowboard.service;

import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.Notification;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tells a person's open browsers that their inbox changed. */
@Component
public class InboxBroadcaster {

    private final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();
    private final Executor executor;

    InboxBroadcaster(Executor broadcastExecutor) {
        this.executor = broadcastExecutor;
    }

    public Registration subscribe(String displayName, Runnable listener) {
        String key = Notification.key(displayName);
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> listeners.computeIfPresent(key, (k, list) -> {
            list.remove(listener);
            return list.isEmpty() ? null : list;
        });
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onInboxChanged(InboxEvent event) {
        listeners.getOrDefault(event.recipientKey(), List.of()).forEach(listener -> executor.execute(listener));
    }
}
