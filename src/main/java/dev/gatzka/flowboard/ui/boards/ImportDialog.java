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
import dev.gatzka.flowboard.ui.i18n.I18n;
import java.util.function.Consumer;

/** Creates a board from an uploaded JSON export. */
final class ImportDialog extends Dialog {

    private final Span error = new Span();

    ImportDialog(BoardExchangeService exchange, String actor, Consumer<BoardSnapshot> onImported) {
        setHeaderTitle(I18n.t("import.title"));
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
        upload.setDropLabel(new Span(I18n.t("import.drop")));

        error.addClassName("danger");
        error.setVisible(false);

        add(new Paragraph(I18n.t("import.text")),
                upload, error);
        getFooter().add(new Button(I18n.t("common.cancel"), event -> close()));
    }
}
