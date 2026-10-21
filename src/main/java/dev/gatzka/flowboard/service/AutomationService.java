package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.Lane;
import dev.gatzka.flowboard.domain.LaneRule;
import dev.gatzka.flowboard.domain.LaneRuleSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.RuleAction;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.LaneRepository;
import dev.gatzka.flowboard.repository.LaneRuleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lane automations: small rules that run when a card enters a lane (assign it, tag it, set its
 * priority or due date, tell somebody) and one that tidies up nightly (archive cards that sat in
 * the lane too long). Rules are kept deliberately simple and declarative.
 */
@Service
@Transactional
public class AutomationService {

    public static final int MAX_RULES_PER_LANE = 10;
    private static final Logger LOG = LoggerFactory.getLogger(AutomationService.class);

    private final LaneRuleRepository rules;
    private final LaneRepository lanes;
    private final CardRepository cards;
    private final NotificationService notifications;
    private final ActivityLog activityLog;
    private final Clock clock;

    AutomationService(LaneRuleRepository rules, LaneRepository lanes, CardRepository cards, NotificationService notifications,
            ActivityLog activityLog, Clock clock) {
        this.rules = rules;
        this.lanes = lanes;
        this.cards = cards;
        this.notifications = notifications;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public LaneRuleSnapshot addRule(long laneId, RuleAction action, String argument, String actor) {
        Lane lane = lanes.findById(laneId).orElseThrow(() -> new NotFoundException("Lane", laneId));
        if (rules.countByLaneId(laneId) >= MAX_RULES_PER_LANE) {
            throw new IllegalArgumentException("A lane can have at most " + MAX_RULES_PER_LANE + " rules");
        }
        LaneRule rule = rules.save(new LaneRule(laneId, action, action.normalise(argument), clock.instant()));
        activityLog.record(BoardChangedEvent.updated(lane.getBoard().getId(), actor,
                "added a rule to %s: %s".formatted(lane.getName(), rule.getAction().describe(rule.getArgument()))));
        return LaneRuleSnapshot.of(rule);
    }

    public void removeRule(long ruleId, String actor) {
        rules.findById(ruleId).ifPresent(rule -> {
            rules.delete(rule);
            lanes.findById(rule.getLaneId()).ifPresent(lane -> activityLog.record(BoardChangedEvent.updated(lane.getBoard().getId(), actor,
                    "removed a rule from %s: %s".formatted(lane.getName(), rule.getAction().describe(rule.getArgument())))));
        });
    }

    @Transactional(readOnly = true)
    public List<LaneRuleSnapshot> rulesFor(long laneId) {
        return rules.findAllByLaneIdOrderByCreatedAtAsc(laneId).stream().map(LaneRuleSnapshot::of).toList();
    }

    /**
     * Applies the lane's on-enter rules to a card that just arrived. Called by the board service
     * inside the same transaction as the move, so the card shows up already adjusted.
     *
     * @return what was done, one short phrase per rule, for the activity feed
     */
    public List<String> applyOnEnter(Card card, Lane lane, String actor) {
        List<LaneRule> laneRules = rules.findAllByLaneIdOrderByCreatedAtAsc(lane.getId());
        if (laneRules.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        List<String> done = new ArrayList<>();
        for (LaneRule rule : laneRules) {
            if (!rule.getAction().runsOnEnter()) {
                continue;
            }
            CardDraft draft = card.toDraft();
            String argument = rule.getArgument();
            switch (rule.getAction()) {
                case ASSIGN -> {
                    if (!argument.equalsIgnoreCase(draft.assignee())) {
                        card.update(new CardDraft(draft.title(), draft.description(), draft.priority(), draft.dueDate(), argument, draft.tags(), draft.estimate()), now);
                        notifications.notify(argument, actor, "assigned \"%s\" to you".formatted(card.getTitle()), lane.getBoard().getId(), card.getId());
                        done.add("assigned to " + argument);
                    }
                }
                case ADD_TAG -> {
                    if (draft.tags().stream().noneMatch(argument::equalsIgnoreCase)) {
                        Set<String> tags = new LinkedHashSet<>(draft.tags());
                        tags.add(argument);
                        card.update(new CardDraft(draft.title(), draft.description(), draft.priority(), draft.dueDate(), draft.assignee(), tags, draft.estimate()), now);
                        done.add("tagged " + argument);
                    }
                }
                case SET_PRIORITY -> {
                    Priority priority = Priority.valueOf(argument);
                    if (draft.priority() != priority) {
                        card.update(new CardDraft(draft.title(), draft.description(), priority, draft.dueDate(), draft.assignee(), draft.tags(), draft.estimate()), now);
                        done.add("priority " + priority.label().toLowerCase(java.util.Locale.ROOT));
                    }
                }
                case DUE_IN_DAYS -> {
                    LocalDate due = today.plusDays(Integer.parseInt(argument));
                    card.update(new CardDraft(draft.title(), draft.description(), draft.priority(), due, draft.assignee(), draft.tags(), draft.estimate()), now);
                    done.add("due " + due);
                }
                case CLEAR_DUE -> {
                    if (draft.dueDate() != null) {
                        card.update(new CardDraft(draft.title(), draft.description(), draft.priority(), null, draft.assignee(), draft.tags(), draft.estimate()), now);
                        done.add("due date cleared");
                    }
                }
                case NOTIFY -> {
                    if (notifications.notify(argument, actor, "\"%s\" entered %s".formatted(card.getTitle(), lane.getName()), lane.getBoard().getId(), card.getId())) {
                        done.add("notified " + argument);
                    }
                }
                default -> {
                    // nightly rules do nothing on enter
                }
            }
        }
        return done;
    }

    @Scheduled(cron = "0 15 0 * * *")
    public void archiveStaleNightly() {
        int archived = archiveStale(clock.instant());
        if (archived > 0) {
            LOG.info("Archived {} cards by lane rules", archived);
        }
    }

    /** Archives cards that have been in a lane longer than its archive rule allows; returns how many. */
    public int archiveStale(Instant now) {
        int archived = 0;
        for (LaneRule rule : rules.findAllByAction(RuleAction.ARCHIVE_AFTER_DAYS)) {
            Duration limit = Duration.ofDays(Integer.parseInt(rule.getArgument()));
            Lane lane = lanes.findById(rule.getLaneId()).orElse(null);
            if (lane == null) {
                continue;
            }
            List<Card> live = cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(lane.getId());
            List<Card> staying = new ArrayList<>();
            for (Card card : live) {
                if (Duration.between(card.getLaneEnteredAt(), now).compareTo(limit) > 0) {
                    card.archive(now);
                    activityLog.record(BoardChangedEvent.content(lane.getBoard().getId(), ActivityLog.SYSTEM_ACTOR,
                            "archived \"%s\" after %d days in %s".formatted(card.getTitle(), limit.toDays(), lane.getName())).forCard(card.getId()));
                    archived++;
                } else {
                    staying.add(card);
                }
            }
            for (int i = 0; i < staying.size(); i++) {
                staying.get(i).setPosition(i);
            }
        }
        return archived;
    }
}
