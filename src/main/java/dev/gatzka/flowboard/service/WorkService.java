package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.CardLocation;
import dev.gatzka.flowboard.repository.CardRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Questions that cut across boards: what is assigned to somebody, and where is something. */
@Service
@Transactional(readOnly = true)
public class WorkService {

    public static final int SEARCH_LIMIT = 50;

    private static final Comparator<CardLocation> BY_BOARD_THEN_POSITION = Comparator
            .comparing(CardLocation::boardName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(location -> location.card().laneId())
            .thenComparingInt(location -> location.card().position());

    private final CardRepository cards;

    WorkService(CardRepository cards) {
        this.cards = cards;
    }

    /** Live cards assigned to the given name on any board, including those in done lanes. */
    public List<CardLocation> assignedTo(String name) {
        return cards.findAllByAssigneeIgnoreCaseAndArchivedAtIsNull(name).stream()
                .map(CardLocation::of)
                .sorted(BY_BOARD_THEN_POSITION)
                .toList();
    }

    /**
     * Free text search over all live cards. The data set of a Flowboard installation is small
     * enough to filter in memory; the result is capped at {@value #SEARCH_LIMIT} hits.
     */
    public List<CardLocation> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return cards.findAllByArchivedAtIsNull().stream()
                .map(CardLocation::of)
                .filter(location -> location.card().matches(query))
                .sorted(BY_BOARD_THEN_POSITION)
                .limit(SEARCH_LIMIT)
                .toList();
    }
}
