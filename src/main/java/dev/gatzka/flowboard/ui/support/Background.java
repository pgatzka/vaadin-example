package dev.gatzka.flowboard.ui.support;

import com.vaadin.flow.component.UI;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs slow work (such as a call to the assistant) off the request thread and delivers the
 * outcome back into the UI through server push.
 */
public final class Background {

    private static final Logger LOG = LoggerFactory.getLogger(Background.class);

    private Background() {
    }

    public static <T> void run(Executor executor, UI ui, Supplier<T> work, Consumer<T> onResult, Consumer<RuntimeException> onError) {
        Objects.requireNonNull(ui, "A UI is needed to deliver the result");
        executor.execute(() -> {
            T result;
            try {
                result = work.get();
            } catch (RuntimeException failure) {
                LOG.debug("Background work failed", failure);
                UiAccess.run(ui, () -> onError.accept(failure));
                return;
            } catch (Error unexpected) {
                LOG.error("Background work crashed", unexpected);
                throw unexpected;
            }
            UiAccess.run(ui, () -> onResult.accept(result));
        });
    }
}
