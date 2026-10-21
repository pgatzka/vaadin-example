package dev.gatzka.flowboard.ui.support;

import com.vaadin.flow.component.UI;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs slow work (such as a call to the assistant) off the request thread and delivers the
 * outcome back into the UI through server push.
 */
public final class Background {

    private Background() {
    }

    public static <T> void run(Executor executor, UI ui, Supplier<T> work, Consumer<T> onResult, Consumer<RuntimeException> onError) {
        executor.execute(() -> {
            T result;
            try {
                result = work.get();
            } catch (RuntimeException failure) {
                UiAccess.run(ui, () -> onError.accept(failure));
                return;
            }
            UiAccess.run(ui, () -> onResult.accept(result));
        });
    }
}
