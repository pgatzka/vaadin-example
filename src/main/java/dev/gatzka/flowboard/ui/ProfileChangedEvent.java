package dev.gatzka.flowboard.ui;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.UI;

/** Fired on the {@link UI} when the current user changed their display name. */
public class ProfileChangedEvent extends ComponentEvent<UI> {

    public ProfileChangedEvent(UI source) {
        super(source, false);
    }
}
