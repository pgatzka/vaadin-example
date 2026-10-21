package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.select.Select;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import java.util.List;
import java.util.function.Consumer;

/**
 * Confirms deleting a lane. When the lane holds cards the user chooses between moving them to
 * another lane (the default, if there is one) and deleting them along with the lane.
 */
final class DeleteLaneDialog extends Dialog {

    enum Fate {
        MOVE("Move them to another lane"),
        DELETE("Delete them too");

        private final String label;

        Fate(String label) {
            this.label = label;
        }
    }

    /**
     * @param onDelete receives the lane the cards should move to, or {@code null} to delete them
     */
    DeleteLaneDialog(LaneSnapshot lane, BoardSnapshot board, Consumer<LaneSnapshot> onDelete) {
        setHeaderTitle("Delete \"%s\"?".formatted(lane.name()));
        setWidth("min(460px, 95vw)");

        int cardCount = lane.cards().size();
        List<LaneSnapshot> otherLanes = board.lanes().stream().filter(other -> other.id() != lane.id()).toList();

        RadioButtonGroup<Fate> fate = new RadioButtonGroup<>("What happens to its %d card%s?".formatted(cardCount, cardCount == 1 ? "" : "s"));
        fate.setItems(Fate.values());
        fate.setItemLabelGenerator(choice -> choice.label);
        fate.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        fate.setItemEnabledProvider(choice -> choice == Fate.DELETE || !otherLanes.isEmpty());
        fate.setValue(otherLanes.isEmpty() ? Fate.DELETE : Fate.MOVE);

        Select<LaneSnapshot> target = new Select<>();
        target.setLabel("Move cards to");
        target.setItems(otherLanes);
        target.setItemLabelGenerator(LaneSnapshot::name);
        target.setWidthFull();
        if (!otherLanes.isEmpty()) {
            target.setValue(otherLanes.getFirst());
        }
        target.setEnabled(fate.getValue() == Fate.MOVE);
        fate.addValueChangeListener(event -> target.setEnabled(event.getValue() == Fate.MOVE));

        Div content = new Div();
        content.addClassName("delete-lane");
        if (cardCount == 0) {
            content.add(new Paragraph("The lane is empty and will be removed for everyone."));
        } else {
            content.add(fate, target);
        }
        add(content);

        Button delete = new Button("Delete lane", event -> {
            onDelete.accept(cardCount > 0 && fate.getValue() == Fate.MOVE ? target.getValue() : null);
            close();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        getFooter().add(new Button("Cancel", event -> close()), delete);
    }
}
