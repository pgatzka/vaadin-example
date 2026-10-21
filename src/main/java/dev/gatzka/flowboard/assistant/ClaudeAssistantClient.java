package dev.gatzka.flowboard.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import java.time.Duration;
import java.util.stream.Collectors;

/** {@link AssistantClient} backed by the Claude API through the official Java SDK. */
public class ClaudeAssistantClient implements AssistantClient {

    private static final long MAX_TOKENS = 4_000;

    private final AnthropicClient client;
    private final String model;

    public ClaudeAssistantClient(String apiKey, String model) {
        this(AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofMinutes(2)).build(), model);
    }

    ClaudeAssistantClient(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .build();
        Message response;
        try {
            response = client.messages().create(params);
        } catch (RateLimitException busy) {
            throw new AssistantException("The assistant is busy right now. Please try again in a moment.", busy);
        } catch (AnthropicException failure) {
            throw new AssistantException("The assistant could not be reached: " + failure.getMessage(), failure);
        }
        if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
            throw new AssistantException("The assistant declined to answer this request.");
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .collect(Collectors.joining("\n"));
    }
}
