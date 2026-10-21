package dev.gatzka.flowboard.ui.support;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.theme.lumo.Lumo;

/** Theme handling for pages outside the main layout, which carries the signed-in user's choice. */
public final class Themes {

    private Themes() {
    }

    /** Switches the page to Lumo dark when the browser prefers a dark colour scheme, and follows later changes. */
    public static void followSystem(UI ui) {
        ui.getPage().executeJs("""
                const apply = (dark) => document.documentElement.setAttribute('theme', dark ? $0 : '');
                const query = window.matchMedia('(prefers-color-scheme: dark)');
                apply(query.matches);
                query.addEventListener('change', (event) => apply(event.matches));
                """, Lumo.DARK);
    }
}
