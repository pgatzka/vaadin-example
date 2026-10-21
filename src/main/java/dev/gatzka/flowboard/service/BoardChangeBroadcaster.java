package dev.gatzka.flowboard.service;

import com.vaadin.flow.shared.Registration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans out {@link BoardChangedEvent}s to every subscriber (typically one per open browser tab).
 * <p>
 * Events are only forwarded once the transaction that produced them has committed, so a
 * subscriber reloading in response always sees the new state.
 */
@Component
public class BoardChangeBroadcaster {

    private final Set<Consumer<BoardChangedEvent>> subscribers = ConcurrentHashMap.newKeySet();
    private final Executor executor;

    BoardChangeBroadcaster(Executor broadcastExecutor) {
        this.executor = broadcastExecutor;
    }

    /** Subscribes to all board changes. Remove the returned registration when done listening. */
    public Registration subscribe(Consumer<BoardChangedEvent> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onBoardChanged(BoardChangedEvent event) {
        for (Consumer<BoardChangedEvent> subscriber : subscribers) {
            executor.execute(() -> subscriber.accept(event));
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }
}
