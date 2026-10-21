package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.BoardInsights;
import dev.gatzka.flowboard.domain.BoardInsights.AssigneeLoad;
import dev.gatzka.flowboard.domain.BoardInsights.DailyCount;
import dev.gatzka.flowboard.domain.BoardInsights.LaneLoad;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.ui.support.Durations;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Numbers about the board: stat tiles, load per lane, completions per day, work per person. */
final class InsightsDialog extends Dialog {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");

    private final Div content = new Div();

    InsightsDialog() {
        addClassName("insights");
        setHeaderTitle("Insights");
        setWidth("min(760px, 95vw)");
        setModal(false);
        setDraggable(true);

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel("Close insights");
        getHeader().add(close);

        content.addClassName("insights__content");
        add(content);
    }

    void show(BoardInsights insights) {
        content.removeAll();
        content.add(tiles(insights), lanes(insights), completions(insights), people(insights));
    }

    private static Component tiles(BoardInsights insights) {
        Div tiles = new Div(
                tile("Open", Integer.toString(insights.openCards()), null),
                tile("Completed", Integer.toString(insights.completedCards()),
                        "%.0f%% of all cards".formatted(insights.completionRatio() * 100)),
                tile("Overdue", Integer.toString(insights.overdueCards()), insights.overdueCards() > 0 ? "needs attention" : "nothing late")
                        .withState(insights.overdueCards() > 0 ? "warn" : "ok"),
                tile("Due in 7 days", Integer.toString(insights.dueThisWeek()), null),
                tile("Done in 7 days", Integer.toString(insights.completedLast7Days()), null),
                tile("Avg. cycle time", insights.averageCycleTime().map(Durations::humanize).orElse("–"), "creation to done"),
                tile("Open points", Integer.toString(insights.openPoints()), "estimated work left"),
                tile("Velocity", formatVelocity(insights.velocityPerWeek()), "points per week, 4-week average"));
        tiles.addClassName("insights__tiles");
        return tiles;
    }

    private static String formatVelocity(double pointsPerWeek) {
        return pointsPerWeek == Math.floor(pointsPerWeek) ? "%.0f".formatted(pointsPerWeek) : "%.1f".formatted(pointsPerWeek);
    }

    private static Tile tile(String label, String value, String hint) {
        return new Tile(label, value, hint);
    }

    private static Component lanes(BoardInsights insights) {
        Div section = section("Cards per lane");
        Span hint = new Span("Hover a lane for the average time cards have spent there.");
        hint.addClassName("insights__hint");
        section.add(hint);
        int busiest = Math.max(1, insights.busiestLaneCount());
        for (LaneLoad lane : insights.lanes()) {
            String count = lane.wipLimit() == null ? Integer.toString(lane.cards()) : lane.cards() + " / " + lane.wipLimit();
            Div row = bar(lane.name(), lane.cards() * 100.0 / busiest, count, lane.isOverWipLimit() ? "over" : lane.done() ? "done" : null);
            lane.averageTimeInLane().ifPresent(average -> row.getElement().setAttribute("title",
                    "Cards have been in " + lane.name() + " for " + Durations.humanize(average) + " on average"));
            section.add(row);
        }
        return section;
    }

    private static Component completions(BoardInsights insights) {
        Div section = section("Completed per day");
        Div chart = new Div();
        chart.addClassName("insights__chart");
        int busiest = Math.max(1, insights.busiestDayCount());
        for (DailyCount day : insights.completedPerDay()) {
            Div column = new Div();
            column.addClassName("insights__column");
            Div fill = new Div();
            fill.addClassName("insights__column-fill");
            fill.getStyle().set("height", (day.count() * 100.0 / busiest) + "%");
            column.add(fill);
            column.getElement().setAttribute("title", DAY.format(day.day()) + ": " + day.count());
            chart.add(column);
        }
        Div axis = new Div(new Span(DAY.format(insights.completedPerDay().getFirst().day())),
                new Span(DAY.format(insights.completedPerDay().getLast().day())));
        axis.addClassName("insights__axis");
        section.add(chart, axis);
        return section;
    }

    private static Component people(BoardInsights insights) {
        Div section = section("Open work");
        Div priorities = new Div();
        priorities.addClassName("insights__priorities");
        for (Priority priority : Priority.values()) {
            int count = insights.openByPriority().getOrDefault(priority, 0);
            Badge badge = new Badge(priority.label() + " · " + count);
            badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            badge.getElement().setAttribute("data-priority", priority.name().toLowerCase(Locale.ROOT));
            badge.addClassName("priority-option");
            priorities.add(badge);
        }
        section.add(priorities);
        if (insights.openByAssignee().isEmpty()) {
            Span none = new Span("No open cards are assigned to anybody.");
            none.addClassName("insights__hint");
            section.add(none);
            return section;
        }
        int busiest = insights.openByAssignee().getFirst().openCards();
        for (AssigneeLoad person : insights.openByAssignee()) {
            Avatar avatar = new Avatar(person.name());
            avatar.setColorIndex(Participant.named(person.name()).colorIndex());
            avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
            Div row = bar(person.name(), person.openCards() * 100.0 / busiest, Integer.toString(person.openCards()), null);
            row.addComponentAsFirst(avatar);
            row.addClassName("insights__bar--person");
            section.add(row);
        }
        return section;
    }

    private static Div section(String title) {
        Div section = new Div(new H3(title));
        section.addClassName("insights__section");
        return section;
    }

    private static Div bar(String label, double percent, String value, String state) {
        Span name = new Span(label);
        name.addClassName("insights__bar-label");
        Div track = new Div();
        track.addClassName("insights__bar-track");
        Div fill = new Div();
        fill.addClassName("insights__bar-fill");
        fill.getStyle().set("width", percent + "%");
        track.add(fill);
        Span count = new Span(value);
        count.addClassName("insights__bar-value");
        Div row = new Div(name, track, count);
        row.addClassName("insights__bar");
        if (state != null) {
            row.getElement().setAttribute("data-state", state);
        }
        return row;
    }

    /** One number with a label and an optional hint underneath. */
    private static final class Tile extends Div {

        Tile(String label, String value, String hint) {
            addClassName("insights__tile");
            Span valueSpan = new Span(value);
            valueSpan.addClassName("insights__tile-value");
            Span labelSpan = new Span(label);
            labelSpan.addClassName("insights__tile-label");
            add(valueSpan, labelSpan);
            if (hint != null) {
                Span hintSpan = new Span(hint);
                hintSpan.addClassName("insights__tile-hint");
                add(hintSpan);
            }
        }

        Tile withState(String state) {
            getElement().setAttribute("data-state", state);
            return this;
        }
    }
}
