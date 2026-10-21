# Flowboard

Real-time collaborative Kanban boards, built with **Vaadin 25**, **Spring Boot 4** and **Java 21**.

Open the same board in two browsers and move a card in one of them: the other updates instantly,
with a small toast saying who did what. No JavaScript was written for this; everything is plain
Java on top of Vaadin Flow's server push.

![Flowboard board view](docs/board.png)

| Live sync between two browsers | Card editor | Activity feed |
|---|---|---|
| ![Live sync](docs/live-sync.png) | ![Card editor](docs/card-editor.png) | ![Activity](docs/activity.png) |
| Insights with cumulative flow | Calendar | Command palette |
| ![Insights](docs/insights.png) | ![Calendar](docs/calendar.png) | ![Palette](docs/palette.png) |
| Home dashboard | Sign in | Create an account |
| ![Home](docs/home.png) | ![Sign in](docs/login.png) | ![Register](docs/register.png) |
| Weekly digest | Activity heatmap | Public read-only link |
| ![Digest](docs/digest.png) | ![Heatmap](docs/heatmap.png) | ![Shared board](docs/shared.png) |
| Undo after a keyboard move | Webhooks | Share dialog |
| ![Undo](docs/undo.png) | ![Webhooks](docs/webhooks.png) | ![Share](docs/share.png) |
| Card templates | Saved views | Time tracking |
| ![Templates](docs/templates.png) | ![Views](docs/views.png) | ![Time](docs/time.png) |

## Features

- **Boards, lanes and cards** with drag & drop. Drop a card onto another card to insert it before
  that card, or onto empty lane space to append it.
- **Live collaboration.** Every change is pushed to everybody looking at the same board. Presence
  avatars in the toolbar show who else is there right now.
- **WIP limits.** Give a lane a work-in-progress limit and it turns red when the limit is exceeded.
- **Done lanes.** Cards in a lane marked as *done* are never reported as overdue.
- **Rich cards.** Priority (shown as a coloured stripe), due date with overdue and due-soon states,
  assignee, tags, and a Markdown description with live preview.
- **Activity feed.** A side sheet lists the recent history of the board as a chat-like log.
- **Filtering.** Type in the filter box (or press `/`) to narrow the board down to matching cards;
  structured filters for priority, tag, assignee, overdue, mine and unassigned. The filter lives
  in the URL, so a filtered board can be linked to, and can be saved as a named **view** that
  shows up as a chip for everybody on the board.
- **Light and dark theme**, following the operating system on first visit, and an installable PWA manifest.
- **Export and import** boards as JSON files.
- **Checklists, comments and history** on every card; **deep links** to cards.
- **Insights** per board: completion rate, cycle time, velocity, load per lane, completions per day, work per person,
  a 12-week activity heatmap with streaks and the busiest contributors.
- **AI assistant** (optional, set `ANTHROPIC_API_KEY`): suggests checklist steps for a card, writes a
  standup summary of the board, and turns pasted meeting notes into cards for review.
- **Story points**, lane point totals and velocity; **templates** for new boards; **archive** with
  restore; a **calendar** of due dates; a cross-board **My work** view and a **command palette** (Ctrl+K)
  that searches every board, opens boards by name and runs actions.
- **Flow analytics.** Card aging ("3 d here"), average time per lane, and a cumulative flow diagram
  built from daily lane snapshots.
- **Home dashboard** with totals across boards, what needs attention and recent activity.
- **Morning reminders.** Every day at seven the inbox tells you which of your cards are due today
  or overdue, once per card.
- **Inbox.** Mention somebody with `@name` in a comment, assign them a card or comment on their card
  and they get a notification, live, with a link to the card.
- **Dependencies** between cards ("blocked by", acyclic, with a nudge before finishing a blocked card)
  and **file attachments** on cards (up to 5 MB each, stored in the database).
- **Votes** on cards with "sort by votes" for backlogs, multi-line paste into a lane to create several
  cards, and a little confetti when a card lands in a done lane.
- **Quick-add shorthand.** Type `Fix login !high @mara #backend ~3 due:fri every:week` into a lane
  and the card comes out with priority, assignee, tag, estimate, due date and repetition set.
