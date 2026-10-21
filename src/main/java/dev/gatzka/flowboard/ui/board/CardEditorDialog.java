package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.validator.StringLengthValidator;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Create or edit a card. Assignees and tags already used on the board are offered as suggestions. */
final class CardEditorDialog extends Dialog {

    private final Binder<CardDraft> binder = new Binder<>(CardDraft.class);
    private final TextField title = new TextField("Title");
    private final TextArea description = new TextArea();
    private final Markdown preview = new Markdown();
    private final Select<Priority> priority = new Select<>();
    private final DatePicker dueDate = new DatePicker("Due date");
    private final ComboBox<String> assignee = new ComboBox<>("Assignee");
    private final MultiSelectComboBox<String> tags = new MultiSelectComboBox<>("Tags");
    private final List<String> assigneeOptions;
    private final List<String> tagOptions;
    private final Consumer<CardDraft> onSave;
    private final BoardSnapshot board;
    private Long cardId;
    private ChecklistSection checklistSection;
    private CommentsSection commentsSection;
    private HistorySection historySection;
    private Tab commentsTab;
    private CardEditorActions actions;

    static CardEditorDialog forNewCard(LaneSnapshot lane, BoardSnapshot board, String initialTitle,
            Consumer<CardDraft> onSave) {
        CardEditorDialog dialog = new CardEditorDialog("New card in " + lane.name(), "Add card", board, onSave);
        dialog.priority.setValue(Priority.MEDIUM);
        if (initialTitle != null && !initialTitle.isBlank()) {
            dialog.title.setValue(initialTitle);
        }
        return dialog;
    }

    static CardEditorDialog forCard(CardSnapshot card, BoardSnapshot board, CardEditorActions actions,
            Consumer<CardDraft> onSave, Runnable onDelete) {
        CardEditorDialog dialog = new CardEditorDialog("Edit card", "Save", board, onSave);
        dialog.binder.readRecord(card.toDraft());
        dialog.addCardSections(card, actions);
        dialog.addDeleteButton(card, onDelete);
        return dialog;
    }

    /** The id of the card being edited, or empty for a new card. */
    Optional<Long> cardId() {
        return Optional.ofNullable(cardId);
    }

    /**
     * Updates checklist, comments and history from a fresh snapshot, e.g. after somebody else
     * changed the card. The form fields are left alone so nothing typed is lost.
     */
    void refresh(CardSnapshot card) {
        if (cardId == null || card.id() != cardId) {
            return;
        }
        checklistSection.show(card);
        commentsSection.show(card);
        commentsTab.setLabel(commentsLabel(card));
        historySection.show(actions.history(card.id()));
    }

    private void addCardSections(CardSnapshot card, CardEditorActions actions) {
        this.cardId = card.id();
        this.actions = actions;
        Button copyLink = new Button(VaadinIcon.LINK.create(), event -> copyLink(card));
        copyLink.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        copyLink.setAriaLabel("Copy link to this card");
        copyLink.setTooltipText("Copy link");
        getHeader().add(copyLink);
        checklistSection = new ChecklistSection(card, actions, this::refresh);
        commentsSection = new CommentsSection(card, actions, this::refresh);
        historySection = new HistorySection(actions.history(card.id()));

        TabSheet tabs = new TabSheet();
        tabs.addClassName("card-tabs");
        commentsTab = new Tab(commentsLabel(card));
        tabs.add(commentsTab, commentsSection);
        tabs.add("History", historySection);
        tabs.setWidthFull();
        add(checklistSection, tabs);
    }

    private void copyLink(CardSnapshot card) {
        String path = BoardView.pathTo(board.id(), card.id());
        getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText(location.origin + '/' + $0)", path));
        Toasts.success("Link copied");
    }

    private static String commentsLabel(CardSnapshot card) {
        return card.hasComments() ? "Comments (" + card.comments().size() + ")" : "Comments";
    }

    private CardEditorDialog(String heading, String saveLabel, BoardSnapshot board, Consumer<CardDraft> onSave) {
        this.onSave = onSave;
        this.board = board;
        this.assigneeOptions = new ArrayList<>(board.assignees());
        this.tagOptions = new ArrayList<>(board.tags());

        setHeaderTitle(heading);
        setWidth("min(640px, 95vw)");
        setDraggable(true);
        setCloseOnOutsideClick(false);

        configureFields();
        bindFields();
        add(form());

        Button save = new Button(saveLabel, event -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER, KeyModifier.CONTROL);
        save.setTooltipText("Ctrl + Enter");
        getFooter().add(new Button("Cancel", event -> close()), save);
    }

