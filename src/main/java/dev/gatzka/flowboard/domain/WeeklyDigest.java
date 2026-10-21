package dev.gatzka.flowboard.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * What happened on a board in one week, ready to be read, printed or pasted somewhere.
 *
 * @param from first day of the week covered
 * @param to last day of the week covered (inclusive)
 * @param overdue open cards whose due date had passed by the end of the week
 * @param dueNextWeek open cards due in the seven days after the week
 */
public record WeeklyDigest(
        String boardName,
        LocalDate from,
        LocalDate to,
        int changes,
        List<CardSnapshot> completed,
        List<CardSnapshot> created,
        List<CardSnapshot> overdue,
        List<CardSnapshot> blocked,
        List<CardSnapshot> dueNextWeek,
        List<ActivityPulse.Contributor> contributors) {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy");

    public WeeklyDigest {
        completed = List.copyOf(completed);
        created = List.copyOf(created);
        overdue = List.copyOf(overdue);
        blocked = List.copyOf(blocked);
        dueNextWeek = List.copyOf(dueNextWeek);
        contributors = List.copyOf(contributors);
    }

    public int pointsCompleted() {
        return completed.stream().filter(CardSnapshot::isEstimated).mapToInt(CardSnapshot::estimate).sum();
    }

    public String period() {
        return DAY.format(from) + " – " + DAY_YEAR.format(to);
    }

    public boolean isQuiet() {
        return changes == 0 && completed.isEmpty() && created.isEmpty();
    }

    /** The digest as Markdown, the same text the page shows and the copy button hands out. */
    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(boardName).append(" · week of ").append(period()).append("\n\n");
        md.append("**").append(changes).append(changes == 1 ? " change" : " changes").append(" by ")
                .append(contributors.size()).append(contributors.size() == 1 ? " person" : " people").append(". ")
                .append(completed.size()).append(completed.size() == 1 ? " card" : " cards").append(" done");
        if (pointsCompleted() > 0) {
            md.append(" (").append(pointsCompleted()).append(" points)");
        }
        md.append(", ").append(created.size()).append(" created, ").append(overdue.size()).append(" overdue, ")
                .append(blocked.size()).append(" blocked.**\n\n");
        section(md, "Done", completed, false);
        section(md, "New", created, false);
        section(md, "Overdue", overdue, true);
        section(md, "Blocked", blocked, false);
        section(md, "Due next week", dueNextWeek, true);
        md.append("## Most active\n\n");
        if (contributors.isEmpty()) {
            md.append("_Nobody made changes this week._\n");
        }
        for (ActivityPulse.Contributor person : contributors) {
            md.append("- ").append(person.name()).append(": ").append(person.changes())
                    .append(person.changes() == 1 ? " change" : " changes").append('\n');
        }
        return md.toString();
    }

    private static void section(StringBuilder md, String title, List<CardSnapshot> cards, boolean withDueDate) {
        md.append("## ").append(title).append(" (").append(cards.size()).append(")\n\n");
        if (cards.isEmpty()) {
            md.append("_Nothing here._\n\n");
            return;
        }
        for (CardSnapshot card : cards) {
            md.append("- ").append(card.title());
            if (withDueDate && card.dueDate() != null) {
                md.append(" — due ").append(DAY.format(card.dueDate()));
            }
            if (card.assignee() != null) {
                md.append(" (").append(card.assignee()).append(')');
            }
            md.append('\n');
        }
        md.append('\n');
    }
}
