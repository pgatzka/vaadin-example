package dev.gatzka.flowboard.ui.boards;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.exchange.BoardExchangeService;
import dev.gatzka.flowboard.ui.i18n.I18n;
import java.io.ByteArrayInputStream;
import java.util.Locale;

/** Offers a board as a JSON download. The file is generated when the link is clicked. */
final class ExportDialog extends Dialog {

    ExportDialog(BoardSummary board, BoardExchangeService exchange) {
        setHeaderTitle(I18n.t("export.title", board.name()));
        setWidth("min(460px, 95vw)");

        String fileName = fileName(board.name());
        DownloadHandler handler = DownloadHandler.fromInputStream(event -> {
            byte[] bytes = exchange.toJson(exchange.export(board.id()));
            return new DownloadResponse(new ByteArrayInputStream(bytes), fileName, "application/json", bytes.length);
        }, fileName);

        Anchor download = new Anchor(handler, I18n.t("export.download", fileName));
        download.addClassName("export-link");
        download.getElement().setAttribute("download", true);

        add(new Paragraph(I18n.t("export.text")),
                download);
        getFooter().add(new Button(I18n.t("common.close"), event -> close()));
    }

    static String fileName(String boardName) {
        String slug = boardName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return (slug.isEmpty() ? "board" : slug) + ".flowboard.json";
    }
}
