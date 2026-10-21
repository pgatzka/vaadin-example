package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.service.SharingService;
import dev.gatzka.flowboard.ui.share.SharedBoardView;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.util.function.Supplier;

/** Turns the public read-only link of a board on or off and hands out its address. */
final class ShareDialog extends Dialog {

    private final SharingService sharing;
    private final BoardSnapshot board;
    private final Supplier<String> actor;
    private final Checkbox enabled = new Checkbox("Anyone with the link can view this board");
    private final TextField link = new TextField();
    private final Div linkRow = new Div();

    ShareDialog(BoardSnapshot board, SharingService sharing, Supplier<String> actor) {
        this.sharing = sharing;
        this.board = board;
        this.actor = actor;
        setHeaderTitle("Share \"" + board.name() + "\"");
        setWidth("min(520px, 95vw)");
        addClassName("share-dialog");

        Span explanation = new Span("Viewers see the board live, including every change as it happens, "
                + "but cannot edit anything and do not need an account. Turning the link off makes it stop working.");
        explanation.addClassName("share-dialog__hint");

        link.setReadOnly(true);
        link.setWidthFull();
        link.addClassName("share-dialog__link");
        Button copy = new Button(VaadinIcon.COPY.create(), event -> copy());
        copy.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        copy.setAriaLabel("Copy link");
        copy.setTooltipText("Copy link");
        HorizontalLayout row = new HorizontalLayout(link, copy);
        row.setWidthFull();
        row.setFlexGrow(1, link);
        row.setAlignItems(HorizontalLayout.Alignment.END);
        linkRow.add(row);

        enabled.addValueChangeListener(event -> {
            if (!event.isFromClient()) {
                return;
            }
            if (event.getValue()) {
                show(sharing.enable(board.id(), actor.get()));
                Toasts.success("Public link is on");
            } else {
                sharing.disable(board.id(), actor.get());
                show(null);
                Toasts.info("Public link is off");
            }
        });
        show(sharing.token(board.id()).orElse(null));

        add(explanation, enabled, linkRow);
        getFooter().add(new Button("Close", event -> close()));
    }

    private void show(String token) {
        enabled.setValue(token != null);
        linkRow.setVisible(token != null);
        link.setValue(token == null ? "" : SharedBoardView.pathTo(token));
        if (token != null) {
            getUI().ifPresent(ui -> ui.getPage().executeJs("return location.origin").then(String.class,
                    origin -> link.setValue(origin + "/" + SharedBoardView.pathTo(token))));
        }
    }

    private void copy() {
        String value = link.getValue();
        getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", value));
        Toasts.success("Link copied");
    }
}
