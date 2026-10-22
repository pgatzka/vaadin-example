package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.MilestoneDraft;
import dev.gatzka.flowboard.domain.MilestoneProgress;
import dev.gatzka.flowboard.domain.MilestoneSnapshot;
import dev.gatzka.flowboard.service.MilestoneService;
import dev.gatzka.flowboard.ui.i18n.I18n;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/** The milestones of a board: progress, the chance of making each date, and a form to add or change one. */
final class MilestonesDialog extends Dialog {

    private final BoardSnapshot board;
    private final MilestoneService milestones;
    private final Supplier<String> actor;
    private final Clock clock;
    private final Div list = new Div();
    private final TextField name = new TextField(I18n.t("milestones.name"));
    private final DatePicker target = new DatePicker(I18n.t("milestones.target"));
    private final Button submit = new Button();
    private final Button cancelEdit = new Button(I18n.t("milestones.cancelEdit"), event -> stopEditing());
    private MilestoneSnapshot editing;

    MilestonesDialog(BoardSnapshot board, MilestoneService milestones, Supplier<String> actor, Clock clock) {
        this.board = board;
        this.milestones = milestones;
        this.actor = actor;
        this.clock = clock;
        setHeaderTitle(I18n.t("milestones.title", board.name()));
        setWidth("min(640px, 95vw)");
        addClassName("milestones-dialog");

        Span hint = new Span(I18n.t("milestones.hint"));
        hint.addClassName("share-dialog__hint");
        list.addClassName("milestones__list");

        name.setPlaceholder(I18n.t("milestones.namePlaceholder"));
        name.setMaxLength(MilestoneDraft.MAX_NAME);
        name.setClearButtonVisible(true);
        name.setWidthFull();
        name.setId("milestone-name");
        target.setClearButtonVisible(true);
        target.setId("milestone-target");
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setIcon(VaadinIcon.FLAG_O.create());
        submit.setId("save-milestone");
        submit.addClickListener(event -> submit());
        cancelEdit.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Div buttons = new Div(submit, cancelEdit);
        buttons.addClassName("milestones__form-buttons");
        Div form = new Div(name, target, buttons);
        form.addClassName("milestones__form");

        add(hint, list, form);
        getFooter().add(new Button(I18n.t("common.close"), event -> close()));
        stopEditing();
        refresh();
    }

    void refresh() {
        list.removeAll();
        var progress = milestones.progress(board.id());
        if (progress.isEmpty()) {
            Span empty = new Span(I18n.t("milestones.empty"));
            empty.addClassName("insights__hint");
            list.add(empty);
            return;
        }
        LocalDate today = LocalDate.now(clock);
        progress.forEach(entry -> list.add(row(entry, today)));
    }

