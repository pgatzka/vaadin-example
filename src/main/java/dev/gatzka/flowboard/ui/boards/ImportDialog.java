package dev.gatzka.flowboard.ui.boards;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.exchange.BoardExchangeService;
import dev.gatzka.flowboard.exchange.BoardExport;
import dev.gatzka.flowboard.exchange.InvalidBoardFileException;
import java.util.function.Consumer;

/** Creates a board from an uploaded JSON export. */
final class ImportDialog extends Dialog {

    private final Span error = new Span();

    ImportDialog(BoardExchangeService exchange, String actor, Consumer<BoardSnapshot> onImported) {
        setHeaderTitle("Import a board");
        setWidth("min(460px, 95vw)");

        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            try {
                BoardExport export = exchange.parse(bytes);
                BoardSnapshot imported = exchange.importBoard(export, actor);
                close();
                onImported.accept(imported);
            } catch (InvalidBoardFileException invalid) {
                error.setText(invalid.getMessage());
                error.setVisible(true);
            }
        }));
        upload.setMaxFiles(1);
        upload.setAcceptedFileTypes("application/json", ".json");
        upload.setDropLabel(new Span("Drop a Flowboard or Trello .json file here"));

        error.addClassName("danger");
        error.setVisible(false);

        add(new Paragraph("Pick a file exported from Flowboard, or a board exported from Trello (Menu → Print and export → "
                + "Export as JSON). A new board is created; nothing existing is changed."),
                upload, error);
        getFooter().add(new Button("Cancel", event -> close()));
    }
}
