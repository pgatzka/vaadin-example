package dev.gatzka.flowboard.ui.i18n;

import com.vaadin.flow.i18n.I18NProvider;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Texts of the user interface in English and German, from {@code i18n/translations*.properties}. Any locale that is
 * not German gets English. Placeholders follow {@link MessageFormat}; a text without parameters is returned as is, so
 * apostrophes in it need no escaping.
 */
@Component
public class FlowboardI18NProvider implements I18NProvider {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale GERMAN = Locale.GERMAN;
    private static final List<Locale> LOCALES = List.of(ENGLISH, GERMAN);
    private static final Logger LOG = LoggerFactory.getLogger(FlowboardI18NProvider.class);
    private static final String BUNDLE = "i18n.translations";

    private final ResourceBundle english = ResourceBundle.getBundle(BUNDLE, Locale.ROOT);
    private final ResourceBundle german = ResourceBundle.getBundle(BUNDLE, GERMAN);

    @Override
    public List<Locale> getProvidedLocales() {
        return LOCALES;
    }

    /** The provided locale that fits the given one: German for any German variant, English otherwise. */
    public static Locale supported(Locale locale) {
        return locale != null && GERMAN.getLanguage().equals(locale.getLanguage()) ? GERMAN : ENGLISH;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        ResourceBundle bundle = supported(locale) == GERMAN ? german : english;
        String text;
        try {
            text = bundle.getString(key);
        } catch (MissingResourceException missing) {
            if (bundle != english && english.containsKey(key)) {
                text = english.getString(key);
            } else {
                LOG.warn("No translation for {}", key);
                return key;
            }
        }
        return params == null || params.length == 0 ? text : new MessageFormat(text, supported(locale)).format(params);
    }
}
