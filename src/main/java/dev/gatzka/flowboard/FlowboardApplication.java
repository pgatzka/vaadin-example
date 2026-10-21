package dev.gatzka.flowboard;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point and application shell.
 * <p>
 * {@link Push} enables server push so that changes made by one user are streamed to every
 * other browser looking at the same board.
 */
@SpringBootApplication
@Push
@PWA(name = "Flowboard", shortName = "Flowboard", description = "Real-time collaborative Kanban boards")
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@StyleSheet("styles.css")
public class FlowboardApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(FlowboardApplication.class, args);
    }
}
