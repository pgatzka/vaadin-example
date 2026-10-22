package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;
import dev.gatzka.flowboard.ui.i18n.Labels;

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
import com.vaadin.flow.component.Svg;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.ActivityPulse;
import dev.gatzka.flowboard.domain.BoardInsights;
import dev.gatzka.flowboard.domain.BoardInsights.AssigneeLoad;
import dev.gatzka.flowboard.domain.BoardInsights.DailyCount;
import dev.gatzka.flowboard.domain.BoardInsights.LaneLoad;
import dev.gatzka.flowboard.domain.CumulativeFlow;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.ui.support.Durations;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Locale;

/** Numbers about the board: stat tiles, load per lane, completions per day, work per person. */
final class InsightsDialog extends Dialog {


    private final Div content = new Div();

    private final java.util.function.Supplier<Instant> now;

    InsightsDialog(java.util.function.Supplier<Instant> now) {
        this.now = now;
        addClassName("insights");
        setHeaderTitle(I18n.t("board.insights"));
        setWidth("min(760px, 95vw)");
        setModal(false);
        setDraggable(true);

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel(I18n.t("insights.close"));
        getHeader().add(close);

        content.addClassName("insights__content");
        add(content);
    }

    void show(BoardInsights insights, CumulativeFlow flow, ActivityPulse pulse) {
        content.removeAll();
        content.add(tiles(insights), lanes(insights), flowSection(flow), completions(insights), people(insights), pulseSection(pulse));
    }

    private Component pulseSection(ActivityPulse pulse) {
        Div section = section(I18n.t("insights.activityWeeks", ActivityPulse.WEEKS));
        Div tiles = new Div(
                tile(I18n.t("insights.changes"), Integer.toString(pulse.totalChanges()), null),
                tile(I18n.t("insights.activeDays"), Long.toString(pulse.activeDays()), I18n.t("insights.of", pulse.days().size())),
                tile(I18n.t("insights.streak"), I18n.t("insights.days", pulse.currentStreak()), I18n.t("insights.inARow")));
        tiles.addClassName("insights__tiles");
        tiles.addClassName("insights__tiles--compact");
        section.add(tiles);
        if (pulse.isQuiet()) {
            Span hint = new Span(I18n.t("insights.quiet", ActivityPulse.WEEKS));
            hint.addClassName("insights__hint");
            section.add(hint);
            return section;
        }
        Svg heatmap = new Svg(HeatmapChart.svg(pulse));
        Div scroller = new Div(heatmap);
        scroller.addClassName("insights__heatmap");
        section.add(scroller);
        if (!pulse.contributors().isEmpty()) {
            int busiest = pulse.contributors().getFirst().changes();
            for (ActivityPulse.Contributor person : pulse.contributors()) {
                Avatar avatar = new Avatar(person.name());
                avatar.setColorIndex(Participant.named(person.name()).colorIndex());
                avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
                Div row = bar(person.name(), person.changes() * 100.0 / busiest, Integer.toString(person.changes()), null);
                row.addComponentAsFirst(avatar);
                row.addClassName("insights__bar--person");
                row.getElement().setAttribute("title", I18n.t("insights.lastActive", Durations.ago(person.lastActive(), now.get())));
                section.add(row);
            }
        }
        return section;
    }

    private static Component flowSection(CumulativeFlow flow) {
        Div section = section(I18n.t("insights.flow"));
        if (!flow.hasEnoughData()) {
            Span hint = new Span(I18n.t("insights.flowHint"));
            hint.addClassName("insights__hint");
            section.add(hint);
            return section;
        }
        Svg chart = new Svg(CumulativeFlowChart.svg(flow, 640, 160));
        Div legend = new Div();
        legend.addClassName("insights__legend");
        for (int i = 0; i < flow.series().size(); i++) {
            Span swatch = new Span();
            swatch.addClassName("insights__swatch");
            swatch.getStyle().set("background", CumulativeFlowChart.colorFor(flow.series().get(i), i));
            Span entry = new Span(swatch, new Span(flow.series().get(i).laneName()));
            entry.addClassName("insights__legend-entry");
            legend.add(entry);
        }
        section.add(chart, legend);
        return section;
    }

    private static Component tiles(BoardInsights insights) {
        Div tiles = new Div(
                tile(I18n.t("insights.open"), Integer.toString(insights.openCards()), null),
                tile(I18n.t("card.completed"), Integer.toString(insights.completedCards()),
                        I18n.t("insights.ofAllCards", Math.round(insights.completionRatio() * 100))),
                tile(I18n.t("home.overdue"), Integer.toString(insights.overdueCards()), I18n.t(insights.overdueCards() > 0 ? "insights.needsAttention" : "insights.nothingLate"))
                        .withState(insights.overdueCards() > 0 ? "warn" : "ok"),
                tile(I18n.t("home.dueIn7Days"), Integer.toString(insights.dueThisWeek()), null),
                tile(I18n.t("home.doneIn7Days"), Integer.toString(insights.completedLast7Days()), null),
                tile(I18n.t("insights.cycleTime"), insights.averageCycleTime().map(Durations::humanize).orElse("\u2013"), I18n.t("insights.creationToDone")),
                tile(I18n.t("insights.openPoints"), Integer.toString(insights.openPoints()), I18n.t("insights.workLeft")),
                tile(I18n.t("insights.velocity"), formatVelocity(insights.velocityPerWeek()), I18n.t("insights.velocityHint")));
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
        Div section = section(I18n.t("insights.cardsPerLane"));
        Span hint = new Span(I18n.t("insights.laneHint"));
        hint.addClassName("insights__hint");
        section.add(hint);
        int busiest = Math.max(1, insights.busiestLaneCount());
        for (LaneLoad lane : insights.lanes()) {
            String count = lane.wipLimit() == null ? Integer.toString(lane.cards()) : lane.cards() + " / " + lane.wipLimit();
            Div row = bar(lane.name(), lane.cards() * 100.0 / busiest, count, lane.isOverWipLimit() ? "over" : lane.done() ? "done" : null);
            lane.averageTimeInLane().ifPresent(average -> row.getElement().setAttribute("title",
                    I18n.t("insights.laneAverage", lane.name(), Durations.humanize(average))));
            section.add(row);
        }
        return section;
    }

    private static Component completions(BoardInsights insights) {
        Div section = section(I18n.t("insights.completedPerDay"));
        DateTimeFormatter DAY = I18n.formatter("MMM d");
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
        Div section = section(I18n.t("insights.openWork"));
        Div priorities = new Div();
        priorities.addClassName("insights__priorities");
        for (Priority priority : Priority.values()) {
            int count = insights.openByPriority().getOrDefault(priority, 0);
            Badge badge = new Badge(Labels.priority(priority) + " \u00b7 " + count);
            badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            badge.getElement().setAttribute("data-priority", priority.name().toLowerCase(Locale.ROOT));
            badge.addClassName("priority-option");
            priorities.add(badge);
        }
        section.add(priorities);
        if (insights.openByAssignee().isEmpty()) {
            Span none = new Span(I18n.t("insights.nobody"));
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
