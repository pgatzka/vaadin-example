package dev.gatzka.flowboard.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.service.BoardService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Drives real HTTP deliveries against a tiny in-process server. */
@SpringBootTest
@ActiveProfiles("test")
class WebhookServiceTest {

    private record Received(String path, Map<String, String> headers, String body) {
    }

    @Autowired
    private BoardService boardService;

    @Autowired
    private WebhookService webhooks;

    @Autowired
    private WebhookDispatcher dispatcher;

    @Autowired
    private JsonMapper json;

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private BoardSnapshot board;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> headers = Map.of(
                    "event", exchange.getRequestHeaders().getFirst(WebhookDispatcher.EVENT_HEADER),
                    "signature", String.valueOf(exchange.getRequestHeaders().getFirst(Signatures.HEADER)),
                    "content-type", exchange.getRequestHeaders().getFirst("Content-Type"));
            received.add(new Received(exchange.getRequestURI().getPath(), headers, body));
            int status = exchange.getRequestURI().getPath().startsWith("/broken") ? 500 : 204;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        board = boardService.createBoard(new BoardDraft("Hooked", null), "Mara");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String endpoint(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void postsSignedJsonAfterEachChangeAndLogsTheDelivery() {
        WebhookSnapshot hook = webhooks.add(board.id(), endpoint("/ok"), "top-secret", "Mara");
        assertThat(hook.hasSecret()).isTrue();
        assertThat(hook.enabled()).isTrue();
        assertThat(hook.lastDelivery()).isEmpty();
        received.clear();

        boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Ping"), "Mara");

        // Registering the hook is itself a board change, so an "updated" event may arrive first.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(received).extracting(call -> call.headers().get("event")).contains("content"));
        Received call = received.stream().filter(it -> it.headers().get("event").equals("content")).findFirst().orElseThrow();
        assertThat(call.path()).isEqualTo("/ok");
        assertThat(call.headers().get("event")).isEqualTo("content");
        assertThat(call.headers().get("content-type")).isEqualTo("application/json");
        assertThat(call.headers().get("signature")).isEqualTo(Signatures.sign("top-secret", call.body().getBytes(StandardCharsets.UTF_8)));
        JsonNode payload = json.readTree(call.body());
        assertThat(payload.get("event").asString()).isEqualTo("content");
        assertThat(payload.get("boardId").asLong()).isEqualTo(board.id());
        assertThat(payload.get("boardName").asString()).isEqualTo("Hooked");
        assertThat(payload.get("actor").asString()).isEqualTo("Mara");
        assertThat(payload.get("message").asString()).isEqualTo("added \"Ping\" to Backlog");
        assertThat(payload.get("cardId").asLong()).isPositive();
        assertThat(payload.get("occurredAt").asString()).isNotBlank();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(webhooks.deliveries(hook.id())).anySatisfy(delivery -> {
                    assertThat(delivery.event()).isEqualTo("content");
                    assertThat(delivery.succeeded()).isTrue();
                    assertThat(delivery.statusCode()).isEqualTo(204);
                    assertThat(delivery.summary()).isEqualTo("HTTP 204");
                }));
        assertThat(webhooks.forBoard(board.id()).getFirst().lastDelivery()).isPresent();
    }

    @Test
    void failuresAreLoggedAndPausedWebhooksStayQuiet() {
        WebhookSnapshot broken = webhooks.add(board.id(), endpoint("/broken"), null, "Mara");
        WebhookSnapshot paused = webhooks.add(board.id(), endpoint("/paused"), null, "Mara");
        webhooks.setEnabled(paused.id(), false, "Mara");
        WebhookSnapshot unreachable = webhooks.add(board.id(), "http://127.0.0.1:1/nothing", null, "Mara");
        received.clear();

        dispatcher.sendTest(broken.id(), "Mara");
        dispatcher.sendTest(paused.id(), "Mara");
        dispatcher.sendTest(unreachable.id(), "Mara");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(testDeliveries(broken.id())).hasSize(1);
            assertThat(testDeliveries(unreachable.id())).hasSize(1);
            assertThat(testDeliveries(paused.id())).hasSize(1);
        });
        assertThat(testDeliveries(broken.id()).getFirst().statusCode()).isEqualTo(500);
        assertThat(testDeliveries(broken.id()).getFirst().succeeded()).isFalse();
        assertThat(testDeliveries(unreachable.id()).getFirst().error()).contains("ConnectException");
        assertThat(received).filteredOn(call -> call.headers().get("event").equals("test"))
                .extracting(Received::path).containsExactlyInAnyOrder("/broken", "/paused");
        assertThat(received.stream().filter(call -> call.path().equals("/paused")).findFirst().orElseThrow().headers().get("signature")).isEqualTo("null");

        received.clear();
        boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("Quiet"), "Mara");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(received).extracting(call -> call.headers().get("event")).contains("content"));
        assertThat(received).extracting(Received::path).containsExactly("/broken");
        assertThat(webhooks.forBoard(board.id())).extracting(WebhookSnapshot::enabled).containsExactly(true, false, true);
    }

    private List<WebhookSnapshot.DeliverySnapshot> testDeliveries(long webhookId) {
        return webhooks.deliveries(webhookId).stream().filter(delivery -> delivery.event().equals("test")).toList();
    }

    @Test
    void validatesAndRemovesWebhooks() {
        assertThatThrownBy(() -> webhooks.add(board.id(), "ftp://example.com/x", null, "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> webhooks.add(board.id(), "not a url", null, "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> webhooks.add(board.id(), "", null, "Mara")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> webhooks.add(board.id(), "https://example.com", "s".repeat(101), "Mara")).isInstanceOf(IllegalArgumentException.class);

        WebhookSnapshot hook = webhooks.add(board.id(), "  https://example.com/hook ", "", "Mara");
        assertThat(hook.url()).isEqualTo("https://example.com/hook");
        assertThat(hook.hasSecret()).isFalse();
        webhooks.remove(hook.id(), "Mara");
        assertThat(webhooks.forBoard(board.id())).isEmpty();

        webhooks.add(board.id(), "https://example.com/other", null, "Mara");
        boardService.deleteBoard(board.id(), "Mara");
        assertThat(webhooks.forBoard(board.id())).isEmpty();
    }

    @Test
    void keepsOnlyTheLatestDeliveries() {
        WebhookSnapshot hook = webhooks.add(board.id(), endpoint("/ok"), null, "Mara");
        for (int i = 0; i < WebhookService.KEPT_DELIVERIES + 5; i++) {
            webhooks.recordDelivery(hook.id(), "test", 200, true, null);
        }
        assertThat(webhooks.deliveries(hook.id())).hasSize(WebhookService.KEPT_DELIVERIES);
        webhooks.recordDelivery(999_999L, "test", 200, true, null);
    }
}
