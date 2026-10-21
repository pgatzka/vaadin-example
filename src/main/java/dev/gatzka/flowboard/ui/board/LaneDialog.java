package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
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

    static LaneDialog forLane(LaneDraft current, Consumer<LaneDraft> onSave) {
        return new LaneDialog("Edit lane", "Save", current, onSave);
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
