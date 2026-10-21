package dev.gatzka.flowboard.ui;

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

/** Search across every board. Results open the card on its board. */
final class SearchDialog extends Dialog {

    private final WorkService workService;
    private final Clock clock;
    private final TextField query = new TextField();
    private final Div results = new Div();
    private final Span hint = new Span("Type to search titles, descriptions, assignees and tags on all boards.");

    SearchDialog(WorkService workService, Clock clock) {
        this.workService = workService;
        this.clock = clock;
        addClassName("search-dialog");
        setWidth("min(640px, 95vw)");

        query.setPlaceholder("Search all boards");
        query.setPrefixComponent(VaadinIcon.SEARCH.create());
        query.setClearButtonVisible(true);
        query.setAutofocus(true);
        query.setWidthFull();
        query.setValueChangeMode(ValueChangeMode.LAZY);
        query.addValueChangeListener(event -> search(event.getValue()));

        results.addClassName("work-list");
        results.addClickListener(event -> close());
        hint.addClassName("search-dialog__hint");
        add(query, hint, results);
    }

    @Override
    public void open() {
        query.clear();
        search("");
        super.open();
    }

    private void search(String text) {
        List<CardLocation> hits = workService.search(text);
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
}
