package dev.gatzka.flowboard.ui.share;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.AttachmentService;
import dev.gatzka.flowboard.service.SharingService;
import dev.gatzka.flowboard.ui.account.LoginView;
import dev.gatzka.flowboard.ui.board.CardComponent;
import dev.gatzka.flowboard.ui.support.Themes;
import dev.gatzka.flowboard.ui.support.UiAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A board as seen through its public link: the same lanes and cards, live, but nothing to click.
 * Reachable without an account; when the link is turned off the page says so.
 */
@Route(value = "share/:" + SharedBoardView.TOKEN, autoLayout = false)
@AnonymousAllowed
public class SharedBoardView extends Div implements BeforeEnterObserver, HasDynamicTitle {

    public static final String TOKEN = "token";

    private final SharingService sharing;
    private final BoardChangeBroadcaster broadcaster;
    private final AttachmentService attachments;
    private final Clock clock;

    private final H2 heading = new H2();
    private final Span description = new Span();
    private final Div lanes = new Div();
    private final Div gone = new Div();
    private final Div content = new Div();
    private String token;
    private BoardSnapshot board;
    private Registration changeSubscription;

    public SharedBoardView(SharingService sharing, BoardChangeBroadcaster broadcaster, AttachmentService attachments, Clock clock) {
        this.sharing = sharing;
        this.attachments = attachments;
        this.broadcaster = broadcaster;
        this.clock = clock;
        addClassName("shared");

        H1 brand = new H1("Flowboard");
        brand.addClassName("shared__brand");
        Badge readOnly = new Badge(I18n.t("shared.readOnly"), VaadinIcon.EYE.create());
        readOnly.addThemeVariants(BadgeVariant.CONTRAST);
        readOnly.addClassName("shared__badge");
        Anchor signIn = new Anchor(LoginView.PATH, I18n.t("login.signIn"));
        signIn.addClassName("shared__sign-in");
        Div header = new Div(brand, readOnly, new Span(), signIn);
        header.addClassName("shared__header");

        heading.addClassName("shared__title");
        description.addClassName("shared__description");
        Div titles = new Div(heading, description);
        titles.addClassName("shared__titles");

        lanes.addClassName("lanes");
        lanes.addClassName("shared__lanes");
        content.add(titles, lanes);
        content.addClassName("shared__content");

        gone.addClassName("shared__gone");
        gone.add(new H2(I18n.t("shared.goneTitle")), new Span(I18n.t("shared.goneText")));
        gone.setVisible(false);

        add(header, content, gone);
    }

    public static String pathTo(String token) {
        return "share/" + token;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getRouteParameters().get(TOKEN).orElse("");
        board = sharing.findShared(token).orElse(null);
        render();
    }

    @Override
    public String getPageTitle() {
        return (board == null ? I18n.t("shared.title") : board.name()) + " \u00b7 Flowboard";
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        Themes.followSystem(ui);
        changeSubscription = broadcaster.subscribe(event -> {
            if (board != null && event.boardId() == board.id()) {
                UiAccess.run(ui, () -> onRemoteChange(event));
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        changeSubscription.remove();
    }

    private void onRemoteChange(BoardChangedEvent event) {
        board = event.kind() == BoardChangedEvent.Kind.DELETED ? null : sharing.findShared(token).orElse(null);
        render();
    }

    private void render() {
        content.setVisible(board != null);
        gone.setVisible(board == null);
        lanes.removeAll();
        if (board == null) {
            return;
        }
        heading.setText(board.name());
        description.setText(board.description() == null ? "" : board.description());
        description.setVisible(board.description() != null && !board.description().isBlank());
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        board.lanes().forEach(lane -> lanes.add(lane(lane, today, now)));
    }

    private Div lane(LaneSnapshot lane, LocalDate today, Instant now) {
        Span name = new Span(lane.name());
        name.addClassName("lane__name");
        Span count = new Span(Integer.toString(lane.cards().size()));
        count.addClassName("lane__count");
        Div header = new Div(name, count);
        header.addClassName("lane__header");
        header.addClassName("lane__header--static");

        Div cards = new Div();
        cards.addClassName("lane__cards");
        lane.cards().forEach(card -> {
            CardComponent component = CardComponent.readOnly(card, today, now, lane.done());
            card.cover().ifPresent(cover -> component.showCover(cover, attachments::content));
            cards.add(component);
        });

        Div laneDiv = new Div(header, cards);
        laneDiv.addClassName("lane");
        if (lane.done()) {
            laneDiv.addClassName("lane--done");
        }
        return laneDiv;
    }
}
