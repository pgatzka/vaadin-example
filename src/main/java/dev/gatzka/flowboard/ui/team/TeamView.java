package dev.gatzka.flowboard.ui.team;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.CardLocation;
import dev.gatzka.flowboard.domain.Workload;
import dev.gatzka.flowboard.domain.Workload.Person;
import dev.gatzka.flowboard.domain.Workload.Slot;
import dev.gatzka.flowboard.domain.Workload.Suggestion;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.service.WorkloadService;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import dev.gatzka.flowboard.ui.work.CardLocationRow;
import jakarta.annotation.security.PermitAll;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** Who has how much to do, week by week, across every board the user can see, with suggestions to even it out. */
@Route("team")
@PermitAll
@PageTitle("Team · Flowboard")
public class TeamView extends VerticalLayout {

    static final int DEFAULT_POINTS_CAPACITY = 10;
    static final int DEFAULT_CARDS_CAPACITY = 5;
    private static final String CAPACITY_ATTRIBUTE = TeamView.class.getName() + ".capacity";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");

    private final WorkloadService workloadService;
    private final BoardChangeBroadcaster broadcaster;
    private final AccessService access;
    private final UserSession session;
    private final Clock clock;

    private final Span subtitle = new Span();
    private final IntegerField capacity = new IntegerField("Capacity per person and week");
    private final Div table = new Div();
    private final Div suggestions = new Div();
    private Registration changeSubscription;
    private Workload workload;

