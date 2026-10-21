package dev.gatzka.flowboard.ui.account;

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
        Span tagline = new Span("Kanban that keeps up with your team.");
        tagline.addClassName("account-page__tagline");
        Div brand = new Div(title, tagline);
        brand.addClassName("account-page__brand");
        return brand;
    }
}
