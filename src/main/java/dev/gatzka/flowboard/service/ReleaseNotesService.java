package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.MilestoneSnapshot;
import dev.gatzka.flowboard.domain.ReleaseNotes;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds release notes from the cards completed in a period, archived ones included. */
@Service
@Transactional(readOnly = true)
public class ReleaseNotesService {

    private final BoardRepository boards;
    private final CardRepository cards;
    private final MilestoneService milestones;
    private final Clock clock;

    ReleaseNotesService(BoardRepository boards, CardRepository cards, MilestoneService milestones, Clock clock) {
        this.boards = boards;
        this.cards = cards;
        this.milestones = milestones;
        this.clock = clock;
    }

    /** Notes for the cards completed from {@code since} to {@code until}, both inclusive; {@code null} dates fall back to the last two weeks. */
    public ReleaseNotes between(long boardId, LocalDate since, LocalDate until) {
        Board board = boards.findById(boardId).orElseThrow(() -> new NotFoundException("Board", boardId));
        LocalDate today = LocalDate.now(clock);
        LocalDate to = until == null ? today : until;
        LocalDate from = since == null ? ReleaseNotes.defaultSince(to) : since;
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The period ends before it starts");
        }
        ZoneId zone = clock.getZone();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<CardSnapshot> completed = cards.findAllByLaneBoardIdAndCompletedAtGreaterThanEqual(boardId, start).stream()
                .filter(card -> card.getCompletedAt().isBefore(end))
                .sorted(Comparator.comparing(Card::getCompletedAt))
                .map(CardSnapshot::of)
                .toList();
        Map<Long, String> milestoneNames = milestones.forBoard(boardId).stream()
                .collect(Collectors.toMap(MilestoneSnapshot::id, MilestoneSnapshot::name));
        return ReleaseNotes.of(board.getName(), from, to, completed, milestoneNames);
    }
}
