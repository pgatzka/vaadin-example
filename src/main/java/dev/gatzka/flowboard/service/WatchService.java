package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.CardWatcher;
import dev.gatzka.flowboard.domain.Notification;
import dev.gatzka.flowboard.repository.CardWatcherRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who follows which card. Watchers are told about comments, moves, edits, archiving and blockers on the card unless
 * they made the change themselves. Assignees and people who comment start watching automatically.
 */
@Service
@Transactional
public class WatchService {

    private final CardWatcherRepository watchers;
    private final Clock clock;

    WatchService(CardWatcherRepository watchers, Clock clock) {
        this.watchers = watchers;
        this.clock = clock;
    }

    /** Starts watching; a no-op for somebody already watching. Returns whether anything changed. */
    public boolean watch(long cardId, String name) {
        if (name == null || name.isBlank() || ActivityLog.SYSTEM_ACTOR.equals(name)) {
            return false;
        }
        if (watchers.findByCardIdAndWatcher(cardId, Notification.key(name)).isPresent()) {
            return false;
        }
        watchers.save(new CardWatcher(cardId, name, clock.instant()));
        return true;
    }

    public boolean unwatch(long cardId, String name) {
        var existing = watchers.findByCardIdAndWatcher(cardId, Notification.key(name));
        existing.ifPresent(watchers::delete);
        return existing.isPresent();
    }

    /** Flips the state and returns whether the person watches the card afterwards. */
    public boolean toggle(long cardId, String name) {
        if (unwatch(cardId, name)) {
            return false;
        }
        watch(cardId, name);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isWatching(long cardId, String name) {
        return name != null && !name.isBlank() && watchers.findByCardIdAndWatcher(cardId, Notification.key(name)).isPresent();
    }

    /** Lower-cased names of everybody watching the card. */
    @Transactional(readOnly = true)
    public List<String> watchers(long cardId) {
        return watchers.findAllByCardId(cardId).stream().map(CardWatcher::getWatcher).sorted().toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> watchedOn(long boardId, String name) {
        if (name == null || name.isBlank()) {
            return Set.of();
        }
        return watchers.findCardIdsWatchedOnBoard(boardId, Notification.key(name)).stream().collect(Collectors.toUnmodifiableSet());
    }

    void forgetCard(long cardId) {
        watchers.deleteAllByCardId(cardId);
    }

    void forgetBoard(long boardId) {
        watchers.deleteAllOnBoard(boardId);
    }
}
