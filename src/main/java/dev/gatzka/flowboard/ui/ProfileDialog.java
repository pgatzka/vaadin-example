package dev.gatzka.flowboard.ui;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import dev.gatzka.flowboard.service.Participant;

/** Lets the visitor pick the name shown to others in presence avatars and the activity feed. */
final class ProfileDialog extends Dialog {

    ProfileDialog(UserSession session, Runnable onSaved) {
        setHeaderTitle(I18n.t("profile.title"));
        setWidth("min(420px, 95vw)");

        Avatar preview = new Avatar();
        preview.addThemeVariants(AvatarVariant.LUMO_XLARGE);

        TextField name = new TextField(I18n.t("profile.displayName"));
        name.setValue(session.getDisplayName());
        name.setMaxLength(UserSession.MAX_NAME_LENGTH);
        name.setRequired(true);
        name.setAutofocus(true);
        name.setWidthFull();
        name.setValueChangeMode(ValueChangeMode.EAGER);
        name.setHelperText(I18n.t("profile.helper"));
        name.addValueChangeListener(event -> showPreview(preview, event.getValue()));
        showPreview(preview, name.getValue());

        HorizontalLayout content = new HorizontalLayout(preview, name);
        content.setAlignItems(FlexComponent.Alignment.CENTER);
        content.setWidthFull();
        content.setFlexGrow(1, name);
        add(content);

        Button save = new Button(I18n.t("common.save"), event -> {
            String value = name.getValue().strip();
            if (value.isEmpty()) {
                name.setErrorMessage(I18n.t("views.nameRequired"));
                name.setInvalid(true);
                return;
            }
            session.setDisplayName(value);
            onSaved.run();
            close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        getFooter().add(new Button(I18n.t("common.cancel"), event -> close()), save);
    }

    private static void showPreview(Avatar preview, String name) {
        String shown = name == null || name.isBlank() ? "?" : name.strip();
        preview.setName(shown);
        preview.setColorIndex(Participant.named(shown).colorIndex());
    }
}
