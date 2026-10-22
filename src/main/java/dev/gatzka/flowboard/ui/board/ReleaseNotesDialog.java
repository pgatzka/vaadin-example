package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.ReleaseNotes;
import dev.gatzka.flowboard.service.ReleaseNotesService;
import dev.gatzka.flowboard.ui.i18n.I18n;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.time.Clock;
import java.time.LocalDate;

/** What shipped in a period, as Markdown you can read here or paste anywhere. */
final class ReleaseNotesDialog extends Dialog {

    private final BoardSnapshot board;
    private final ReleaseNotesService service;
    private final DatePicker since = new DatePicker(I18n.t("releaseNotes.since"));
    private final DatePicker until = new DatePicker(I18n.t("releaseNotes.until"));
    private final Markdown preview = new Markdown();
    private final Span summary = new Span();
    private ReleaseNotes notes;

    ReleaseNotesDialog(BoardSnapshot board, ReleaseNotesService service, Clock clock) {
        this.board = board;
        this.service = service;
        setHeaderTitle(I18n.t("releaseNotes.title"));
        setWidth("min(720px, 95vw)");
        addClassName("release-notes");

        LocalDate today = LocalDate.now(clock);
        since.setValue(ReleaseNotes.defaultSince(today));
        since.setId("release-notes-since");
        until.setValue(today);
        until.setId("release-notes-until");
        since.addValueChangeListener(event -> {
            until.setMin(event.getValue());
            refresh();
        });
        until.addValueChangeListener(event -> {
            since.setMax(event.getValue());
            refresh();
        });
        Div period = new Div(since, until);
        period.addClassName("release-notes__period");
        summary.addClassName("release-notes__summary");
        preview.addClassName("release-notes__preview");

        Button copy = new Button(I18n.t("common.copyMarkdown"), VaadinIcon.COPY_O.create(), event -> copy());
        copy.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        copy.setId("copy-release-notes");
        add(period, summary, preview);
        getFooter().add(new Button(I18n.t("common.close"), event -> close()), copy);
        refresh();
    }

    private void refresh() {
        if (since.getValue() == null || until.getValue() == null || since.getValue().isAfter(until.getValue())) {
            return;
        }
        notes = service.between(board.id(), since.getValue(), until.getValue());
        summary.setText(notes.isEmpty() ? I18n.t("releaseNotes.empty")
                : I18n.t("releaseNotes.summary", notes.cardCount(), notes.sections().size(), notes.points()));
        preview.setContent(notes.toMarkdown());
    }

    private void copy() {
        getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", notes.toMarkdown()));
        Toasts.success(I18n.t("releaseNotes.copied"));
    }

    ReleaseNotes notes() {
        return notes;
    }
}
