package dev.gatzka.flowboard.ui;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.UI;

/** Fired on the {@link UI} when the command palette asks for a new board; the board list opens its dialog. */
public class NewBoardRequestedEvent extends ComponentEvent<UI> {

    public NewBoardRequestedEvent(UI source) {
        super(source, false);
    }
}
