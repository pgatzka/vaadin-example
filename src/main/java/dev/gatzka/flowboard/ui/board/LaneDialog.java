package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import dev.gatzka.flowboard.domain.LaneRuleSnapshot;
import dev.gatzka.flowboard.domain.RuleAction;
import java.util.List;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.IntegerRangeValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import dev.gatzka.flowboard.domain.LaneDraft;
import java.util.function.Consumer;

/** Create or edit a lane: name, optional WIP limit and whether its cards count as done. */
final class LaneDialog extends Dialog {

    private final Binder<LaneDraft> binder = new Binder<>(LaneDraft.class);
    private final Consumer<LaneDraft> onSave;

    static LaneDialog forNewLane(Consumer<LaneDraft> onSave) {
        return new LaneDialog("New lane", "Add lane", null, onSave);
    }

    /** Callbacks for the automation section of an existing lane. */
    interface Automation {
        List<LaneRuleSnapshot> rules();

        void add(RuleAction action, String argument);

        void remove(LaneRuleSnapshot rule);
    }

    static LaneDialog forLane(LaneDraft current, Consumer<LaneDraft> onSave, Automation automation) {
        LaneDialog dialog = new LaneDialog("Edit lane", "Save", current, onSave);
        dialog.addAutomation(automation);
        return dialog;
    }

    private void addAutomation(Automation automation) {
        H3 heading = new H3("When a card enters this lane");
        heading.addClassName("dashboard__heading");
        Div list = new Div();
        list.addClassName("rules__list");
        Select<RuleAction> action = new Select<>();
        action.setLabel("Rule");
        action.setItems(RuleAction.values());
        action.setItemLabelGenerator(RuleAction::label);
        action.setValue(RuleAction.ASSIGN);
        action.setWidthFull();
        action.setId("rule-action");
        TextField argument = new TextField("Value");
        argument.setWidthFull();
        argument.setId("rule-argument");
        action.addValueChangeListener(event -> {
            argument.setVisible(event.getValue().needsArgument());
            argument.setPlaceholder(event.getValue().needsArgument() ? event.getValue().argumentHint() : "");
        });
        argument.setPlaceholder(RuleAction.ASSIGN.argumentHint());
        Button add = new Button("Add rule", VaadinIcon.PLUS.create(), event -> {
            try {
                automation.add(action.getValue(), argument.getValue());
                argument.clear();
                argument.setInvalid(false);
                showRules(list, automation);
            } catch (IllegalArgumentException e) {
                argument.setErrorMessage(e.getMessage());
                argument.setInvalid(true);
            }
        });
        add.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        add.setId("add-rule");
        Div form = new Div(action, argument, add);
        form.addClassName("rules__form");
        showRules(list, automation);
        add(heading, list, form);
    }

    private static void showRules(Div list, Automation automation) {
        list.removeAll();
        List<LaneRuleSnapshot> rules = automation.rules();
        if (rules.isEmpty()) {
            Span none = new Span("No rules yet. Rules run for cards that are added here or moved here.");
            none.addClassName("insights__hint");
            list.add(none);
        }
        for (LaneRuleSnapshot rule : rules) {
            Span text = new Span(rule.describe());
            text.addClassName("rules__text");
            Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> {
                automation.remove(rule);
                showRules(list, automation);
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            remove.setAriaLabel("Remove rule");
            Div row = new Div(VaadinIcon.BOLT.create(), text, remove);
            row.addClassName("rules__row");
            list.add(row);
        }
    }

    private LaneDialog(String heading, String saveLabel, LaneDraft current, Consumer<LaneDraft> onSave) {
        this.onSave = onSave;
        setHeaderTitle(heading);
        setWidth("min(460px, 95vw)");
        setCloseOnOutsideClick(false);

        TextField name = new TextField("Name");
        name.setAutofocus(true);
        name.setMaxLength(60);

        IntegerField wipLimit = new IntegerField("Work-in-progress limit");
        wipLimit.setMin(1);
        wipLimit.setMax(99);
        wipLimit.setStepButtonsVisible(true);
        wipLimit.setClearButtonVisible(true);
        wipLimit.setHelperText("Leave empty for no limit. The lane turns red when exceeded.");

        Checkbox done = new Checkbox("Cards in this lane count as completed");
        done.setHelperText("Completed cards are never reported as overdue.");

        binder.forField(name)
                .asRequired("Give the lane a name")
                .withValidator(new StringLengthValidator("Keep the name under 60 characters", 0, 60))
                .bind("name");
        binder.forField(wipLimit)
                .withValidator(new IntegerRangeValidator("Choose a limit between 1 and 99", 1, 99))
                .bind("wipLimit");
        binder.forField(done).bind("done");
        if (current != null) {
            binder.readRecord(current);
        }

        FormLayout form = new FormLayout(name, wipLimit, done);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        Button save = new Button(saveLabel, event -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER, KeyModifier.CONTROL);
        getFooter().add(new Button("Cancel", event -> close()), save);
    }

    private void save() {
        try {
            onSave.accept(binder.writeRecord());
            close();
        } catch (ValidationException invalid) {
            // Field level messages are already visible.
        }
    }
}
