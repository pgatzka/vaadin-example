package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.TimerSnapshot;
import dev.gatzka.flowboard.domain.WorkEntrySnapshot;
import dev.gatzka.flowboard.service.TimeService;
import dev.gatzka.flowboard.ui.support.Durations;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Time on a card: a start/stop timer, manual entries and the log of who spent what. */
final class TimeSection extends Div {

    private final CardEditorActions actions;
    private final Consumer<CardSnapshot> onChanged;
    private final Supplier<Instant> now;
    private final Span total = new Span();
    private final Button timer = new Button();
    private final IntegerField minutes = new IntegerField();
    private final TextField note = new TextField();
    private final Div list = new Div();
    private final Button focus = new Button();
    private final Span empty = new Span(I18n.t("time.empty"));
    private CardSnapshot card;

    TimeSection(CardSnapshot card, CardEditorActions actions, Consumer<CardSnapshot> onChanged, Supplier<Instant> now) {
        this.card = card;
        this.actions = actions;
        this.onChanged = onChanged;
        this.now = now;
        addClassName("time");

        total.addClassName("time__total");
        timer.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        timer.addClassName("time__timer");
        timer.addClickListener(event -> toggleTimer());
        focus.setId("focus-start");
        focus.setText(I18n.t("time.focus", TimeService.FOCUS_MINUTES));
        focus.setIcon(VaadinIcon.BULLSEYE.create());
        focus.addThemeVariants(ButtonVariant.LUMO_SMALL);
        focus.setTooltipText(I18n.t("time.focusTooltip", TimeService.FOCUS_MINUTES));
        focus.addClickListener(event -> {
            actions.startFocus(card.id(), TimeService.FOCUS_MINUTES);
            Toasts.info(I18n.t("time.focusStarted", card.title(), TimeService.FOCUS_MINUTES));
            show(card);
        });
        Div top = new Div(total, focus, timer);
        top.addClassName("time__top");

        minutes.setPlaceholder(I18n.t("time.minutes"));
        minutes.setMin(1);
        minutes.setMax(TimeService.MAX_MINUTES);
        minutes.setStepButtonsVisible(true);
        minutes.setStep(15);
        minutes.setWidth("9rem");
        minutes.addClassName("time__minutes");
        note.setPlaceholder(I18n.t("time.notePlaceholder"));
        note.setMaxLength(TimeService.MAX_NOTE_LENGTH);
        note.addClassName("time__note");
        Button log = new Button(I18n.t("time.log"), VaadinIcon.PLUS.create(), event -> log());
        log.addThemeVariants(ButtonVariant.LUMO_SMALL);
        log.setId("log-time");
        Div form = new Div(minutes, note, log);
        form.addClassName("time__form");

        list.addClassName("time__list");
        empty.addClassName("attachments__empty");
        add(top, form, list, empty);
        show(card);
    }

    void show(CardSnapshot card) {
        this.card = card;
        total.setText(card.hasWork() ? I18n.t("time.logged", humanize(card.workedMinutes())) : I18n.t("time.none"));
        Optional<TimerSnapshot> running = actions.runningTimer().filter(t -> t.cardId() == card.id());
        focus.setVisible(running.isEmpty());
        if (running.isPresent() && running.get().isFocusSession()) {
            long left = Math.max(0, running.get().remaining(now.get()).toMinutes());
            timer.setText(I18n.t("time.stopFocus", left));
            timer.setIcon(VaadinIcon.STOP.create());
            timer.addClassName("time__timer--running");
        } else if (running.isPresent()) {
            long elapsed = Math.max(1, running.get().elapsed(now.get()).toMinutes());
            timer.setText(I18n.t("time.stopTimer", elapsed));
            timer.setIcon(VaadinIcon.STOP.create());
            timer.addClassName("time__timer--running");
        } else {
            timer.setText(I18n.t("time.startTimer"));
            timer.setIcon(VaadinIcon.PLAY.create());
            timer.removeClassName("time__timer--running");
        }
        list.removeAll();
        var entries = actions.workEntries(card.id());
        entries.forEach(entry -> list.add(row(entry)));
        empty.setVisible(entries.isEmpty());
    }

    private Div row(WorkEntrySnapshot entry) {
        Span who = new Span(entry.person());
        who.addClassName("time__who");
        Span amount = new Span(humanize(entry.minutes()));
        amount.addClassName("time__amount");
        Span text = new Span(entry.note() == null ? "" : entry.note());
        text.addClassName("time__text");
        Span when = new Span(Durations.ago(entry.loggedAt(), now.get()));
        when.addClassName("time__when");
        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> update(actions.removeWorkEntry(entry.id())));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        remove.setAriaLabel(I18n.t("time.removeEntry"));
        Div row = new Div(who, amount, text, when, remove);
        row.addClassName("time__row");
        return row;
    }

    private void toggleTimer() {
        boolean runningHere = actions.runningTimer().filter(t -> t.cardId() == card.id()).isPresent();
        if (runningHere) {
            actions.stopTimer().ifPresent(this::update);
            Toasts.success(I18n.t("time.timerStopped"));
        } else {
            actions.startTimer(card.id());
            Toasts.info(I18n.t("time.timerRunning", card.title()));
            show(card);
        }
    }

    private void log() {
        Integer value = minutes.getValue();
        if (value == null || value < 1) {
            minutes.setErrorMessage(I18n.t("time.howMany"));
            minutes.setInvalid(true);
            return;
        }
        try {
            update(actions.logTime(card.id(), value, note.getValue()));
        } catch (IllegalArgumentException e) {
            minutes.setErrorMessage(e.getMessage());
            minutes.setInvalid(true);
            return;
        }
        minutes.clear();
        minutes.setInvalid(false);
        note.clear();
    }

    private void update(CardSnapshot refreshed) {
        show(refreshed);
        onChanged.accept(refreshed);
    }

    /** "45 min", "2 h" or "2 h 30 min" in the current language. */
    static String humanize(int minutes) {
        if (minutes < 60) {
            return I18n.t("duration.minutes", Integer.toString(minutes));
        }
        int rest = minutes % 60;
        String hours = I18n.t("duration.hours", Integer.toString(minutes / 60));
        return rest == 0 ? hours : hours + " " + I18n.t("duration.minutes", Integer.toString(rest));
    }
}
