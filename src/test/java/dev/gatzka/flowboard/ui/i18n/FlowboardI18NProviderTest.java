package dev.gatzka.flowboard.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlowboardI18NProviderTest {

    private final FlowboardI18NProvider provider = new FlowboardI18NProvider();

    @Test
    void translatesIntoEnglishAndGermanWithParameters() {
        assertThat(provider.getTranslation("nav.myWork", Locale.ENGLISH)).isEqualTo("My work");
        assertThat(provider.getTranslation("nav.myWork", Locale.GERMAN)).isEqualTo("Meine Aufgaben");
        assertThat(provider.getTranslation("nav.myWork", Locale.forLanguageTag("de-AT"))).as("any German variant").isEqualTo("Meine Aufgaben");
        assertThat(provider.getTranslation("nav.myWork", Locale.FRENCH)).as("unknown languages fall back to English").isEqualTo("My work");
        assertThat(provider.getTranslation("selection.count", Locale.ENGLISH, 1)).isEqualTo("1 card selected");
        assertThat(provider.getTranslation("selection.count", Locale.ENGLISH, 3)).isEqualTo("3 cards selected");
        assertThat(provider.getTranslation("selection.count", Locale.GERMAN, 3)).isEqualTo("3 Karten ausgewählt");
        assertThat(provider.getTranslation("board.movedTo", Locale.ENGLISH, "Fix it", "Done")).isEqualTo("Moved \"Fix it\" to Done");
        assertThat(provider.getTranslation("editor.templateTooltip", Locale.ENGLISH)).as("apostrophes survive without parameters")
                .isEqualTo("Reuse this card's fields and checklist for new cards");
        assertThat(provider.getTranslation("heatmap.changes", Locale.ENGLISH)).as("leading spaces are kept").isEqualTo(" changes");
        assertThat(provider.getTranslation("no.such.key", Locale.ENGLISH)).isEqualTo("no.such.key");
        assertThat(provider.getProvidedLocales()).containsExactly(Locale.ENGLISH, Locale.GERMAN);
        assertThat(FlowboardI18NProvider.supported(null)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void everyEnglishKeyHasAGermanTranslation() {
        Set<String> english = java.util.ResourceBundle.getBundle("i18n.translations", Locale.ROOT).keySet();
        Set<String> german = java.util.ResourceBundle.getBundle("i18n.translations", Locale.GERMAN).keySet();
        assertThat(german).containsAll(english);
        assertThat(english).containsAll(german);
        assertThat(english.size()).isGreaterThan(500);
    }

    @Test
    void helpersWorkWithoutAUi() {
        assertThat(I18n.t("common.save")).isEqualTo("Save");
        assertThat(I18n.count(1, "unit.card", "unit.cards")).isEqualTo("1 card");
        assertThat(I18n.count(2, "unit.card", "unit.cards")).isEqualTo("2 cards");
        assertThat(I18n.locale()).isEqualTo(Locale.ENGLISH);
        assertThat(Labels.priority(dev.gatzka.flowboard.domain.Priority.HIGH)).isEqualTo("High");
        assertThat(Labels.rule(dev.gatzka.flowboard.domain.RuleAction.DUE_IN_DAYS, "3")).isEqualTo("Due in 3 days");
        assertThat(Labels.rule(dev.gatzka.flowboard.domain.RuleAction.ADD_TAG, "qa")).isEqualTo("Add the tag qa");
    }
}
