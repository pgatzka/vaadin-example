package dev.gatzka.flowboard.ui.assistant;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.progressbar.ProgressBar;
import dev.gatzka.flowboard.assistant.AssistantService;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.ui.support.Background;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.util.List;
import java.util.concurrent.Executor;

/** Shows an assistant-written standup summary of the board, ready to copy into chat. */
public final class StandupDialog extends Dialog {

    private final ProgressBar progress = new ProgressBar();
    private final Span status = new Span("Reading the board…");
    private final Markdown summary = new Markdown();
    private final Button copy = new Button("Copy as Markdown", VaadinIcon.COPY_O.create());
    private String markdown = "";

    public StandupDialog(BoardSnapshot board, List<ActivityEntry> recentActivity, AssistantService assistant, Executor executor) {
        setHeaderTitle("Standup for " + board.name());
        setWidth("min(640px, 95vw)");
        addClassName("assistant-dialog");

        progress.setIndeterminate(true);
        status.addClassName("assistant-dialog__status");
        summary.addClassName("markdown-preview");
        summary.setVisible(false);
        add(status, progress, summary);

        copy.setEnabled(false);
        copy.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        copy.addClickListener(event -> {
            getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", markdown));
            Toasts.success("Summary copied");
        });
        getFooter().add(new Button("Close", event -> close()), copy);

        UI ui = UI.getCurrent();
        Background.run(executor, ui, () -> assistant.standupSummary(board, recentActivity), this::show, this::showError);
    }

    private void show(String text) {
        markdown = text;
        progress.setVisible(false);
        status.setVisible(false);
        summary.setContent(text);
        summary.setVisible(true);
        copy.setEnabled(true);
    }

    private void showError(RuntimeException failure) {
        progress.setVisible(false);
        status.setText(failure.getMessage());
        status.addClassName("danger");
    }

    String markdown() {
        return markdown;
    }
}
