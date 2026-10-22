package dev.gatzka.flowboard.ui.account;

import dev.gatzka.flowboard.ui.i18n.I18n;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;

/** Shared pieces of the sign-in and registration pages. */
final class AccountPages {

    private AccountPages() {
    }

    static Div brand() {
        H1 title = new H1("Flowboard");
        title.addClassName("account-page__title");
        Span tagline = new Span(I18n.t("account.tagline"));
        tagline.addClassName("account-page__tagline");
        Div brand = new Div(title, tagline);
        brand.addClassName("account-page__brand");
        return brand;
    }
}