- **Card templates.** Define ready-made cards per board, checklist included, create them from a
  lane's menu, or turn any existing card into a template from its editor.
- **Time tracking.** Start a timer on a card (one per person, shown pulsing in the header while it
  runs), or log minutes by hand with a note. Cards show the time spent; the editor keeps the log.
- **Weekly digest.** A printable one-page report per board and week: done, new, overdue, blocked,
  due next week and who did the most, with a copy-as-Markdown button for the team chat.
- **Keyboard first.** Arrow keys walk the cards, Shift+arrows move them, Enter opens, N adds.
- **Webhooks.** Register endpoints per board; every change is posted as JSON, optionally signed
  with HMAC-SHA256. A delivery log shows what the other side answered.
- **Recurring cards.** Set a card to repeat daily, weekly, every two weeks or monthly; finishing it
  puts a fresh copy back in its lane with the due date moved on.
- **Undo.** Moving, finishing or archiving cards shows a toast with an Undo button.
- **What's new.** Boards in the sidebar show how much happened since you last opened them, and cards
  edited since your last visit carry a dot.
- **Public links.** Share a board read-only with anyone, no account needed. Viewers see every
  change live; turn the link off and it stops working.
- **Accounts.** Register with a username and password (BCrypt-hashed), sign in, sign out. The
  display name on your account is what everyone else sees. With sample data on, a `demo` account
  (password `demo1234`) is created so you can look around straight away.
- **Persistent.** Boards live in a file-based H2 database under `./data`, so they survive restarts.
  The schema is managed with Flyway migrations and validated by Hibernate on startup. A demo board
  is seeded on the first start.
- **Operations.** A health endpoint at `/actuator/health`, a Dockerfile and a CI workflow.

## Running

Requirements: JDK 21+ and Node 20+ (Node is only needed for the frontend build and is downloaded
automatically by the Vaadin plugin if missing).

```bash
# development mode with live reload
./mvnw            # or: mvn spring-boot:run

# production build and run
mvn -DskipTests package
java -jar target/flowboard.jar
```

Then open <http://localhost:8080>. Open a second tab or browser to see changes synchronise.

With Docker:

```bash
docker compose up --build
```

The database lives in the `flowboard-data` volume, so boards survive container restarts.

Useful switches (`src/main/resources/application.properties`):

| Property                 | Default                      | Purpose                                      |
|--------------------------|------------------------------|----------------------------------------------|
| `flowboard.sample-data`  | `true`                       | Seed demo boards when the database is empty. |
| `spring.datasource.url`  | `jdbc:h2:file:./data/flowboard` | Where boards are stored.                  |
| `server.port` / `PORT`   | `8080`                       | HTTP port.                                   |

## Tests

```bash
mvn test
```

The suite covers the service layer (ordering logic, WIP limits, deletion, event publication) and
the views. UI tests use Vaadin's browserless testing library: they drive real view code,
including dialogs and server push, without a browser or servlet container.

## API

A small JSON API under `/api` lets scripts and other tools work with boards. Changes made through
it are pushed to open browsers like any other change. Requests authenticate with HTTP Basic using
a Flowboard account; the account's display name is what the activity feed shows.

| Method and path | Purpose |
|---|---|
| `GET /api/boards` | List boards with headline numbers |
| `GET /api/boards/{id}` | The whole board in the export format |
| `POST /api/boards` | Create a board from the export format (`{"name": "…"}` is enough) |
| `DELETE /api/boards/{id}` | Delete a board |
| `POST /api/boards/{id}/lanes/{laneId}/cards` | Create a card from a draft (`title`, `description`, `priority`, `dueDate`, `assignee`, `tags`, `estimate`) |
| `PUT /api/cards/{id}` | Replace a card's draft fields |
| `POST /api/cards/{id}/move` | Move a card: `{"targetLaneId": 3, "targetIndex": 0}` |
| `DELETE /api/cards/{id}` | Delete a card |
| `POST /api/cards/{id}/time` | Log time in your name: `{"minutes": 30, "note": "…"}` |
| `GET /api/boards/{id}/activity` | Recent history of the board, newest first |
| `GET /api/boards/{id}/digest` | The weekly digest as JSON; `?week=2026-10-21` picks the week ending that day |
| `GET /api/boards/{id}/digest.md` | The same digest as Markdown, e.g. for a chat bot |
| `GET /api/boards/{id}/templates` | Card templates of the board |
| `POST /api/boards/{id}/templates` | Create or replace a template (`name`, `title`, `description`, `priority`, `assignee`, `tags`, `estimate`, `checklist`) |
| `POST /api/templates/{id}/cards` | Create a card from a template: `{"laneId": 3}` |
| `DELETE /api/templates/{id}` | Delete a template |

