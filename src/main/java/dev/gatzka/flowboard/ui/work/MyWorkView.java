package dev.gatzka.flowboard.ui.work;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.CardLocation;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.WorkService;
import dev.gatzka.flowboard.ui.ProfileChangedEvent;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.support.UiAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Everything assigned to the current user, across boards, grouped by urgency. */
@Route("my-work")
@PermitAll
@PageTitle("My work · Flowboard")
public class MyWorkView extends VerticalLayout {

    /** Urgency buckets in display order. */
    enum Bucket {
        OVERDUE("Overdue"),
        TODAY("Due today"),
        THIS_WEEK("Due this week"),
        LATER("Later"),
        UNDATED("No due date"),
        COMPLETED("Completed");

        private final String label;

        Bucket(String label) {
            this.label = label;
        }

        static Bucket of(CardLocation location, LocalDate today) {
            if (location.isCompleted()) {
                return COMPLETED;
            }
            LocalDate due = location.card().dueDate();
            if (due == null) {
                return UNDATED;
            }
            if (due.isBefore(today)) {
                return OVERDUE;
            }
            if (due.equals(today)) {
                return TODAY;
            }
            return due.isBefore(today.plusDays(7)) ? THIS_WEEK : LATER;
        }
    }

    private final WorkService workService;
    private final BoardChangeBroadcaster broadcaster;
    private final UserSession session;
    private final AccessService access;
    private final Clock clock;

    private final Span subtitle = new Span();
    private final Div groups = new Div();
    private Registration changeSubscription;
    private Registration profileSubscription;

    public MyWorkView(WorkService workService, BoardChangeBroadcaster broadcaster, AccessService access, UserSession session, Clock clock) {
        this.access = access;
        this.workService = workService;
        this.broadcaster = broadcaster;
        this.session = session;
        this.clock = clock;

        addClassName("boards-view");
        setSizeFull();
        H2 heading = new H2("My work");
        subtitle.addClassName("page-subtitle");
        groups.addClassName("work-groups");
        add(new Div(heading, subtitle), groups);
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        changeSubscription = broadcaster.subscribe(event -> UiAccess.run(ui, this::refresh));
        profileSubscription = ComponentUtil.addListener(ui, ProfileChangedEvent.class, event -> refresh());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        changeSubscription.remove();
        profileSubscription.remove();
    }

    private void refresh() {
        String me = session.getDisplayName();
        LocalDate today = LocalDate.now(clock);
        List<CardLocation> mine = workService.assignedTo(me).stream()
                .filter(location -> access.canAccess(location.boardId(), session.login())).toList();
        Map<Bucket, List<CardLocation>> byBucket = mine.stream()
                .collect(Collectors.groupingBy(location -> Bucket.of(location, today), () -> new EnumMap<>(Bucket.class), Collectors.toList()));

        long open = mine.stream().filter(location -> !location.isCompleted()).count();
        subtitle.setText(open == 0
                ? "Nothing is assigned to " + me + " right now. Assign cards to this name to see them here."
                : open + (open == 1 ? " open card" : " open cards") + " assigned to " + me + " across all boards.");

        groups.removeAll();
        for (Bucket bucket : Bucket.values()) {
            List<CardLocation> locations = byBucket.getOrDefault(bucket, List.of());
            if (!locations.isEmpty()) {
                groups.add(group(bucket, locations, today));
            }
        }
    }

    private Div group(Bucket bucket, List<CardLocation> locations, LocalDate today) {
        H3 title = new H3(bucket.label + " · " + locations.size());
        Div list = new Div();
        list.addClassName("work-list");
        locations.forEach(location -> list.add(new CardLocationRow(location, today)));
        Div group = new Div(title, list);
        group.addClassName("work-group");
        group.getElement().setAttribute("data-bucket", bucket.name().toLowerCase());
        return group;
    }
}
