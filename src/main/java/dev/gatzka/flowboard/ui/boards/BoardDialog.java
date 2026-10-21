package dev.gatzka.flowboard.ui.boards;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import dev.gatzka.flowboard.domain.BoardDraft;
import java.util.function.Consumer;

/** Create or edit a board's name and description. */
public final class BoardDialog extends Dialog {

    private final Binder<BoardDraft> binder = new Binder<>(BoardDraft.class);
    private final Consumer<BoardDraft> onSave;

    public static BoardDialog forNewBoard(Consumer<BoardDraft> onSave) {
        return new BoardDialog("New board", "Create board", null, onSave);
    }

    public static BoardDialog forBoard(BoardDraft current, Consumer<BoardDraft> onSave) {
        return new BoardDialog("Edit board", "Save", current, onSave);
    }

    private BoardDialog(String heading, String saveLabel, BoardDraft current, Consumer<BoardDraft> onSave) {
        this.onSave = onSave;
        setHeaderTitle(heading);
        setWidth("min(520px, 95vw)");
        setCloseOnOutsideClick(false);

        TextField name = new TextField("Name");
        name.setAutofocus(true);
        name.setMaxLength(80);

        TextArea description = new TextArea("Description");
        description.setMaxLength(500);
        description.setPlaceholder("What is this board for?");
        description.setMinHeight("6em");

        binder.forField(name)
                .asRequired("Give the board a name")
                .withValidator(new StringLengthValidator("Keep the name under 80 characters", 0, 80))
                .bind("name");
        binder.forField(description)
                .withNullRepresentation("")
                .bind("description");
        if (current != null) {
            binder.readRecord(current);
        }

        FormLayout form = new FormLayout(name, description);
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
