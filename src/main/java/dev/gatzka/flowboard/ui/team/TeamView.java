package dev.gatzka.flowboard.ui.team;

import dev.gatzka.flowboard.ui.i18n.I18n;

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

    private final WorkloadService workloadService;
    private final BoardChangeBroadcaster broadcaster;
    private final AccessService access;
    private final UserSession session;
    private final Clock clock;

    private final Span subtitle = new Span();
    private final IntegerField capacity = new IntegerField(I18n.t("team.capacity"));
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
        H2 heading = new H2(I18n.t("nav.team"));
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
        capacity.setHelperText(I18n.t(workload.pointsBased() ? "team.capacityPoints" : "team.capacityCards"));
        render();
    }

    private void render() {
        int limit = capacity.getValue() == null ? DEFAULT_CARDS_CAPACITY : capacity.getValue();
        subtitle.setText(workload.isEmpty()
                ? I18n.t("team.empty")
                : I18n.t("team.summary", workload.openCards(), workload.people().size(), unit(limit)));
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
        DateTimeFormatter DAY = I18n.formatter("MMM d");
        Div header = new Div(cell("team__corner", new Span(I18n.t("team.person"))));
        for (int week = 0; week < Workload.WEEKS; week++) {
            LocalDate start = workload.weekStart(week);
            Span title = new Span(week == 0 ? I18n.t("team.thisWeek") : week == 1 ? I18n.t("team.nextWeek") : I18n.t("team.week", week + 1));
            Span range = new Span(DAY.format(start) + " \u2013 " + DAY.format(start.plusDays(6)));
            range.addClassName("team__range");
            header.add(cell("team__head", title, range));
        }
        header.add(cell("team__head", new Span(I18n.t("work.later"))), cell("team__head", new Span(I18n.t("team.noDate"))), cell("team__head", new Span(I18n.t("team.total"))));
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
        Span label = new Span(person.isUnassigned() ? I18n.t("filter.unassigned") : person.name());
        label.addClassName("team__name");
        name.add(label);
        if (person.overdue() > 0) {
            Badge overdue = new Badge(I18n.t("tile.overdue", person.overdue()));
            overdue.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.ERROR);
            overdue.addClassName("team__overdue");
            name.add(overdue);
        }
        Div row = new Div(name);
        row.addClassName("team__row");
        row.getElement().setAttribute("data-person", person.name());
        DateTimeFormatter DAY = I18n.formatter("MMM d");
        for (int week = 0; week < Workload.WEEKS; week++) {
            LocalDate start = workload.weekStart(week);
            row.add(loadCell(person, person.week(week), limit, true,
                    person.name() + " \u00b7 " + I18n.t("team.weekOf", DAY.format(start))));
        }
        row.add(loadCell(person, person.later(), limit, false, person.name() + " \u00b7 " + I18n.t("work.later").toLowerCase(I18n.locale())),
                loadCell(person, person.undated(), limit, false, person.name() + " \u00b7 " + I18n.t("work.noDueDate").toLowerCase(I18n.locale())));
        Span total = new Span(unit(workload.load(person)));
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
        Span amount = new Span(unit(load));
        amount.addClassName("team__amount");
        Span count = new Span(workload.pointsBased() ? I18n.count(slot.count(), "unit.card", "unit.cards") : "");
        count.addClassName("team__count");
        Div numbers = new Div(amount, count);
        numbers.addClassName("team__load");
        Button open = new Button();
        open.getElement().appendChild(numbers.getElement());
        open.addClickListener(event -> showCards(title, slot));
        open.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        open.addClassName("team__open");
        open.setAriaLabel(I18n.t("team.showCards", title));
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
        Button close = new Button(I18n.t("common.close"), event -> dialog.close());
        dialog.getFooter().add(close);
        dialog.open();
    }

    private void renderSuggestions(List<Suggestion> proposals) {
        if (proposals.isEmpty()) {
            return;
        }
        H3 heading = new H3(I18n.t("team.evenOut"));
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
        String week = I18n.t("team.weekOf", I18n.formatter("MMM d").format(workload.weekStart(proposal.weekIndex())));
        if (proposal.isAssignment()) {
            text.add(new Span(I18n.t("team.assignPrefix")), card, new Span(I18n.t("team.assignSuffix", proposal.to(), week, unit(proposal.toAfter()))));
        } else {
            text.add(new Span(I18n.t("team.movePrefix")), card, new Span(I18n.t("team.moveSuffix", proposal.from(), proposal.to(), week,
                    unit(proposal.fromAfter()), unit(proposal.toAfter()))));
        }
        Button apply = new Button(I18n.t(proposal.isAssignment() ? "team.assign" : "team.reassign"), event -> {
            workloadService.reassign(location.card().id(), proposal.to(), session.getDisplayName());
            Toasts.success(I18n.t("team.assigned", location.card().title(), proposal.to()));
        });
        apply.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        apply.addClassName("team__reassign");
        Div row = new Div(VaadinIcon.EXCHANGE.create(), text, apply);
        row.addClassName("team__suggestion");
        return row;
    }

    /** The load in the workload's unit, in the current language. */
    private String unit(int amount) {
        return workload.pointsBased() ? I18n.t("unit.points", amount) : I18n.count(amount, "unit.card", "unit.cards");
    }

    private static Div cell(String classes, com.vaadin.flow.component.Component... content) {
        Div cell = new Div(content);
        for (String name : classes.split(" ")) {
            cell.addClassName(name);
        }
        return cell;
    }
}
