package dev.gatzka.flowboard.domain;

/** The body shape a webhook endpoint expects. */
public enum WebhookFormat {
    /** Flowboard's own JSON with every field, for your own integrations. */
    FLOWBOARD("Flowboard JSON"),
    /** The {@code {"text": …}} shape of a Slack incoming webhook. */
    SLACK("Slack message"),
    /** The {@code {"content": …}} shape of a Discord webhook. */
    DISCORD("Discord message");

    private final String label;

    WebhookFormat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isChat() {
        return this != FLOWBOARD;
    }
}
