package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.CardFilter;
import dev.gatzka.flowboard.domain.CardFilterCodec;
import dev.gatzka.flowboard.domain.SavedView;
import dev.gatzka.flowboard.domain.SavedViewSnapshot;
import dev.gatzka.flowboard.repository.SavedViewRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Named filters per board. Saving under an existing name replaces that view. */
@Service
@Transactional
public class SavedViewService {

    public static final int MAX_NAME_LENGTH = 60;

    private final SavedViewRepository views;
    private final ActivityLog activityLog;
    private final Clock clock;

    SavedViewService(SavedViewRepository views, ActivityLog activityLog, Clock clock) {
        this.views = views;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public SavedViewSnapshot save(long boardId, String name, CardFilter filter, String actor) {
        String cleaned = name == null ? "" : name.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Give the view a name of 1 to " + MAX_NAME_LENGTH + " characters");
        }
        if (!filter.isActive()) {
            throw new IllegalArgumentException("Set at least one filter before saving a view");
        }
        String encoded = CardFilterCodec.encode(filter);
        SavedView view = views.findByBoardIdAndNameIgnoreCase(boardId, cleaned)
                .map(existing -> {
                    existing.setFilter(encoded);
                    return existing;
                })
                .orElseGet(() -> views.save(new SavedView(boardId, cleaned, encoded, actor, clock.instant())));
        activityLog.record(BoardChangedEvent.updated(boardId, actor, "saved the view \"%s\"".formatted(view.getName())));
        return SavedViewSnapshot.of(view);
    }

    public void delete(long viewId, String actor) {
        views.findById(viewId).ifPresent(view -> {
            views.delete(view);
            activityLog.record(BoardChangedEvent.updated(view.getBoardId(), actor, "removed the view \"%s\"".formatted(view.getName())));
        });
    }

    @Transactional(readOnly = true)
    public List<SavedViewSnapshot> forBoard(long boardId) {
        return views.findAllByBoardIdOrderByNameAsc(boardId).stream().map(SavedViewSnapshot::of).toList();
    }

    public void forgetBoard(long boardId) {
        views.deleteAllByBoardId(boardId);
    }
}
