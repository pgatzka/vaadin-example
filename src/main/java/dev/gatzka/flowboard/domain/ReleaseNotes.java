package dev.gatzka.flowboard.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What shipped: the cards completed in a period, grouped by milestone where they have one and by
 * their first tag otherwise, ready to paste into a changelog or an announcement.
 *
 * @param since first day of the period, inclusive
 * @param until last day of the period, inclusive
 */
public record ReleaseNotes(String boardName, LocalDate since, LocalDate until, List<Section> sections) {

    public static final String OTHER = "Other";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /** @param milestone whether the section is a milestone (as opposed to a tag or "Other") */
    public record Section(String title, boolean milestone, List<CardSnapshot> cards) {

        public Section {
            cards = List.copyOf(cards);
        }
    }

    public ReleaseNotes {
        sections = List.copyOf(sections);
    }

    /**
     * Groups the completed cards: milestone sections first in name order, then one section per
     * first tag, then "Other". Cards within a section keep the given order (completion order).
     */
    public static ReleaseNotes of(String boardName, LocalDate since, LocalDate until, List<CardSnapshot> completed, Map<Long, String> milestoneNames) {
        Map<String, List<CardSnapshot>> byMilestone = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<CardSnapshot>> byTag = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<CardSnapshot> other = new ArrayList<>();
        for (CardSnapshot card : completed) {
            String milestone = card.milestoneId() == null ? null : milestoneNames.get(card.milestoneId());
            if (milestone != null) {
                byMilestone.computeIfAbsent(milestone, name -> new ArrayList<>()).add(card);
            } else if (!card.tags().isEmpty()) {
                byTag.computeIfAbsent(new TreeSet<>(String.CASE_INSENSITIVE_ORDER) {{ addAll(card.tags()); }}.first(), tag -> new ArrayList<>()).add(card);
            } else {
                other.add(card);
            }
        }
        List<Section> sections = new ArrayList<>();
        byMilestone.forEach((name, cards) -> sections.add(new Section(name, true, cards)));
        byTag.forEach((tag, cards) -> sections.add(new Section(tag, false, cards)));
        if (!other.isEmpty()) {
            sections.add(new Section(OTHER, false, other));
        }
        return new ReleaseNotes(boardName, since, until, sections);
    }

    public int cardCount() {
        return sections.stream().mapToInt(section -> section.cards().size()).sum();
    }

    public int points() {
        return sections.stream().flatMap(section -> section.cards().stream()).filter(CardSnapshot::isEstimated).mapToInt(CardSnapshot::estimate).sum();
    }

    /** Everybody who had a completed card assigned, in name order. */
    public List<String> contributors() {
        return sections.stream().flatMap(section -> section.cards().stream()).map(CardSnapshot::assignee)
                .filter(name -> name != null && !name.isBlank()).distinct()
                .sorted(Comparator.comparing(String::toLowerCase)).toList();
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    /** The notes as Markdown, in English: headings, a one-line summary, one bullet per card. */
    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(boardName).append(" · Release notes · ").append(DAY.format(since)).append(" – ").append(DAY.format(until)).append("\n\n");
        if (isEmpty()) {
            md.append("_Nothing was completed in this period._\n");
            return md.toString();
        }
        md.append('_').append(cardCount()).append(cardCount() == 1 ? " card" : " cards").append(" completed");
        if (points() > 0) {
            md.append(", ").append(points()).append(" points");
        }
        List<String> people = contributors();
        if (!people.isEmpty()) {
            md.append(", by ").append(String.join(", ", people));
        }
        md.append("._\n\n");
        for (Section section : sections) {
            md.append("## ").append(section.milestone() ? "🏁 " : "").append(section.title())
                    .append(" (").append(section.cards().size()).append(")\n\n");
            for (CardSnapshot card : section.cards()) {
                md.append("- ").append(card.title());
                if (card.assignee() != null && !card.assignee().isBlank()) {
                    md.append(" — ").append(card.assignee());
                }
                md.append('\n');
            }
            md.append('\n');
        }
        return md.toString().stripTrailing() + "\n";
    }

    /** A sensible default period: the last two weeks. */
    public static LocalDate defaultSince(LocalDate today) {
        return today.minusDays(13);
    }
}
