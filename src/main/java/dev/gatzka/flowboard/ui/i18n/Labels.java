package dev.gatzka.flowboard.ui.i18n;

import dev.gatzka.flowboard.domain.BoardTemplate;
import dev.gatzka.flowboard.domain.Grouping;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.Recurrence;
import dev.gatzka.flowboard.domain.Requirement;
import dev.gatzka.flowboard.domain.RuleAction;
import dev.gatzka.flowboard.domain.Timeline;
import dev.gatzka.flowboard.domain.WebhookFormat;

/** Translated names of the domain's enums; the enums' own labels stay English for stored data and exports. */
public final class Labels {

    private Labels() {
    }

    public static String priority(Priority priority) {
        return I18n.t("priority." + priority.name());
    }

    public static String recurrence(Recurrence recurrence) {
        return I18n.t("recurrence." + recurrence.name());
    }

    public static String grouping(Grouping grouping) {
        return I18n.t("grouping." + grouping.name());
    }

    public static String webhookFormat(WebhookFormat format) {
        return I18n.t("webhookFormat." + format.name());
    }

    public static String groupBy(Timeline.GroupBy groupBy) {
        return I18n.t("groupBy." + groupBy.name());
    }

    public static String ruleAction(RuleAction action) {
        return I18n.t("rule." + action.name());
    }

    public static String ruleHint(RuleAction action) {
        return I18n.t("rule.hint." + action.name());
    }

    /** A rule with its argument in words, e.g. "Add the tag qa". */
    public static String rule(RuleAction action, String argument) {
        return switch (action) {
            case DUE_IN_DAYS -> "0".equals(argument) ? I18n.t("rule.describe.dueToday") : I18n.t("rule.describe.dueIn", Integer.parseInt(argument));
            case ARCHIVE_AFTER_DAYS -> I18n.t("rule.describe.archiveAfter", Integer.parseInt(argument));
            case SET_PRIORITY -> I18n.t("rule.describe.priority", priority(Priority.valueOf(argument)));
            case REQUIRE -> I18n.t("rule.describe.require", requirement(Requirement.valueOf(argument)));
            case CLEAR_DUE -> ruleAction(action);
            default -> ruleAction(action) + " " + argument;
        };
    }

    /** A requirement as a noun phrase, e.g. "an estimate". */
    public static String requirement(Requirement requirement) {
        return I18n.t("requirement." + requirement.name());
    }

    public static String boardTemplate(BoardTemplate template) {
        return I18n.t("boardTemplate." + template.name());
    }

    public static String boardTemplateDescription(BoardTemplate template) {
        return I18n.t("boardTemplate." + template.name() + ".description");
    }
}
