package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

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
    private final Checkbox enabled = new Checkbox(I18n.t("share.enabled"));
    private final TextField link = new TextField();
    private final TextField calendar = new TextField(I18n.t("share.calendar"));
    private final Div linkRow = new Div();

    ShareDialog(BoardSnapshot board, SharingService sharing, Supplier<String> actor) {
        this.sharing = sharing;
        this.board = board;
        this.actor = actor;
        setHeaderTitle(I18n.t("share.title", board.name()));
        setWidth("min(520px, 95vw)");
        addClassName("share-dialog");

        Span explanation = new Span(I18n.t("share.hint"));
        explanation.addClassName("share-dialog__hint");

        link.setReadOnly(true);
        link.setWidthFull();
        link.addClassName("share-dialog__link");
        Button copy = new Button(VaadinIcon.COPY.create(), event -> copy());
        copy.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        copy.setAriaLabel(I18n.t("editor.copyLink"));
        copy.setTooltipText(I18n.t("editor.copyLink"));
        HorizontalLayout row = new HorizontalLayout(link, copy);
        row.setWidthFull();
        row.setFlexGrow(1, link);
        row.setAlignItems(HorizontalLayout.Alignment.END);
        linkRow.add(row);

        calendar.setReadOnly(true);
        calendar.setWidthFull();
        calendar.setHelperText(I18n.t("share.calendarHelper"));
        calendar.addClassName("share-dialog__calendar");
        linkRow.add(calendar);

        enabled.addValueChangeListener(event -> {
            if (!event.isFromClient()) {
                return;
            }
            if (event.getValue()) {
                show(sharing.enable(board.id(), actor.get()));
                Toasts.success(I18n.t("share.on"));
            } else {
                sharing.disable(board.id(), actor.get());
                show(null);
                Toasts.info(I18n.t("share.off"));
            }
        });
        show(sharing.token(board.id()).orElse(null));

        add(explanation, enabled, linkRow);
        getFooter().add(new Button(I18n.t("common.close"), event -> close()));
    }

    private void show(String token) {
        enabled.setValue(token != null);
        linkRow.setVisible(token != null);
        link.setValue(token == null ? "" : SharedBoardView.pathTo(token));
        calendar.setValue(token == null ? "" : calendarPath(token));
        if (token != null) {
            getUI().ifPresent(ui -> ui.getPage().executeJs("return location.origin").then(String.class, origin -> {
                link.setValue(origin + "/" + SharedBoardView.pathTo(token));
                calendar.setValue(origin + "/" + calendarPath(token));
            }));
        }
    }

    static String calendarPath(String token) {
        return "api/calendar/" + token + ".ics";
    }

    private void copy() {
        String value = link.getValue();
        getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", value));
        Toasts.success(I18n.t("common.linkCopied"));
    }
}
