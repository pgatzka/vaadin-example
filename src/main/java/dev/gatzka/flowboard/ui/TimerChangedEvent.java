package dev.gatzka.flowboard.ui;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.UI;

/** Fired on the UI when the current user started or stopped a timer, so the header can update. */
public class TimerChangedEvent extends ComponentEvent<UI> {

    public TimerChangedEvent(UI source) {
        super(source, false);
    }
}
