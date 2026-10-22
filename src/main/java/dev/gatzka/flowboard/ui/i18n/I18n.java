package dev.gatzka.flowboard.ui.i18n;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinService;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shorthand for translated texts from code that is not a component. */
public final class I18n {

    private I18n() {
    }

    /** The locale of the current UI, English when there is none. */
    public static Locale locale() {
        UI ui = UI.getCurrent();
        return FlowboardI18NProvider.supported(ui == null ? null : ui.getLocale());
    }

    private static final I18NProvider FALLBACK = new FlowboardI18NProvider();

    public static String t(String key, Object... params) {
        VaadinService service = VaadinService.getCurrent();
        I18NProvider provider = service == null ? null : service.getInstantiator().getI18NProvider();
        return (provider == null ? FALLBACK : provider).getTranslation(key, locale(), params);
    }

    /** A date formatter in the current language. */
    public static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, locale());
    }

    /** "card" or "cards" style plural from two keys. */
    public static String count(long count, String singularKey, String pluralKey) {
        return count == 1 ? t(singularKey, count) : t(pluralKey, count);
    }
}
