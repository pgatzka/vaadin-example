package dev.gatzka.flowboard.ui.i18n;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import jakarta.servlet.http.Cookie;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Remembers the chosen language in a cookie and applies it to every new UI. */
@Component
public class LocalePreference implements VaadinServiceInitListener {

    public static final String COOKIE = "flowboard-lang";
    private static final int ONE_YEAR = 365 * 24 * 60 * 60;

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit -> fromCookie(VaadinRequest.getCurrent()).ifPresent(uiInit.getUI()::setLocale));
    }

    static java.util.Optional<Locale> fromCookie(VaadinRequest request) {
        if (request == null || request.getCookies() == null) {
            return java.util.Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return java.util.Optional.of(FlowboardI18NProvider.supported(Locale.forLanguageTag(cookie.getValue())));
            }
        }
        return java.util.Optional.empty();
    }

    /** Switches the UI to the language, stores the choice for a year and reloads so every text follows. */
    public static void choose(UI ui, Locale locale) {
        Locale chosen = FlowboardI18NProvider.supported(locale);
        ui.setLocale(chosen);
        ui.getPage().executeJs("document.cookie = $0 + '=' + $1 + '; path=/; max-age=' + $2 + '; samesite=lax'; location.reload();",
                COOKIE, chosen.getLanguage(), ONE_YEAR);
    }
}
