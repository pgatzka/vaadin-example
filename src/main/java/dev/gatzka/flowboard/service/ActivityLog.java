package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Activity;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.repository.ActivityRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what happened on a board and publishes the matching {@link BoardChangedEvent}.
 * The event is delivered to browsers only after the surrounding transaction commits, see
 * {@link BoardChangeBroadcaster}.
 */
@Service
@Transactional
public class ActivityLog {

    private final ActivityRepository activities;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ActivityLog(ActivityRepository activities, ApplicationEventPublisher events, Clock clock) {
        this.activities = activities;
        this.events = events;
        this.clock = clock;
    }

    public void record(BoardChangedEvent event) {
        if (event.kind() != BoardChangedEvent.Kind.DELETED) {
            activities.save(new Activity(event.boardId(), event.cardId(), event.actor(), event.message(), clock.instant()));
        }
        events.publishEvent(event);
    }

    /** Tells connected browsers about a change without writing an activity entry. */
    public void announce(BoardChangedEvent event) {
        events.publishEvent(event);
    }

    @Transactional(readOnly = true)
    public List<ActivityEntry> recent(long boardId) {
        return activities.findTop50ByBoardIdOrderByOccurredAtDesc(boardId).stream().map(ActivityEntry::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityEntry> recentForCard(long cardId) {
        return activities.findTop50ByCardIdOrderByOccurredAtDesc(cardId).stream().map(ActivityEntry::of).toList();
    }

    void forget(long boardId) {
        activities.deleteAllByBoardId(boardId);
    }
}
