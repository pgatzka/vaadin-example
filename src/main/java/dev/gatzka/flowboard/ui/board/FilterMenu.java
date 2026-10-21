package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.combobox.ComboBox;
import dev.gatzka.flowboard.domain.CardFilter;
import dev.gatzka.flowboard.domain.Priority;
import java.util.function.Consumer;

/** Toolbar button with a popover holding the structured card filters. */
final class FilterMenu extends Div {

    private final Badge count = new Badge();
    private final ComboBox<Priority> priority = new ComboBox<>("Priority");
    private final Checkbox overdue = new Checkbox("Overdue only");
    private final Checkbox mine = new Checkbox("Assigned to me");
    private final Checkbox unassigned = new Checkbox("Unassigned");
    private final ComboBox<String> tag = new ComboBox<>("Tag");
    private final ComboBox<String> assignee = new ComboBox<>("Assignee");
    private final Button clear = new Button("Clear filters");
    private final Consumer<CardFilter> onChange;
    private CardFilter filter = CardFilter.none();
    private boolean updating;

    FilterMenu(Consumer<CardFilter> onChange) {
        this.onChange = onChange;
        addClassName("filter-menu");

        Button toggle = new Button("Filters", VaadinIcon.FILTER.create());
        toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        toggle.setSuffixComponent(count);
        count.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.FILLED);
        count.setVisible(false);

        priority.setItems(Priority.values());
        priority.setItemLabelGenerator(Priority::label);
        priority.setPlaceholder("Any");
        priority.setClearButtonVisible(true);
        priority.setWidthFull();
        priority.addValueChangeListener(event -> update(filter.withPriority(event.getValue())));
        overdue.addValueChangeListener(event -> update(filter.withOverdueOnly(event.getValue())));
        mine.addValueChangeListener(event -> update(filter.withMineOnly(event.getValue())));
        unassigned.addValueChangeListener(event -> update(filter.withUnassignedOnly(event.getValue())));
        tag.setPlaceholder("Any");
        tag.setClearButtonVisible(true);
        tag.setWidthFull();
        tag.setAllowCustomValue(true);
        tag.addCustomValueSetListener(event -> update(filter.withTag(event.getDetail())));
        tag.addValueChangeListener(event -> update(filter.withTag(event.getValue())));
        assignee.setPlaceholder("Anybody");
        assignee.setClearButtonVisible(true);
        assignee.setWidthFull();
        assignee.setAllowCustomValue(true);
        assignee.addCustomValueSetListener(event -> update(filter.withAssignee(event.getDetail())));
        assignee.addValueChangeListener(event -> update(filter.withAssignee(event.getValue())));

        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        clear.addClickListener(event -> update(filter.withoutToggles()));

        Div content = new Div(priority, tag, assignee, overdue, mine, unassigned, clear);
        content.addClassName("filter-menu__content");

        Popover popover = new Popover(content);
        popover.setTarget(toggle);
        popover.setPosition(PopoverPosition.BOTTOM_END);
        popover.setOpenOnClick(true);
        popover.setAriaLabel("Card filters");

        add(toggle, popover);
        show(filter);
    }

    CardFilter filter() {
        return filter;
    }

    /** Replaces the text part of the filter, keeping the toggles. */
    void setQuery(String text) {
        update(filter.withText(text));
    }

    /** Applies a whole filter, e.g. from a saved view or the URL. */
    void setFilter(CardFilter changed) {
        update(changed);
    }

    /** The tags and names offered in the drop-downs; a value outside the list can still be typed. */
    void setOptions(java.util.Collection<String> tags, java.util.Collection<String> assignees) {
        updating = true;
        try {
            tag.setItems(withCurrent(tags, filter.tag()));
            tag.setValue(filter.tag());
            assignee.setItems(withCurrent(assignees, filter.assignee()));
            assignee.setValue(filter.assignee());
        } finally {
            updating = false;
        }
    }

    private static java.util.List<String> withCurrent(java.util.Collection<String> options, String current) {
        java.util.List<String> items = new java.util.ArrayList<>(options);
        if (current != null && items.stream().noneMatch(current::equalsIgnoreCase)) {
            items.add(current);
        }
        return items;
    }

    private void update(CardFilter changed) {
        if (updating || changed.equals(filter)) {
            return;
        }
        filter = changed;
        show(filter);
        onChange.accept(filter);
    }

    private void show(CardFilter current) {
        updating = true;
        try {
            priority.setValue(current.priority());
            overdue.setValue(current.overdueOnly());
            mine.setValue(current.mineOnly());
            unassigned.setValue(current.unassignedOnly());
            if (current.tag() != null && tag.getListDataView().getItems().noneMatch(current.tag()::equalsIgnoreCase)) {
                tag.setItems(withCurrent(tag.getListDataView().getItems().toList(), current.tag()));
            }
            tag.setValue(current.tag());
            if (current.assignee() != null && assignee.getListDataView().getItems().noneMatch(current.assignee()::equalsIgnoreCase)) {
                assignee.setItems(withCurrent(assignee.getListDataView().getItems().toList(), current.assignee()));
            }
            assignee.setValue(current.assignee());
            int toggles = current.toggleCount();
            count.setText(Integer.toString(toggles));
            count.setVisible(toggles > 0);
            clear.setEnabled(toggles > 0);
        } finally {
            updating = false;
        }
    }
}
