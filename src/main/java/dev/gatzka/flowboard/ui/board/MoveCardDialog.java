package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.ui.support.Toasts;
import java.util.List;
import java.util.function.Consumer;

/** Picks a board and lane to move or copy a card to. */
final class MoveCardDialog extends Dialog {

    private static final String MOVE = "Move";
    private static final String COPY = "Copy";

    private final ComboBox<BoardSummary> board = new ComboBox<>(I18n.t("move.board"));
    private final ComboBox<LaneSnapshot> lane = new ComboBox<>(I18n.t("move.lane"));
    private final RadioButtonGroup<String> mode = new RadioButtonGroup<>(I18n.t("move.what"));

    MoveCardDialog(CardSnapshot card, BoardSnapshot current, CardEditorActions actions, Consumer<CardSnapshot> onDone) {
        setHeaderTitle(I18n.t("move.title", card.title()));
        setWidth("min(460px, 95vw)");
        addClassName("move-card-dialog");

        List<BoardSummary> boards = actions.boardsForTransfer();
        board.setItems(boards);
        board.setItemLabelGenerator(BoardSummary::name);
        board.setWidthFull();
        board.setId("move-board");
        lane.setItemLabelGenerator(LaneSnapshot::name);
        lane.setWidthFull();
        lane.setId("move-lane");
        board.addValueChangeListener(event -> {
            List<LaneSnapshot> lanes = event.getValue() == null ? List.of()
                    : actions.boardFor(event.getValue().id()).map(BoardSnapshot::lanes).orElse(List.of());
            lane.setItems(lanes);
            lane.setValue(lanes.isEmpty() ? null : lanes.getFirst());
        });
        boards.stream().filter(summary -> summary.id() == current.id()).findFirst().ifPresent(board::setValue);

        mode.setItems(MOVE, COPY);
        mode.setItemLabelGenerator(value -> I18n.t(MOVE.equals(value) ? "move.move" : "move.copy"));
        mode.setValue(MOVE);
        mode.setId("move-mode");
        Span hint = new Span(I18n.t("move.hint"));
        hint.addClassName("share-dialog__hint");

        add(board, lane, mode, hint);
        Button confirm = new Button(I18n.t("move.go"), event -> {
            if (lane.getValue() == null) {
                Toasts.error(I18n.t("move.pickLane"));
                return;
            }
            boolean copy = COPY.equals(mode.getValue());
            CardSnapshot result = actions.transferCard(card.id(), lane.getValue().id(), copy);
            Toasts.success(I18n.t(copy ? "move.copied" : "move.moved", card.title(), board.getValue().name() + " \u00b7 " + lane.getValue().name()));
            close();
            onDone.accept(result);
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setId("move-confirm");
        getFooter().add(new Button(I18n.t("common.cancel"), event -> close()), confirm);
    }
}
