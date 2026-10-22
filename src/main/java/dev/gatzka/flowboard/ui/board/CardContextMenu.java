package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Hr;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.MilestoneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.ui.i18n.I18n;
import dev.gatzka.flowboard.ui.i18n.Labels;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The right-click menu of a card: the everyday changes (lane, assignee, priority, watching,
 * archiving) without opening the editor. Every entry re-renders the board through the actions.
 */
final class CardContextMenu extends ContextMenu {

    CardContextMenu(CardComponent target, CardSnapshot card, LaneSnapshot lane, BoardActions actions) {
        super(target);
        addClassName("card-menu");
        addItem(I18n.t("cardMenu.open"), event -> actions.openCard(card)).addClassName("card-menu__open");

        MenuItem move = addItem(I18n.t("cardMenu.moveTo"));
        move.addClassName("card-menu__move");
        SubMenu lanes = move.getSubMenu();
        for (LaneSnapshot other : actions.lanes()) {
            MenuItem item = lanes.addItem(other.name(), event -> actions.moveCard(card.id(), other.id(), Integer.MAX_VALUE));
            item.setEnabled(other.id() != lane.id());
        }

        MenuItem assign = addItem(I18n.t("cardMenu.assignTo"));
        assign.addClassName("card-menu__assign");
        SubMenu people = assign.getSubMenu();
        Set<String> names = new LinkedHashSet<>();
        names.add(actions.currentUser());
        names.addAll(actions.assignees());
        for (String name : names) {
            MenuItem item = people.addItem(name, event -> actions.assignCard(card, name));
            item.setCheckable(true);
            item.setChecked(name.equals(card.assignee()));
        }
        MenuItem nobody = people.addItem(I18n.t("cardMenu.unassign"), event -> actions.assignCard(card, null));
        nobody.addClassName("card-menu__unassign");
        nobody.setEnabled(card.assignee() != null);

        MenuItem priority = addItem(I18n.t("cardMenu.priority"));
        priority.addClassName("card-menu__priority");
        for (Priority level : Priority.values()) {
            MenuItem item = priority.getSubMenu().addItem(Labels.priority(level), event -> actions.prioritiseCard(card, level));
            item.setCheckable(true);
            item.setChecked(level == card.priority());
        }

        List<MilestoneSnapshot> milestones = actions.milestones();
        if (!milestones.isEmpty()) {
            MenuItem milestone = addItem(I18n.t("cardMenu.milestone"));
            milestone.addClassName("card-menu__milestone");
            for (MilestoneSnapshot candidate : milestones) {
                MenuItem item = milestone.getSubMenu().addItem(candidate.name(), event -> actions.setMilestone(card, candidate.id()));
                item.setCheckable(true);
                item.setChecked(Long.valueOf(candidate.id()).equals(card.milestoneId()));
            }
            MenuItem none = milestone.getSubMenu().addItem(I18n.t("cardMenu.noMilestone"), event -> actions.setMilestone(card, null));
            none.setEnabled(card.milestoneId() != null);
        }

        boolean watching = actions.isWatching(card);
        addItem(I18n.t(watching ? "cardMenu.unwatch" : "cardMenu.watch"), event -> actions.toggleWatch(card)).addClassName("card-menu__watch");
        addItem(I18n.t("cardMenu.copyLink"), event -> actions.copyLink(card)).addClassName("card-menu__link");
        addComponent(new Hr());
        MenuItem archive = addItem(I18n.t("cardMenu.archive"), event -> actions.archiveCard(card));
        archive.addClassName("card-menu__archive");
        archive.addClassName("danger");
    }
}