```bash
curl -s -u demo:demo1234 localhost:8080/api/boards | jq '.[0].id'
curl -s -u demo:demo1234 -X POST localhost:8080/api/boards/1/lanes/1/cards \
  -H 'Content-Type: application/json' \
  -d '{"title": "Rotate the signing key", "priority": "HIGH", "tags": ["ops"]}'
```

Errors come back as `{"error": "…"}` with 400 for invalid input, 401 without credentials and 404
for unknown ids.

## Webhooks

The plug icon on a board registers webhooks. After every committed change Flowboard posts a JSON
body to each enabled endpoint, from a background thread so the user never waits for it:

```json
{"event": "content", "boardId": 1, "boardName": "Launch", "actor": "Mara",
 "message": "moved \"Dark mode\" to Done", "cardId": 42, "occurredAt": "2026-10-21T22:40:00Z"}
```

`event` is `created`, `updated`, `deleted`, `content` or `test`, and is repeated in the
`X-Flowboard-Event` header. With a secret set, `X-Flowboard-Signature` carries
`sha256=<hex HMAC-SHA256 of the raw body>`; verify it with the same secret before trusting the
payload. The last 20 deliveries per webhook are kept with status codes, so a misbehaving endpoint
is easy to spot. Anything other than a 2xx response counts as a failure.

## How it is built

```
dev.gatzka.flowboard
├── domain        JPA entities plus immutable records: drafts going in, snapshots coming out
├── repository    Spring Data JPA repositories
├── service       BoardService (use cases), ActivityLog, BoardChangeBroadcaster, PresenceTracker
├── bootstrap     Sample data on first start
└── ui            MainLayout, board list, Kanban board view and its dialogs
```

A few design choices worth knowing about:

- **The UI never sees entities.** `BoardService` returns `BoardSnapshot` / `LaneSnapshot` /
  `CardSnapshot` records and accepts `*Draft` records. Snapshots are safe to hand to any thread and
  need no open persistence context, which matters once server push is involved.
- **Changes flow one way.** A view calls the service; the service records an `Activity` row and
  publishes a `BoardChangedEvent`. `BoardChangeBroadcaster` listens with a transactional event
  listener, so browsers are only notified *after commit*, and fans the event out on virtual
  threads. Each open view subscribes in `onAttach`, reloads its snapshot inside `UI.access`, and
  unsubscribes in `onDetach`.
- **Ordering is explicit.** Lanes and cards carry a `sort_order` column that the service renumbers
  on every move, so a reload always produces the same order the user saw.
- **Presence is ephemeral.** `PresenceTracker` is an in-memory registry keyed by board; it is
  deliberately not persisted.
- **Styling is one stylesheet.** `META-INF/resources/styles.css` is loaded after Lumo and only
  uses Lumo custom properties, so the dark theme comes for free.

## Stack

| Concern      | Choice                                                   |
|--------------|----------------------------------------------------------|
| UI           | Vaadin Flow 25 (Lumo theme, Card, Badge, Markdown, MessageList, Avatar, drag & drop) |
| Backend      | Spring Boot 4, Spring Data JPA, Hibernate, H2            |
| Realtime     | Vaadin server push (Atmosphere) + Spring application events |
| Tests        | JUnit, AssertJ, Awaitility, Vaadin browserless testing   |
| Build        | Maven, `vaadin-maven-plugin` frontend build              |