    private Div row(MilestoneProgress progress, LocalDate today) {
        MilestoneSnapshot milestone = progress.milestone();
        Span title = new Span(milestone.name());
        title.addClassName("milestones__name");
        Span date = new Span(milestone.hasTarget() ? I18n.weekdayAndDate(milestone.targetDate()) : I18n.t("milestones.noTarget"));
        date.addClassName("milestones__date");
        Div head = new Div(title, date);
        head.addClassName("milestones__head");

        Div bar = bar(progress);
        Div foot = new Div();
        foot.addClassName("milestones__foot");
        if (progress.isComplete()) {
            foot.add(badge(I18n.t("milestones.complete"), "ok"));
        } else if (progress.total() == 0) {
            foot.add(new Span(I18n.t("milestones.noCards")));
        } else if (progress.isLate(today)) {
            foot.add(badge(I18n.t("milestones.late"), "warn"));
            foot.add(new Span(I18n.t("milestones.daysOver", ChronoUnit.DAYS.between(milestone.targetDate(), today))));
        } else {
            progress.chance().ifPresentOrElse(chance -> {
                long percent = Math.round(chance * 100);
                Badge odds = badge(I18n.t("milestones.chance", percent), percent >= 85 ? "ok" : percent >= 50 ? "maybe" : "warn");
                odds.getElement().setAttribute("title", I18n.t("milestones.chanceTitle", percent));
                foot.add(odds);
            }, () -> foot.add(new Span(I18n.t(milestone.hasTarget() ? "milestones.noHistory" : "milestones.noTarget"))));
            if (progress.forecast().hasForecast()) {
                foot.add(new Span(I18n.t("milestones.likelyBy", I18n.weekdayAndDate(progress.forecast().outcome(85).date()))));
            }
            if (milestone.hasTarget()) {
                foot.add(new Span(I18n.t("milestones.daysLeft", ChronoUnit.DAYS.between(today, milestone.targetDate()))));
            }
        }

        Button edit = new Button(VaadinIcon.PENCIL.create(), event -> startEditing(milestone));
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        edit.setAriaLabel(I18n.t("milestones.editAria"));
        edit.addClassName("edit-milestone");
        Button remove = new Button(VaadinIcon.TRASH.create(), event -> {
            milestones.delete(milestone.id(), actor.get());
            Toasts.info(I18n.t("milestones.removed"));
            if (editing != null && editing.id() == milestone.id()) {
                stopEditing();
            }
            refresh();
        });
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        remove.setAriaLabel(I18n.t("milestones.removeAria"));
        remove.addClassName("remove-milestone");
        Div actions = new Div(edit, remove);
        actions.addClassName("milestones__actions");

        Div row = new Div(head, actions, bar, foot);
        row.addClassName("milestones__row");
        row.getElement().setAttribute("data-milestone-id", Long.toString(milestone.id()));
        row.getElement().setAttribute("data-state", progress.isComplete() ? "complete" : progress.isLate(today) ? "late" : "open");
        return row;
    }

    private static Div bar(MilestoneProgress progress) {
        Div track = new Div();
        track.addClassName("insights__bar-track");
        Div fill = new Div();
        fill.addClassName("insights__bar-fill");
        fill.getStyle().set("width", Math.round(progress.ratio() * 100) + "%");
        track.add(fill);
        Span count = new Span(I18n.t("milestones.progress", progress.done(), progress.total()));
        count.addClassName("insights__bar-value");
        count.addClassName("milestones__count");
        Div bar = new Div(track, count);
        bar.addClassName("insights__bar");
        bar.addClassName("milestones__bar");
        if (progress.isComplete()) {
            bar.getElement().setAttribute("data-state", "done");
        }
        return bar;
    }

    private static Badge badge(String text, String state) {
        Badge badge = new Badge(text);
        badge.addThemeVariants(BadgeVariant.SMALL);
        badge.addClassName("milestones__chance");
        badge.getElement().setAttribute("data-state", state);
        return badge;
    }

    private void startEditing(MilestoneSnapshot milestone) {
        editing = milestone;
        name.setValue(milestone.name());
        target.setValue(milestone.targetDate());
        submit.setText(I18n.t("milestones.save"));
        cancelEdit.setVisible(true);
        name.focus();
    }

    private void stopEditing() {
        editing = null;
        name.clear();
        target.clear();
        name.setInvalid(false);
        submit.setText(I18n.t("milestones.add"));
        cancelEdit.setVisible(false);
    }

    private void submit() {
        MilestoneDraft draft;
        try {
            draft = new MilestoneDraft(name.getValue(), target.getValue());
        } catch (IllegalArgumentException invalid) {
            name.setErrorMessage(invalid.getMessage());
            name.setInvalid(true);
            return;
        }
        if (editing == null) {
            milestones.create(board.id(), draft, actor.get());
            Toasts.success(I18n.t("milestones.added"));
        } else {
            milestones.update(editing.id(), draft, actor.get());
            Toasts.success(I18n.t("milestones.saved"));
        }
        stopEditing();
        refresh();
    }
}