    private void configureFields() {
        title.setAutofocus(true);
        title.setMaxLength(120);
        title.setPlaceholder("What needs to be done?");

        description.setPlaceholder("Add details. Markdown is supported.");
        description.setMinHeight("9em");
        description.setWidthFull();
        preview.addClassName("markdown-preview");

        priority.setLabel("Priority");
        priority.setItems(Priority.values());
        priority.setItemLabelGenerator(Priority::label);
        priority.setRenderer(new ComponentRenderer<>(CardEditorDialog::priorityOption));

        dueDate.setClearButtonVisible(true);

        assignee.setItems(assigneeOptions);
        assignee.setAllowCustomValue(true);
        assignee.setClearButtonVisible(true);
        assignee.setPlaceholder("Type a name");
        assignee.addCustomValueSetListener(event -> {
            String value = event.getDetail().strip();
            if (!value.isEmpty()) {
                addOption(assigneeOptions, value);
                assignee.setItems(assigneeOptions);
                assignee.setValue(value);
            }
        });

        tags.setItems(tagOptions);
        tags.setAllowCustomValue(true);
        tags.setClearButtonVisible(true);
        tags.setPlaceholder("Add tags");
        tags.setSelectedItemsOnTop(true);
        tags.addCustomValueSetListener(event -> {
            String value = event.getDetail().strip();
            if (!value.isEmpty()) {
                addOption(tagOptions, value);
                tags.setItems(tagOptions);
                Set<String> selected = new LinkedHashSet<>(tags.getValue());
                selected.add(value);
                tags.setValue(selected);
            }
        });
    }

    private void bindFields() {
        binder.forField(title)
                .asRequired("A card needs a title")
                .withValidator(new StringLengthValidator("Keep the title under 120 characters", 0, 120))
                .bind("title");
        binder.forField(description)
                .withNullRepresentation("")
                .withValidator(new StringLengthValidator("Keep the description under 4000 characters", 0, 4000))
                .bind("description");
        binder.forField(priority).asRequired("Pick a priority").bind("priority");
        binder.forField(dueDate).bind("dueDate");
        binder.forField(assignee).bind("assignee");
        binder.forField(tags).bind("tags");
    }

    private FormLayout form() {
        TabSheet descriptionTabs = new TabSheet();
        descriptionTabs.add("Write", description);
        descriptionTabs.add("Preview", preview);
        descriptionTabs.addSelectedChangeListener(event -> preview.setContent(
                description.isEmpty() ? "*Nothing to preview yet.*" : description.getValue()));
        descriptionTabs.setWidthFull();

        Span descriptionLabel = new Span("Description");
        descriptionLabel.addClassName("field-label");
        Div descriptionSection = new Div(descriptionLabel, descriptionTabs);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("480px", 2));
        form.add(title, 2);
        form.add(descriptionSection, 2);
        form.add(priority, dueDate, assignee);
        form.add(tags, 2);
        return form;
    }

    private void addDeleteButton(CardSnapshot card, Runnable onDelete) {
        Button delete = new Button("Delete", event -> {
            ConfirmDialog confirm = new ConfirmDialog(
                    "Delete \"%s\"?".formatted(card.title()),
                    "The card is removed from the board for everyone.",
                    "Delete",
                    confirmed -> {
                        onDelete.run();
                        close();
                    });
            confirm.setCancelable(true);
            confirm.setConfirmButtonTheme("error primary");
            confirm.open();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delete.addClassName("dialog-footer__start");
        getFooter().addComponentAsFirst(delete);
    }

    private void save() {
        try {
            onSave.accept(binder.writeRecord());
            close();
        } catch (ValidationException invalid) {
            // Field level messages are already visible.
        }
    }

    private static Span priorityOption(Priority priority) {
        Span option = new Span(priority.label());
        option.addClassName("priority-option");
        option.getElement().setAttribute("data-priority", priority.name().toLowerCase(Locale.ROOT));
        return option;
    }

    private static void addOption(List<String> options, String value) {
        if (options.stream().noneMatch(option -> option.equalsIgnoreCase(value))) {
            options.add(value);
            options.sort(String.CASE_INSENSITIVE_ORDER);
        }
    }
}
