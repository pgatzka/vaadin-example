package dev.gatzka.flowboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.security.AccountService;
import dev.gatzka.flowboard.security.ApiTokenService;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.BoardService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Drives the API over real HTTP so the servlet routing next to Vaadin is covered too. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BoardsApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private BoardService boardService;

    @Autowired
    private ActivityLog activityLog;

    @Autowired
    private JsonMapper json;

    @Autowired
    private AccountService accounts;

    @Autowired
    private ApiTokenService tokenService;

    @Autowired
    private dev.gatzka.flowboard.service.AccessService access;

    @Autowired
    private dev.gatzka.flowboard.service.SharingService sharing;

    private final HttpClient http = HttpClient.newHttpClient();
    private BoardSnapshot board;

    @BeforeEach
    void seed() {
        if (accounts.find("deploy-bot").isEmpty()) {
            accounts.register("deploy-bot", "Deploy Bot", "robots-only");
        }
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        board = boardService.createBoard(new BoardDraft("Api board", "Reachable over HTTP"), "Seeder");
    }

    @Test
    void refusesAnonymousAndUnknownCallers() throws Exception {
        HttpRequest anonymous = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/boards")).GET().build();
        assertThat(http.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        HttpRequest wrongPassword = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/boards"))
                .header("Authorization", basic("deploy-bot", "wrong")).GET().build();
        HttpResponse<String> refused = http.send(wrongPassword, HttpResponse.BodyHandlers.ofString());
        assertThat(refused.statusCode()).as(refused.headers().map() + refused.body()).isEqualTo(401);
    }

    @Test
    void acceptsPersonalApiTokensAsBearerTokens() throws Exception {
        ApiTokenService.Issued issued = tokenService.issue("deploy-bot", "test");
        HttpRequest withToken = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/boards"))
                .header("Authorization", "Bearer " + issued.secret()).GET().build();
        HttpResponse<String> accepted = http.send(withToken, HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        assertThat(json.readTree(accepted.body()).get(0).get("name").asString()).isEqualTo("Api board");

        tokenService.revoke(issued.token().id(), "deploy-bot");
        HttpResponse<String> revoked = http.send(withToken, HttpResponse.BodyHandlers.ofString());
        assertThat(revoked.statusCode()).isEqualTo(401);
        assertThat(revoked.body()).contains("Bearer");

        HttpRequest garbage = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/boards"))
                .header("Authorization", "Bearer nonsense").GET().build();
        assertThat(http.send(garbage, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    @Test
    void listsAndReadsBoards() throws Exception {
        HttpResponse<String> list = send("GET", "/api/boards", null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.headers().firstValue("content-type").orElse("")).contains("application/json");
        JsonNode boards = json.readTree(list.body());
        assertThat(boards.size()).isEqualTo(1);
        assertThat(boards.get(0).get("name").asString()).isEqualTo("Api board");
        assertThat(boards.get(0).get("laneCount").asInt()).isEqualTo(4);

        JsonNode detail = json.readTree(send("GET", "/api/boards/" + board.id(), null).body());
        assertThat(detail.get("version").asInt()).isEqualTo(1);
        assertThat(detail.get("lanes").size()).isEqualTo(4);
        assertThat(detail.get("lanes").get(0).get("name").asString()).isEqualTo("Backlog");
    }

    @Test
    void createsMovesUpdatesAndDeletesCards() throws Exception {
        long backlog = board.lanes().get(0).id();
        long done = board.lanes().get(3).id();

        HttpResponse<String> created = send("POST", "/api/boards/" + board.id() + "/lanes/" + backlog + "/cards",
                "{\"title\": \"From the API\", \"priority\": \"high\", \"tags\": [\"ops\"], \"estimate\": 3}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode card = json.readTree(created.body());
        long cardId = card.get("id").asLong();
        assertThat(created.headers().firstValue("location")).contains("/api/cards/" + cardId);
        assertThat(card.get("priority").asString()).isEqualTo("HIGH");
        assertThat(card.get("tags").get(0).asString()).isEqualTo("ops");
        assertThat(activityLog.recent(board.id()).getFirst().actor()).isEqualTo("Deploy Bot");

        assertThat(send("POST", "/api/cards/" + cardId + "/move", "{\"targetLaneId\": " + done + ", \"targetIndex\": 0}").statusCode()).isEqualTo(204);
        assertThat(boardService.findBoard(board.id()).orElseThrow().lane(done).orElseThrow().cards())
                .extracting(CardSnapshot::title).containsExactly("From the API");

        JsonNode updated = json.readTree(send("PUT", "/api/cards/" + cardId, "{\"title\": \"Renamed\", \"priority\": \"LOW\"}").body());
        assertThat(updated.get("title").asString()).isEqualTo("Renamed");
        assertThat(updated.get("estimate").isNull()).isTrue();

        assertThat(send("DELETE", "/api/cards/" + cardId, null).statusCode()).isEqualTo(204);
        assertThat(boardService.findBoard(board.id()).orElseThrow().cardCount()).isZero();
    }

    @Test
    void createsBoardsFromTheExportFormat() throws Exception {
        String body = "{\"name\": \"Posted\", \"lanes\": [{\"name\": \"Todo\", \"cards\": [{\"title\": \"One\"}]}, {\"name\": \"Done\", \"done\": true}]}";

        HttpResponse<String> created = send("POST", "/api/boards", body);

        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode snapshot = json.readTree(created.body());
        assertThat(snapshot.get("name").asString()).isEqualTo("Posted");
        assertThat(snapshot.get("lanes").size()).isEqualTo(2);
        assertThat(snapshot.get("lanes").get(0).get("cards").get(0).get("title").asString()).isEqualTo("One");
        assertThat(send("DELETE", "/api/boards/" + snapshot.get("id").asLong(), null).statusCode()).isEqualTo(204);
    }

    @Test
    void importsTrelloExports() throws Exception {
        String trello;
        try (var in = getClass().getResourceAsStream("/trello-board.json")) {
            trello = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        HttpResponse<String> created = send("POST", "/api/boards/import/trello", trello);

        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode snapshot = json.readTree(created.body());
        assertThat(snapshot.get("name").asString()).isEqualTo("Website relaunch");
        assertThat(snapshot.get("lanes").size()).isEqualTo(3);
        assertThat(snapshot.get("lanes").get(0).get("cards").get(0).get("title").asString()).isEqualTo("Fix the header");
        assertThat(send("POST", "/api/boards/import/trello", "{\"name\": \"not trello\"}").statusCode()).isEqualTo(400);
        assertThat(send("DELETE", "/api/boards/" + snapshot.get("id").asLong(), null).statusCode()).isEqualTo(204);
    }

    @Test
    void servesDigestsTimeAndTemplates() throws Exception {
        long backlog = board.lanes().get(0).id();
        HttpResponse<String> template = send("POST", "/api/boards/" + board.id() + "/templates",
                "{\"name\": \"Incident\", \"priority\": \"urgent\", \"tags\": [\"ops\"], \"checklist\": [\"Acknowledge\", \"Mitigate\", \"Write up\"]}");
        assertThat(template.statusCode()).as(template.body()).isEqualTo(201);
        long templateId = json.readTree(template.body()).get("id").asLong();
        assertThat(json.readTree(send("GET", "/api/boards/" + board.id() + "/templates", null).body()).size()).isEqualTo(1);

        HttpResponse<String> created = send("POST", "/api/templates/" + templateId + "/cards", "{\"laneId\": " + backlog + "}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode card = json.readTree(created.body());
        assertThat(card.get("title").asString()).isEqualTo("Incident");
        assertThat(card.get("priority").asString()).isEqualTo("URGENT");
        assertThat(card.get("checklist").size()).isEqualTo(3);
        long cardId = card.get("id").asLong();

        HttpResponse<String> logged = send("POST", "/api/cards/" + cardId + "/time", "{\"minutes\": 30, \"note\": \"triage\"}");
        assertThat(logged.statusCode()).as(logged.body()).isEqualTo(201);
        assertThat(json.readTree(logged.body()).get("workedMinutes").asInt()).isEqualTo(30);
        assertThat(send("POST", "/api/cards/" + cardId + "/time", "{\"minutes\": 0}").statusCode()).isEqualTo(400);

        JsonNode digest = json.readTree(send("GET", "/api/boards/" + board.id() + "/digest", null).body());
        assertThat(digest.get("boardName").asString()).isEqualTo("Api board");
        assertThat(digest.get("created").size()).isEqualTo(1);
        assertThat(digest.get("contributors").get(0).get("name").asString()).isEqualTo("Deploy Bot");
        HttpResponse<String> markdown = send("GET", "/api/boards/" + board.id() + "/digest.md?week=" + java.time.LocalDate.now(), null,
                "Accept", "text/markdown");
        assertThat(markdown.statusCode()).as(markdown.body()).isEqualTo(200);
        assertThat(markdown.headers().firstValue("content-type").orElse("")).startsWith("text/markdown");
        assertThat(markdown.body()).startsWith("# Api board · week of ").contains("- Incident");
        assertThat(send("GET", "/api/boards/" + board.id() + "/digest?week=2026-01-07", null).statusCode()).isEqualTo(200);

        JsonNode activity = json.readTree(send("GET", "/api/boards/" + board.id() + "/activity", null).body());
        assertThat(activity.get(0).get("actor").asString()).isEqualTo("Deploy Bot");
        assertThat(activity.get(0).get("message").asString()).isEqualTo("logged 30 min on \"Incident\"");

        assertThat(send("DELETE", "/api/templates/" + templateId, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/boards/999999/templates", null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/boards/999999/digest", null).statusCode()).isEqualTo(404);

        JsonNode forecast = json.readTree(send("GET", "/api/boards/" + board.id() + "/forecast", null).body());
        assertThat(forecast.get("remaining").asInt()).isEqualTo(1);
        assertThat(forecast.get("throughput").size()).isEqualTo(60);
        assertThat(forecast.get("outcomes").size()).as("one completion is too little history").isZero();
        assertThat(send("GET", "/api/boards/999999/forecast", null).statusCode()).isEqualTo(404);
    }

    @Test
    void membersOnlyBoardsAreInvisibleToNonMembers() throws Exception {
        if (accounts.find("outsider").isEmpty()) {
            accounts.register("outsider", "Outsider", "robots-only");
        }
        access.setMembersOnly(board.id(), true, "deploy-bot", "Deploy Bot");
        long backlog = board.lanes().get(0).id();
        CardSnapshot card = boardService.createCard(backlog, dev.gatzka.flowboard.domain.CardDraft.titled("Secret"), "Deploy Bot");

        assertThat(json.readTree(send("GET", "/api/boards", null).body()).size()).as("member still sees it").isEqualTo(1);
        assertThat(send("GET", "/api/boards/" + board.id(), null).statusCode()).isEqualTo(200);

        HttpRequest list = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/boards"))
                .header("Authorization", basic("outsider", "robots-only")).GET().build();
        assertThat(json.readTree(http.send(list, HttpResponse.BodyHandlers.ofString()).body()).size()).isZero();
        for (String path : List.of("/api/boards/" + board.id(), "/api/boards/" + board.id() + "/digest", "/api/boards/" + board.id() + "/templates")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Authorization", basic("outsider", "robots-only")).GET().build();
            assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).as(path).isEqualTo(404);
        }
        HttpRequest move = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/cards/" + card.id() + "/move"))
                .header("Authorization", basic("outsider", "robots-only")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"targetLaneId\": " + backlog + ", \"targetIndex\": 0}")).build();
        assertThat(http.send(move, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
    }

    @Test
    void servesCalendarFeedsForMembersAndThroughTheShareToken() throws Exception {
        boardService.createCard(board.lanes().get(0).id(), new dev.gatzka.flowboard.domain.CardDraft("Launch day", null, null,
                java.time.LocalDate.of(2026, 12, 1), null, java.util.Set.of(), null), "Seeder");
        HttpResponse<String> own = send("GET", "/api/boards/" + board.id() + "/calendar.ics", null, "Accept", "text/calendar");
        assertThat(own.statusCode()).as(own.body()).isEqualTo(200);
        assertThat(own.headers().firstValue("content-type").orElse("")).startsWith("text/calendar");
        assertThat(own.body()).contains("SUMMARY:Launch day").contains("URL:http://localhost:" + port + "/board/" + board.id() + "/card/");

        HttpRequest anonymous = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/calendar/nope.ics")).GET().build();
        assertThat(http.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()).as("unknown token").isEqualTo(404);

        String token = sharing.enable(board.id(), "Seeder");
        HttpRequest shared = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/calendar/" + token + ".ics")).GET().build();
        HttpResponse<String> feed = http.send(shared, HttpResponse.BodyHandlers.ofString());
        assertThat(feed.statusCode()).as(feed.body()).isEqualTo(200);
        assertThat(feed.body()).contains("BEGIN:VCALENDAR").contains("SUMMARY:Launch day");
    }

    @Test
    void reportsErrorsAsJson() throws Exception {
        HttpResponse<String> missing = send("GET", "/api/boards/424242", null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(json.readTree(missing.body()).get("error").asString()).contains("Board 424242");

        long backlog = board.lanes().get(0).id();
        HttpResponse<String> blank = send("POST", "/api/boards/" + board.id() + "/lanes/" + backlog + "/cards", "{\"title\": \"  \"}");
        assertThat(blank.statusCode()).isEqualTo(400);
        assertThat(json.readTree(blank.body()).get("error").asString()).contains("must not be blank");

        HttpResponse<String> wrongLane = send("POST", "/api/boards/" + board.id() + "/lanes/999/cards", "{\"title\": \"x\"}");
        assertThat(wrongLane.statusCode()).isEqualTo(404);

        HttpResponse<String> garbage = send("POST", "/api/boards", "not json");
        assertThat(garbage.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> send(String method, String path, String body, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", basic("deploy-bot", "robots-only"))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