    public TeamView(WorkloadService workloadService, BoardChangeBroadcaster broadcaster, AccessService access,
            UserSession session, Clock clock) {
        this.workloadService = workloadService;
        this.broadcaster = broadcaster;
        this.access = access;
        this.session = session;
        this.clock = clock;

        addClassName("boards-view");
        addClassName("team");
        setSizeFull();
        H2 heading = new H2("Team");
        subtitle.addClassName("page-subtitle");

        capacity.setId("team-capacity");
        capacity.setMin(1);
        capacity.setMax(999);
        capacity.setStepButtonsVisible(true);
        capacity.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                VaadinSession.getCurrent().setAttribute(CAPACITY_ATTRIBUTE, event.getValue());
                render();
            }
        });
        Div toolbar = new Div(capacity);
        toolbar.addClassName("team__toolbar");

        table.addClassName("team__table");
        suggestions.addClassName("team__suggestions");
        add(new Div(heading, subtitle), toolbar, table, suggestions);
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        changeSubscription = broadcaster.subscribe(event -> UiAccess.run(ui, this::refresh));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        changeSubscription.remove();
    }

    private void refresh() {
        Set<Long> boards = access.visibleBoardIds(session.login());
        workload = workloadService.workload(boards, LocalDate.now(clock));
        Integer remembered = (Integer) VaadinSession.getCurrent().getAttribute(CAPACITY_ATTRIBUTE);
        int defaultCapacity = workload.pointsBased() ? DEFAULT_POINTS_CAPACITY : DEFAULT_CARDS_CAPACITY;
        capacity.setValue(remembered != null ? remembered : defaultCapacity);
        capacity.setHelperText(workload.pointsBased() ? "In story points; a card without an estimate counts as one point." : "In cards.");
        render();
    }

    private void render() {
        int limit = capacity.getValue() == null ? DEFAULT_CARDS_CAPACITY : capacity.getValue();
        subtitle.setText(workload.isEmpty()
                ? "No open cards on the boards you can see."
                : "%d open %s across %d %s. Cells turn red above %s per week."
                        .formatted(workload.openCards(), workload.openCards() == 1 ? "card" : "cards", workload.people().size(),
                                workload.people().size() == 1 ? "person" : "people", workload.unit(limit)));
        table.removeAll();
        suggestions.removeAll();
        if (workload.isEmpty()) {
            return;
        }
        table.add(header());
        for (Person person : workload.people()) {
            table.add(row(person, limit));
        }
        renderSuggestions(workload.suggestions(limit));
    }

    private Div header() {
        Div header = new Div(cell("team__corner", new Span("Person")));
        for (int week = 0; week < Workload.WEEKS; week++) {
            LocalDate start = workload.weekStart(week);
            Span title = new Span(week == 0 ? "This week" : week == 1 ? "Next week" : "Week " + (week + 1));
            Span range = new Span(DAY.format(start) + " – " + DAY.format(start.plusDays(6)));
            range.addClassName("team__range");
            header.add(cell("team__head", title, range));
        }
        header.add(cell("team__head", new Span("Later")), cell("team__head", new Span("No date")), cell("team__head", new Span("Total")));
        header.addClassName("team__row");
        header.addClassName("team__row--head");
        return header;
    }

    private Div row(Person person, int limit) {
        Div name = new Div();
        name.addClassName("team__person");
        if (!person.isUnassigned()) {
            Avatar avatar = new Avatar(person.name());
            avatar.setColorIndex(Participant.named(person.name()).colorIndex());
            avatar.addThemeVariants(AvatarVariant.LUMO_SMALL);
            name.add(avatar);
        } else {
            name.add(VaadinIcon.USER_CLOCK.create());
        }
        Span label = new Span(person.name());
        label.addClassName("team__name");
        name.add(label);
        if (person.overdue() > 0) {
            Badge overdue = new Badge(person.overdue() + " overdue");
            overdue.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.ERROR);
            overdue.addClassName("team__overdue");
            name.add(overdue);
        }
        Div row = new Div(name);
        row.addClassName("team__row");
        row.getElement().setAttribute("data-person", person.name());
        for (int week = 0; week < Workload.WEEKS; week++) {
            LocalDate start = workload.weekStart(week);
            row.add(loadCell(person, person.week(week), limit, true,
                    person.name() + " · week of " + DAY.format(start)));
        }
        row.add(loadCell(person, person.later(), limit, false, person.name() + " · later"),
                loadCell(person, person.undated(), limit, false, person.name() + " · no due date"));
        Span total = new Span(workload.unit(workload.load(person)));
        total.addClassName("team__total");
        row.add(cell("team__cell team__cell--total", total));
        return row;
    }

    private Div loadCell(Person person, Slot slot, int limit, boolean capped, String title) {
        int load = workload.load(slot);
        Div cell = new Div();
        cell.addClassName("team__cell");
        cell.getElement().setAttribute("data-load", Integer.toString(load));
        if (slot.isEmpty()) {
            cell.addClassName("team__cell--empty");
            cell.add(new Span("–"));
            return cell;
        }
        if (capped && !person.isUnassigned() && load > limit) {
            cell.addClassName("team__cell--over");
        }
        Div bar = new Div();
        bar.addClassName("team__bar");
        double share = capped ? Math.min(1.0, (double) load / Math.max(1, limit)) : 0;
        bar.getStyle().set("--team-fill", String.format(java.util.Locale.ROOT, "%.0f%%", share * 100));
        Span amount = new Span(workload.unit(load));
        amount.addClassName("team__amount");
        Span count = new Span(workload.pointsBased() ? slot.count() + (slot.count() == 1 ? " card" : " cards") : "");
        count.addClassName("team__count");
        Div numbers = new Div(amount, count);
        numbers.addClassName("team__load");
        Button open = new Button();
        open.getElement().appendChild(numbers.getElement());
        open.addClickListener(event -> showCards(title, slot));
        open.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        open.addClassName("team__open");
        open.setAriaLabel("Show the cards of " + title);
        cell.add(bar, open);
        return cell;
    }

    private void showCards(String title, Slot slot) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.addClassName("team__cards");
        dialog.setWidth("min(560px, 95vw)");
        Div list = new Div();
        list.addClassName("work-list");
        LocalDate today = LocalDate.now(clock);
        slot.cards().forEach(location -> list.add(new CardLocationRow(location, today)));
        dialog.add(list);
        Button close = new Button("Close", event -> dialog.close());
        dialog.getFooter().add(close);
        dialog.open();
    }

    private void renderSuggestions(List<Suggestion> proposals) {
        if (proposals.isEmpty()) {
            return;
        }
        H3 heading = new H3("Even it out");
        heading.addClassName("team__suggestions-heading");
        suggestions.add(heading);
        for (Suggestion proposal : proposals) {
            suggestions.add(suggestionRow(proposal));
        }
    }

    private Div suggestionRow(Suggestion proposal) {
        CardLocation location = proposal.card();
        Span card = new Span(location.card().title());
        card.addClassName("team__suggestion-card");
        Span text = new Span();
        text.addClassName("team__suggestion-text");
        String week = "week of " + DAY.format(workload.weekStart(proposal.weekIndex()));
        if (proposal.isAssignment()) {
            text.add(new Span("Assign "), card, new Span(" to %s (%s): they would be at %s."
                    .formatted(proposal.to(), week, workload.unit(proposal.toAfter()))));
        } else {
            text.add(new Span("Move "), card, new Span(" from %s to %s (%s): %s would be at %s, %s at %s."
                    .formatted(proposal.from(), proposal.to(), week, proposal.from(), workload.unit(proposal.fromAfter()),
                            proposal.to(), workload.unit(proposal.toAfter()))));
        }
        Button apply = new Button(proposal.isAssignment() ? "Assign" : "Reassign", event -> {
            workloadService.reassign(location.card().id(), proposal.to(), session.getDisplayName());
            Toasts.success("\"%s\" is now assigned to %s".formatted(location.card().title(), proposal.to()));
        });
        apply.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        apply.addClassName("team__reassign");
        Div row = new Div(VaadinIcon.EXCHANGE.create(), text, apply);
        row.addClassName("team__suggestion");
        return row;
    }

    private static Div cell(String classes, com.vaadin.flow.component.Component... content) {
        Div cell = new Div(content);
        for (String name : classes.split(" ")) {
            cell.addClassName(name);
        }
        return cell;
    }
}
