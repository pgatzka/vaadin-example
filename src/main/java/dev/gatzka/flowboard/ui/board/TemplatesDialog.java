package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;
import dev.gatzka.flowboard.ui.i18n.Labels;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardTemplateDraft;
import dev.gatzka.flowboard.domain.CardTemplateSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.CardTemplateService;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** Lists the board's card templates and lets the user add or replace one. */
final class TemplatesDialog extends Dialog {

    private final BoardSnapshot board;
    private final CardTemplateService templates;
    private final Supplier<String> actor;
    private final Runnable onChanged;
    private final Div list = new Div();
    private final TextField name = new TextField(I18n.t("templates.name"));
    private final TextField title = new TextField(I18n.t("composer.placeholder"));
    private final TextArea description = new TextArea(I18n.t("common.description"));
    private final Select<Priority> priority = new Select<>();
    private final TextField assignee = new TextField(I18n.t("filter.assignee"));
    private final MultiSelectComboBox<String> tags = new MultiSelectComboBox<>(I18n.t("editor.tags"));
    private final IntegerField estimate = new IntegerField(I18n.t("card.estimate"));
    private final TextArea checklist = new TextArea(I18n.t("templates.checklist"));

    TemplatesDialog(BoardSnapshot board, CardTemplateService templates, Supplier<String> actor, Runnable onChanged) {
        this.board = board;
        this.templates = templates;
        this.actor = actor;
        this.onChanged = onChanged;
        setHeaderTitle(I18n.t("templates.title", board.name()));
        setWidth("min(680px, 95vw)");
        addClassName("templates-dialog");

        Span hint = new Span(I18n.t("templates.hint"));
        hint.addClassName("share-dialog__hint");
        list.addClassName("templates__list");

        name.setMaxLength(CardTemplateDraft.MAX_NAME_LENGTH);
        name.setRequired(true);
        name.setId("template-name");
        title.setMaxLength(120);
        title.setPlaceholder(I18n.t("templates.titlePlaceholder"));
        description.setMaxLength(4000);
        description.setMinHeight("5em");
        priority.setLabel(I18n.t("filter.priority"));
        priority.setItems(Priority.values());
        priority.setItemLabelGenerator(Labels::priority);
        priority.setValue(Priority.MEDIUM);
        assignee.setMaxLength(60);
        tags.setItems(board.tags());
        tags.setAllowCustomValue(true);
        tags.addCustomValueSetListener(event -> {
            String value = event.getDetail().strip();
            if (!value.isEmpty()) {
                java.util.Set<String> selected = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                selected.addAll(tags.getValue());
                selected.add(value);
                java.util.List<String> items = new java.util.ArrayList<>(board.tags());
                selected.stream().filter(tag -> items.stream().noneMatch(tag::equalsIgnoreCase)).forEach(items::add);
                tags.setItems(items);
                tags.setValue(selected);
            }
        });
        estimate.setMin(0);
        estimate.setMax(CardDraft.MAX_ESTIMATE);
        estimate.setStepButtonsVisible(true);
        estimate.setPlaceholder(I18n.t("editor.pointsPlaceholder"));
        checklist.setMaxLength(2000);
        checklist.setMinHeight("5em");
        checklist.setPlaceholder(I18n.t("templates.checklistPlaceholder"));

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("480px", 2));
        form.add(name, title);
        form.add(description, 2);
        form.add(priority, estimate, assignee, tags);
        form.add(checklist, 2);

        Button save = new Button(I18n.t("templates.save"), VaadinIcon.CHECK.create(), event -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setId("save-template");

        H3 newHeading = new H3(I18n.t("templates.new"));
        newHeading.addClassName("dashboard__heading");
        add(hint, list, newHeading, form, save);
        getFooter().add(new Button(I18n.t("common.close"), event -> close()));
        refresh();
    }

    private void refresh() {
        list.removeAll();
        List<CardTemplateSnapshot> all = templates.forBoard(board.id());
        if (all.isEmpty()) {
            Span empty = new Span(I18n.t("templates.empty"));
            empty.addClassName("insights__hint");
            list.add(empty);
        }
        all.forEach(template -> list.add(row(template)));
    }

    private Div row(CardTemplateSnapshot template) {
        CardTemplateDraft draft = template.draft();
        Span label = new Span(template.name());
        label.addClassName("templates__name");
        Div meta = new Div();
        meta.addClassName("templates__meta");
        meta.add(badge(Labels.priority(draft.priority())));
        if (draft.estimate() != null) {
            meta.add(badge(I18n.t("unit.points", draft.estimate())));
        }
        draft.tags().forEach(tag -> meta.add(badge(tag)));
        if (!draft.checklist().isEmpty()) {
            meta.add(badge(I18n.t("templates.steps", draft.checklist().size())));
        }
        if (draft.assignee() != null) {
            meta.add(badge("→ " + draft.assignee()));
        }
        Button edit = new Button(I18n.t("common.edit"), event -> load(draft));
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Button remove = new Button(VaadinIcon.TRASH.create(), event -> {
            templates.delete(template.id(), actor.get());
            Toasts.info(I18n.t("templates.removed"));
            refresh();
            onChanged.run();
        });
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        remove.setAriaLabel(I18n.t("templates.removeAria", template.name()));
        Div row = new Div(label, meta, edit, remove);
        row.addClassName("templates__row");
        return row;
    }

    private static Badge badge(String text) {
        Badge badge = new Badge(text);
        badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
        return badge;
    }

    private void load(CardTemplateDraft draft) {
        name.setValue(draft.name());
        title.setValue(draft.title() == null ? "" : draft.title());
        description.setValue(draft.description() == null ? "" : draft.description());
        priority.setValue(draft.priority());
        assignee.setValue(draft.assignee() == null ? "" : draft.assignee());
        tags.setValue(draft.tags());
        estimate.setValue(draft.estimate());
        checklist.setValue(String.join("\n", draft.checklist()));
        name.focus();
    }

    private void save() {
        try {
            CardTemplateDraft draft = new CardTemplateDraft(name.getValue(), title.getValue(), description.getValue(),
                    priority.getValue(), assignee.getValue(), tags.getValue(), estimate.getValue(),
                    Arrays.asList(checklist.getValue().split("\n")));
            templates.save(board.id(), draft, actor.get());
        } catch (IllegalArgumentException e) {
            name.setErrorMessage(e.getMessage());
            name.setInvalid(true);
            return;
        }
        name.clear();
        name.setInvalid(false);
        title.clear();
        description.clear();
        priority.setValue(Priority.MEDIUM);
        assignee.clear();
        tags.clear();
        estimate.clear();
        checklist.clear();
        Toasts.success(I18n.t("templates.saved"));
        refresh();
        onChanged.run();
    }
}
