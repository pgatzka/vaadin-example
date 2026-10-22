package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.ui.i18n.I18n;
import dev.gatzka.flowboard.ui.i18n.Labels;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import java.util.Collection;

/** Floats above the board while cards are selected and applies one change to all of them. */
final class SelectionBar extends Div {

    interface Actions {
        void moveSelected(LaneSnapshot lane);

        void assignSelected(String assignee);

        void prioritiseSelected(Priority priority);

        void tagSelected(String tag);

        void archiveSelected();

        void clearSelection();
    }

    private final Span count = new Span();
    private final Select<LaneSnapshot> lane = new Select<>();
    private final ComboBox<String> assignee = new ComboBox<>();
    private final Select<Priority> priority = new Select<>();
    private final ComboBox<String> tag = new ComboBox<>();

    SelectionBar(Actions actions) {
        addClassName("selection-bar");
        setVisible(false);
        count.addClassName("selection-bar__count");

        lane.setPlaceholder(I18n.t("selection.moveTo"));
        lane.setItemLabelGenerator(LaneSnapshot::name);
        lane.addValueChangeListener(event -> {
            if (event.getValue() != null && event.isFromClient()) {
                actions.moveSelected(event.getValue());
                lane.clear();
            }
        });
        lane.addClassName("selection-bar__lane");

        assignee.setPlaceholder(I18n.t("selection.assignTo"));
        assignee.setAllowCustomValue(true);
        assignee.addCustomValueSetListener(event -> actions.assignSelected(event.getDetail()));
        assignee.addValueChangeListener(event -> {
            if (event.getValue() != null && event.isFromClient()) {
                actions.assignSelected(event.getValue());
                assignee.clear();
            }
        });
        assignee.addClassName("selection-bar__assignee");

        priority.setPlaceholder(I18n.t("selection.priority"));
        priority.setItems(Priority.values());
        priority.setItemLabelGenerator(Labels::priority);
        priority.addValueChangeListener(event -> {
            if (event.getValue() != null && event.isFromClient()) {
                actions.prioritiseSelected(event.getValue());
                priority.clear();
            }
        });
        priority.addClassName("selection-bar__priority");

        tag.setPlaceholder(I18n.t("selection.addTag"));
        tag.setAllowCustomValue(true);
        tag.addCustomValueSetListener(event -> actions.tagSelected(event.getDetail()));
        tag.addValueChangeListener(event -> {
            if (event.getValue() != null && event.isFromClient()) {
                actions.tagSelected(event.getValue());
                tag.clear();
            }
        });
        tag.addClassName("selection-bar__tag");

        Button archive = new Button(I18n.t("selection.archive"), VaadinIcon.ARCHIVE.create(), event -> actions.archiveSelected());
        archive.addThemeVariants(ButtonVariant.LUMO_SMALL);
        archive.setId("archive-selected");
        Button clear = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> actions.clearSelection());
        clear.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        clear.setAriaLabel(I18n.t("selection.clear"));
        clear.setId("clear-selection");

        add(count, lane, assignee, priority, tag, archive, clear);
    }

    /** Shows the bar for the given number of cards, with the board's lanes, names and tags as choices. */
    void show(int selected, BoardSnapshot board, Collection<String> assignees, Collection<String> tags) {
        setVisible(selected > 0);
        count.setText(I18n.t("selection.count", selected));
        lane.setItems(board.lanes());
        assignee.setItems(assignees);
        tag.setItems(tags);
    }
}
