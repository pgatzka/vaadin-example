package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One attempt to call a webhook. */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_id", nullable = false)
    private Long webhookId;

    @Column(nullable = false, length = 20)
    private String event;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(length = 300)
    private String error;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(long webhookId, String event, Integer statusCode, boolean succeeded, String error, Instant attemptedAt) {
        this.webhookId = webhookId;
        this.event = event;
        this.statusCode = statusCode;
        this.succeeded = succeeded;
        this.error = error;
        this.attemptedAt = attemptedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getWebhookId() {
        return webhookId;
    }

    public String getEvent() {
        return event;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public String getError() {
        return error;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
