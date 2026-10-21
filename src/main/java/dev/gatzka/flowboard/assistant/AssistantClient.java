package dev.gatzka.flowboard.assistant;

/** The thinnest possible seam to a language model: a system prompt and a user prompt in, text out. */
public interface AssistantClient {

    /**
     * @throws AssistantException when the model could not answer (network, quota, refusal)
     */
    String complete(String systemPrompt, String userPrompt);
}
