package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.CardSnapshot;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.function.Consumer;

/** Side sheet listing archived cards with restore and permanent delete. */
final class ArchiveDialog extends Dialog {

    private static final DateTimeFormatter ARCHIVED = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

    private final Div list = new Div();
    private final Span empty = new Span(I18n.t("archive.empty"));
    private final Consumer<CardSnapshot> onRestore;
    private final Consumer<CardSnapshot> onDelete;

    ArchiveDialog(Consumer<CardSnapshot> onRestore, Consumer<CardSnapshot> onDelete) {
        this.onRestore = onRestore;
        this.onDelete = onDelete;
        addClassName("activity-sheet");
        setHeaderTitle(I18n.t("board.menu.archive"));
        setModal(false);
        setDraggable(false);
        setCloseOnOutsideClick(true);

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel(I18n.t("archive.close"));
        getHeader().add(close);

        list.addClassName("archive__list");
        empty.addClassName("activity-sheet__empty");
        add(list, empty);
    }

    void show(List<CardSnapshot> archived) {
        list.removeAll();
        archived.forEach(card -> list.add(row(card)));
        empty.setVisible(archived.isEmpty());
    }

    private Div row(CardSnapshot card) {
        Span title = new Span(card.title());
        title.addClassName("archive__title");
        Span when = new Span(I18n.t("archive.when", ARCHIVED.withLocale(I18n.locale()).format(card.archivedAt().atZone(ZoneId.systemDefault()))));
        when.addClassName("archive__when");
        Button restore = new Button(I18n.t("archive.restore"), VaadinIcon.ARROW_BACKWARD.create(), event -> onRestore.accept(card));
        restore.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button(VaadinIcon.TRASH.create(), event -> confirmDelete(card));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR);
        delete.setAriaLabel(I18n.t("archive.deleteAria", card.title()));
        Div actions = new Div(restore, delete);
        actions.addClassName("archive__actions");
        Div row = new Div(new Div(title, when), actions);
        row.addClassName("archive__row");
        return row;
    }

    private void confirmDelete(CardSnapshot card) {
        ConfirmDialog confirm = new ConfirmDialog(I18n.t("archive.deleteTitle", card.title()),
                I18n.t("archive.deleteText"), I18n.t("common.delete"), event -> onDelete.accept(card));
        confirm.setCancelable(true);
        confirm.setConfirmButtonTheme("error primary");
        confirm.open();
    }
}
