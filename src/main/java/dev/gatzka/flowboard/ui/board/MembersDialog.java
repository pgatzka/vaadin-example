package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.BoardAccess;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.security.AccountService;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.support.Toasts;

/** Who may see the board: everybody, or only its members. */
final class MembersDialog extends Dialog {

    private final BoardSnapshot board;
    private final AccessService access;
    private final AccountService accounts;
    private final UserSession session;
    private final Checkbox membersOnly = new Checkbox(I18n.t("members.onlyLabel"));
    private final Div list = new Div();
    private final ComboBox<String> newMember = new ComboBox<>();

    MembersDialog(BoardSnapshot board, AccessService access, AccountService accounts, UserSession session) {
        this.board = board;
        this.access = access;
        this.accounts = accounts;
        this.session = session;
        setHeaderTitle(I18n.t("members.title", board.name()));
        setWidth("min(520px, 95vw)");
        addClassName("members-dialog");

        Span hint = new Span(I18n.t("members.hint"));
        hint.addClassName("share-dialog__hint");

        membersOnly.setId("members-only");
        membersOnly.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                show(access.setMembersOnly(board.id(), event.getValue(), session.login(), session.getDisplayName()));
                Toasts.info(I18n.t(event.getValue() ? "members.nowRestricted" : "members.nowOpen"));
            }
        });

        list.addClassName("members__list");
        newMember.setPlaceholder(I18n.t("members.placeholder"));
        newMember.setItems(accounts.usernames());
        newMember.setAllowCustomValue(true);
        newMember.addCustomValueSetListener(event -> newMember.setValue(event.getDetail()));
        newMember.setWidthFull();
        newMember.setId("new-member");
        Button add = new Button(I18n.t("composer.add"), VaadinIcon.PLUS.create(), event -> add());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.setId("add-member");
        Div form = new Div(newMember, add);
        form.addClassName("members__form");

        add(hint, membersOnly, list, form);
        getFooter().add(new Button(I18n.t("common.close"), event -> close()));
        show(access.access(board.id()));
    }

    private void show(BoardAccess current) {
        membersOnly.setValue(current.membersOnly());
        list.removeAll();
        if (current.members().isEmpty()) {
            Span none = new Span(I18n.t("members.empty"));
            none.addClassName("insights__hint");
            list.add(none);
        }
        for (String login : current.members()) {
            String shown = accounts.displayNameOf(login);
            Avatar avatar = new Avatar(shown);
            avatar.setColorIndex(Participant.named(shown).colorIndex());
            avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
            Span name = new Span(shown);
            name.addClassName("members__name");
            Span handle = new Span("@" + login);
            handle.addClassName("members__login");
            Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> remove(login));
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            remove.setAriaLabel(I18n.t("files.removeAria", login));
            Div row = new Div(avatar, name, handle, remove);
            row.addClassName("members__row");
            list.add(row);
        }
    }

    private void add() {
        String login = newMember.getValue();
        if (login == null || login.isBlank()) {
            return;
        }
        try {
            show(access.addMember(board.id(), login, session.getDisplayName()));
            newMember.clear();
        } catch (IllegalArgumentException e) {
            Toasts.error(e.getMessage());
        }
    }

    private void remove(String login) {
        try {
            show(access.removeMember(board.id(), login, session.getDisplayName()));
        } catch (IllegalArgumentException e) {
            Toasts.error(e.getMessage());
        }
    }
}
