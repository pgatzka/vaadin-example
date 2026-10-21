package dev.gatzka.flowboard.ui.board;

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

    private final ComboBox<BoardSummary> board = new ComboBox<>("Board");
    private final ComboBox<LaneSnapshot> lane = new ComboBox<>("Lane");
    private final RadioButtonGroup<String> mode = new RadioButtonGroup<>("What to do");

    MoveCardDialog(CardSnapshot card, BoardSnapshot current, CardEditorActions actions, Consumer<CardSnapshot> onDone) {
        setHeaderTitle("Move or copy \"" + card.title() + "\"");
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
        mode.setValue(MOVE);
        mode.setId("move-mode");
        Span hint = new Span("Moving to another board drops the card's dependencies, which are board-local. "
                + "Copying takes the fields, checklist and repetition; comments, files and history stay with the original.");
        hint.addClassName("share-dialog__hint");

        add(board, lane, mode, hint);
        Button confirm = new Button("Go", event -> {
            if (lane.getValue() == null) {
                Toasts.error("Pick a lane");
                return;
            }
            boolean copy = COPY.equals(mode.getValue());
            CardSnapshot result = actions.transferCard(card.id(), lane.getValue().id(), copy);
            Toasts.success((copy ? "Copied \"" : "Moved \"") + card.title() + "\" to " + board.getValue().name() + " · " + lane.getValue().name());
            close();
            onDone.accept(result);
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setId("move-confirm");
        getFooter().add(new Button("Cancel", event -> close()), confirm);
    }
}
