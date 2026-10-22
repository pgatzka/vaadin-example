package dev.gatzka.flowboard.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import dev.gatzka.flowboard.domain.CardLocation;
import dev.gatzka.flowboard.service.WorkService;
import dev.gatzka.flowboard.ui.work.CardLocationRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Command palette and search in one: actions and boards match first, then cards from every board.
 * Results open the card on its board or run the action.
 */
final class SearchDialog extends Dialog {

    static final int MAX_COMMANDS = 10;

    private final WorkService workService;
    private final Supplier<List<Command>> commands;
    private final Clock clock;
    private final Predicate<CardLocation> visible;
    private final TextField query = new TextField();
    private final Div actions = new Div();
    private final Div results = new Div();
    private final Span hint = new Span("Type to search titles, descriptions, assignees and tags on all boards.");

    SearchDialog(WorkService workService, Supplier<List<Command>> commands, Predicate<CardLocation> visible, Clock clock) {
        this.visible = visible;
        this.workService = workService;
        this.commands = commands;
        this.clock = clock;
        addClassName("search-dialog");
        setWidth("min(640px, 95vw)");

        query.setPlaceholder("Search boards and cards, or type a command");
        query.setPrefixComponent(VaadinIcon.SEARCH.create());
        query.setClearButtonVisible(true);
        query.setAutofocus(true);
        query.setWidthFull();
        query.setValueChangeMode(ValueChangeMode.LAZY);
        query.addValueChangeListener(event -> search(event.getValue()));

        actions.addClassName("palette");
        results.addClassName("work-list");
        results.addClickListener(event -> close());
        hint.addClassName("search-dialog__hint");
        add(query, actions, hint, results);
    }

    @Override
    public void open() {
        query.clear();
        search("");
        super.open();
    }

    private void search(String text) {
        actions.removeAll();
        commands.get().stream().filter(command -> command.matches(text)).limit(MAX_COMMANDS).forEach(command -> actions.add(button(command)));

        List<CardLocation> hits = workService.search(text).stream().filter(visible).toList();
        results.removeAll();
        LocalDate today = LocalDate.now(clock);
        hits.forEach(hit -> results.add(new CardLocationRow(hit, today)));
        if (text.isBlank()) {
            hint.setText("Type to search titles, descriptions, assignees and tags on all boards.");
        } else if (hits.isEmpty()) {
            hint.setText("Nothing matches \"" + text + "\".");
        } else {
            hint.setText(hits.size() == WorkService.SEARCH_LIMIT ? "Showing the first " + hits.size() + " matches." : hits.size() + (hits.size() == 1 ? " match" : " matches"));
        }
    }

    private Button button(Command command) {
        Button button = new Button(command.label(), command.icon().create(), event -> {
            close();
            command.action().run();
        });
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.addClassName("palette__command");
        return button;
    }
}
