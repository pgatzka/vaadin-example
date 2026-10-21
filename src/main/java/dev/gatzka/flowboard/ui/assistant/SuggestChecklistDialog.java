package dev.gatzka.flowboard.ui.assistant;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;
import dev.gatzka.flowboard.assistant.AssistantService;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.ui.support.Background;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Asks the assistant for checklist steps and lets the user pick which ones to add. */
public final class SuggestChecklistDialog extends Dialog {

    private final CheckboxGroup<String> steps = new CheckboxGroup<>();
    private final ProgressBar progress = new ProgressBar();
    private final Span status = new Span("Thinking about \"%s\"…");
    private final Button add = new Button("Add selected");
    private List<String> suggestions = List.of();

    public SuggestChecklistDialog(CardSnapshot card, BoardSnapshot board, AssistantService assistant, Executor executor,
            Consumer<List<String>> onAdd) {
        setHeaderTitle("Suggested checklist");
        setWidth("min(520px, 95vw)");
        addClassName("assistant-dialog");

        progress.setIndeterminate(true);
        status.setText("Thinking about \"" + card.title() + "\"…");
        status.addClassName("assistant-dialog__status");
        steps.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        steps.setVisible(false);
        add(status, progress, steps);

        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.setEnabled(false);
        add.addClickListener(event -> {
            onAdd.accept(suggestions.stream().filter(steps.getSelectedItems()::contains).toList());
            close();
        });
        getFooter().add(new Button("Cancel", event -> close()), add);

        UI ui = UI.getCurrent();
        Background.run(executor, ui, () -> assistant.suggestChecklist(card, board), this::showSuggestions, this::showError);
    }

    private void showSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
        progress.setVisible(false);
        status.setText("Tick the steps worth adding.");
        steps.setItems(suggestions);
        steps.setValue(java.util.Set.copyOf(suggestions));
        steps.setVisible(true);
        add.setEnabled(true);
    }

    private void showError(RuntimeException failure) {
        progress.setVisible(false);
        status.setText(failure.getMessage());
        status.addClassName("danger");
    }
}
