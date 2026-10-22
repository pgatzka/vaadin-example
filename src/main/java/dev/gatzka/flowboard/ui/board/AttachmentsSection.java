package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;
import dev.gatzka.flowboard.domain.AttachmentSnapshot;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.service.AttachmentService;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.function.Consumer;

/** Files on a card: upload, download, remove. */
final class AttachmentsSection extends Div {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private final Div list = new Div();
    private final Span empty = new Span(I18n.t("files.empty"));
    private final CardEditorActions actions;
    private final Consumer<CardSnapshot> onChanged;
    private CardSnapshot card;

    AttachmentsSection(CardSnapshot card, CardEditorActions actions, Consumer<CardSnapshot> onChanged) {
        this.card = card;
        this.actions = actions;
        this.onChanged = onChanged;
        addClassName("attachments");
        list.addClassName("attachments__list");
        empty.addClassName("attachments__empty");

        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) ->
                update(actions.attach(this.card.id(), metadata.fileName(), metadata.contentType(), bytes))));
        upload.setMaxFileSize((int) AttachmentService.MAX_SIZE);
        upload.setDropLabel(new Span(I18n.t("files.drop")));
        upload.addFileRejectedListener(event -> Toasts.error(event.getErrorMessage()));
        upload.addClassName("attachments__upload");

        add(list, empty, upload);
        show(card);
    }

    void show(CardSnapshot card) {
        this.card = card;
        list.removeAll();
        card.attachments().forEach(attachment -> list.add(row(attachment)));
        empty.setVisible(card.attachments().isEmpty());
    }

    private Div row(AttachmentSnapshot attachment) {
        Icon icon = (attachment.isImage() ? VaadinIcon.PICTURE : VaadinIcon.FILE_O).create();
        icon.addClassName("attachments__icon");

        DownloadHandler handler = DownloadHandler.fromInputStream(event -> actions.attachmentContent(attachment.id())
                .map(bytes -> new DownloadResponse(new ByteArrayInputStream(bytes), attachment.fileName(), attachment.contentType(), bytes.length))
                .orElseGet(() -> DownloadResponse.error(404)), attachment.fileName());
        Anchor name = new Anchor(handler, attachment.fileName());
        name.addClassName("attachments__name");

        Span meta = new Span(attachment.humanSize() + " \u00b7 " + attachment.uploadedBy() + " \u00b7 "
                + WHEN.withLocale(I18n.locale()).format(attachment.uploadedAt().atZone(ZoneId.systemDefault())));
        meta.addClassName("attachments__meta");

        Div text = new Div(name, meta);
        text.addClassName("attachments__text");
        if (attachment.isImage()) {
            boolean cover = card.isCover(attachment.id());
            Button useAsCover = new Button(I18n.t(cover ? "files.cover" : "files.useAsCover"), event -> update(actions.setCover(card.id(), cover ? null : attachment.id())));
            useAsCover.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            useAsCover.addClassName("attachments__cover");
            if (cover) {
                useAsCover.setIcon(VaadinIcon.CHECK.create());
                useAsCover.addClassName("attachments__cover--on");
                useAsCover.setTooltipText(I18n.t("files.coverTooltip"));
            } else {
                useAsCover.setTooltipText(I18n.t("files.useAsCoverTooltip"));
            }
            text.add(useAsCover);
        }
        Button remove = new Button(VaadinIcon.TRASH.create(), event -> update(actions.removeAttachment(attachment.id())));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        remove.setAriaLabel(I18n.t("files.removeAria", attachment.fileName()));

        Div row = new Div(icon, text, remove);
        row.addClassName("attachments__row");
        return row;
    }

    private void update(CardSnapshot refreshed) {
        onChanged.accept(refreshed);
    }
}
