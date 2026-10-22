package dev.gatzka.flowboard.assistant;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Test double answering from the system prompt's wording; records what it was asked. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAssistant {

    public static final class Client implements AssistantClient {

        public final List<String> prompts = new ArrayList<>();
        public String checklistAnswer = "Here you go:\n```json\n[\"Write the test\", \"Make it pass\", \"Refactor\"]\n```";
        public String standupAnswer = "## Done in the last 24 hours\n- Mara shipped the sync\n\n## At risk\n- nothing";
        public String digestAnswer = "A quiet week: Mara shipped the sync and nothing is late.";
        public String notesAnswer = """
                [{"title": "Order tiles", "priority": "URGENT", "assignee": "Mara", "description": "Two boxes."},
                 {"title": "", "priority": "low"},
                 {"title": "Call the plumber", "priority": "whatever"},
                 "not an object"]
                """;
        public RuntimeException failure;

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            prompts.add(userPrompt);
            if (failure != null) {
                throw failure;
            }
            if (systemPrompt.contains("checkable steps")) {
                return checklistAnswer;
            }
            if (systemPrompt.contains("standup")) {
                return standupAnswer;
            }
            if (systemPrompt.contains("weekly report")) {
                return digestAnswer;
            }
            return notesAnswer;
        }
    }

    @Bean
    public Client fakeAssistantClient() {
        return new Client();
    }
}
