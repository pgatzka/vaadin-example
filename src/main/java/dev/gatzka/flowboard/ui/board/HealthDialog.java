package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.Health;
import dev.gatzka.flowboard.domain.Health.Finding;
import dev.gatzka.flowboard.ui.i18n.I18n;
import java.util.List;
import java.util.Locale;

/** The health findings of a board, each with the one action that gets you to the cards concerned. */
final class HealthDialog extends Dialog {

    interface Actions {
        void selectCards(List<Long> cardIds);

        void openMilestones();

        void openInsights();
    }

    HealthDialog(Health health, Actions actions) {
        addClassName("health-dialog");
        setHeaderTitle(I18n.t("health.title"));
        setWidth("min(560px, 95vw)");
        Badge score = new Badge(I18n.t("health.score", health.score()));
        score.addClassName("health__score");
        score.getElement().setAttribute("data-state", state(health.score()));
        getHeader().add(score);

        Span hint = new Span(I18n.t("health.hint"));
        hint.addClassName("share-dialog__hint");
        Div list = new Div();
        list.addClassName("health__list");
        if (health.isClear()) {
            Span clear = new Span(VaadinIcon.CHECK_CIRCLE.create(), new Span(I18n.t("health.clear")));
            clear.addClassName("health__clear");
            list.add(clear);
        }
        health.findings().forEach(finding -> list.add(row(finding, actions)));
        add(hint, list);
        getFooter().add(new Button(I18n.t("common.close"), event -> close()));
    }

    static String state(int score) {
        return score >= 85 ? "ok" : score >= 60 ? "maybe" : "warn";
    }

    private Div row(Finding finding, Actions actions) {
        Span dot = new Span();
        dot.addClassName("health__dot");
        Span text = new Span(message(finding));
        text.addClassName("health__text");
        Div row = new Div(dot, text);
        row.addClassName("health__row");
        row.getElement().setAttribute("data-severity", finding.severity().name().toLowerCase(Locale.ROOT));
        row.getElement().setAttribute("data-kind", finding.kind().name());
        action(finding, actions).ifPresent(row::add);
        return row;
    }

    private java.util.Optional<Button> action(Finding finding, Actions actions) {
        Button button;
        if (finding.hasCards()) {
            button = new Button(I18n.t("health.select"), event -> {
                actions.selectCards(finding.cardIds());
                close();
            });
            button.addClassName("health__select");
        } else if (finding.kind() == Health.Kind.FORECAST_LATE) {
            button = new Button(I18n.t("health.insights"), event -> {
                actions.openInsights();
                close();
            });
        } else if (finding.kind() == Health.Kind.MILESTONE_AT_RISK || finding.kind() == Health.Kind.MILESTONE_LATE) {
            button = new Button(I18n.t("health.milestones"), event -> {
                actions.openMilestones();
                close();
            });
        } else {
            return java.util.Optional.empty();
        }
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.addClassName("health__action");
        return java.util.Optional.of(button);
    }

    static String message(Finding finding) {
        String date = finding.date() == null ? "" : I18n.weekdayAndDate(finding.date());
        return I18n.t("health." + finding.kind().name(), finding.count(), finding.subject() == null ? "" : finding.subject(), date);
    }
}
