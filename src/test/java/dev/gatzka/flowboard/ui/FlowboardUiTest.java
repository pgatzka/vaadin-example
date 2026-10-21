package dev.gatzka.flowboard.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.server.VaadinSession;

/**
 * Base class for browserless view tests.
 *
 * <p>Server push is delivered through {@code ui.access} from background threads. The library's
 * {@code roundTrip()} flushes the UI but does not run that pending access queue, which otherwise
 * only happens when the session lock is released, so tests waiting for pushed updates call
 * {@link #pump()} instead.
 */
public abstract class FlowboardUiTest extends SpringBrowserlessTest {

    /** Runs UI updates queued by background threads, then flushes the UI like a client round trip. */
    protected static void pump() {
        VaadinSession session = VaadinSession.getCurrent();
        session.lock();
        try {
            session.getService().runPendingAccessTasks(session);
        } finally {
            session.unlock();
        }
        roundTrip();
    }
}
