package dev.gatzka.flowboard.ui.assistant;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import dev.gatzka.flowboard.assistant.AssistantService;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.ui.support.Background;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** Paste notes, let the assistant extract cards, review them, create the ones you want. */
public final class NotesToCardsDialog extends Dialog {

    private final TextArea notes = new TextArea("Notes");
    private final Button extract = new Button("Extract cards");
    private final ProgressBar progress = new ProgressBar();
    private final Span status = new Span();
    private final CheckboxGroup<CardDraft> proposals = new CheckboxGroup<>("Proposed cards");
    private final Select<LaneSnapshot> lane = new Select<>();
    private final Button create = new Button("Create selected");
    private List<CardDraft> drafts = List.of();

    /**
     * @param onCreate receives the chosen drafts and the lane they go to
     */
    public NotesToCardsDialog(BoardSnapshot board, AssistantService assistant, Executor executor,
            BiConsumer<List<CardDraft>, LaneSnapshot> onCreate) {
        setHeaderTitle("Cards from notes");
        setWidth("min(680px, 95vw)");
        addClassName("assistant-dialog");

        notes.setPlaceholder("Paste meeting notes, an email, a brain dump… The assistant turns the actionable parts into cards.");
        notes.setMinHeight("10em");
        notes.setWidthFull();
        notes.setMaxLength(8000);

        extract.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        extract.addClickListener(event -> extract(board, assistant, executor));

        progress.setIndeterminate(true);
        progress.setVisible(false);
        status.addClassName("assistant-dialog__status");
        status.setVisible(false);

        proposals.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        proposals.setRenderer(new ComponentRenderer<>(NotesToCardsDialog::proposal));
        proposals.setVisible(false);

        lane.setLabel("Add to lane");
        lane.setItems(board.lanes());
        lane.setItemLabelGenerator(LaneSnapshot::name);
        if (!board.lanes().isEmpty()) {
            lane.setValue(board.lanes().getFirst());
        }
        lane.setVisible(false);

        add(notes, extract, progress, status, proposals, lane);

        create.setEnabled(false);
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(event -> {
            onCreate.accept(drafts.stream().filter(proposals.getSelectedItems()::contains).toList(), lane.getValue());
            close();
        });
        getFooter().add(new Button("Cancel", event -> close()), create);
    }

    private void extract(BoardSnapshot board, AssistantService assistant, Executor executor) {
        String text = notes.getValue().strip();
        if (text.isEmpty()) {
            notes.focus();
            return;
        }
        extract.setEnabled(false);
        progress.setVisible(true);
        status.setText("Reading your notes…");
        status.setVisible(true);
        UI ui = UI.getCurrent();
        Background.run(executor, ui, () -> assistant.cardsFromNotes(text, board), this::show, this::showError);
    }

    private void show(List<CardDraft> drafts) {
        this.drafts = drafts;
        extract.setEnabled(true);
        progress.setVisible(false);
        if (drafts.isEmpty()) {
            status.setText("No actionable items found in these notes.");
            return;
        }
        status.setText(drafts.size() + (drafts.size() == 1 ? " card proposed." : " cards proposed.") + " Untick what you do not want.");
        proposals.setItems(drafts);
        proposals.setValue(Set.copyOf(drafts));
        proposals.setVisible(true);
        lane.setVisible(true);
        create.setEnabled(lane.getValue() != null);
    }

    private void showError(RuntimeException failure) {
        extract.setEnabled(true);
        progress.setVisible(false);
        status.setText(failure.getMessage());
        status.addClassName("danger");
    }

    private static Div proposal(CardDraft draft) {
        Span title = new Span(draft.title());
        title.addClassName("proposal__title");
        Div item = new Div(title);
        item.addClassName("proposal");
        if (draft.description() != null) {
            Span description = new Span(draft.description());
            description.addClassName("proposal__description");
            item.add(description);
        }
        Span meta = new Span(draft.priority().label() + (draft.assignee() == null ? "" : " · " + draft.assignee()));
        meta.addClassName("proposal__meta");
        item.add(meta);
        return item;
    }
}
