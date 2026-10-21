package dev.gatzka.flowboard.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the Claude client only when an API key is configured; otherwise the assistant stays hidden. */
@Configuration(proxyBeanMethods = false)
class AssistantConfiguration {

    @Bean
    @ConditionalOnExpression("'${flowboard.assistant.api-key:}' != ''")
    AssistantClient claudeAssistantClient(
            @Value("${flowboard.assistant.api-key}") String apiKey,
            @Value("${flowboard.assistant.model:claude-opus-5-5}") String model) {
        return new ClaudeAssistantClient(apiKey, model);
    }
}
