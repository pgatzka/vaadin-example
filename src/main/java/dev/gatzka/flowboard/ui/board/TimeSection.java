package dev.gatzka.flowboard.ui.board;

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
    private final Span empty = new Span("Nothing logged yet.");
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
        Div top = new Div(total, timer);
        top.addClassName("time__top");

        minutes.setPlaceholder("Minutes");
        minutes.setMin(1);
        minutes.setMax(TimeService.MAX_MINUTES);
        minutes.setStepButtonsVisible(true);
        minutes.setStep(15);
        minutes.setWidth("9rem");
        minutes.addClassName("time__minutes");
        note.setPlaceholder("What did you do? (optional)");
        note.setMaxLength(TimeService.MAX_NOTE_LENGTH);
        note.addClassName("time__note");
        Button log = new Button("Log", VaadinIcon.PLUS.create(), event -> log());
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
        total.setText(card.hasWork() ? TimeService.humanize(card.workedMinutes()) + " logged" : "No time logged");
        Optional<TimerSnapshot> running = actions.runningTimer().filter(t -> t.cardId() == card.id());
        if (running.isPresent()) {
            long elapsed = Math.max(1, running.get().elapsed(now.get()).toMinutes());
            timer.setText("Stop timer · " + elapsed + " min");
            timer.setIcon(VaadinIcon.STOP.create());
            timer.addClassName("time__timer--running");
        } else {
            timer.setText("Start timer");
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
        Span amount = new Span(TimeService.humanize(entry.minutes()));
        amount.addClassName("time__amount");
        Span text = new Span(entry.note() == null ? "" : entry.note());
        text.addClassName("time__text");
        Span when = new Span(Durations.ago(entry.loggedAt(), now.get()));
        when.addClassName("time__when");
        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> update(actions.removeWorkEntry(entry.id())));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        remove.setAriaLabel("Remove entry");
        Div row = new Div(who, amount, text, when, remove);
        row.addClassName("time__row");
        return row;
    }

    private void toggleTimer() {
        boolean runningHere = actions.runningTimer().filter(t -> t.cardId() == card.id()).isPresent();
        if (runningHere) {
            actions.stopTimer().ifPresent(this::update);
            Toasts.success("Timer stopped and time logged");
        } else {
            actions.startTimer(card.id());
            Toasts.info("Timer running on \"" + card.title() + "\"");
            show(card);
        }
    }

    private void log() {
        Integer value = minutes.getValue();
        if (value == null || value < 1) {
            minutes.setErrorMessage("How many minutes?");
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
}
