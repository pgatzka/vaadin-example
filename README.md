# Flowboard

Real-time collaborative Kanban boards, built with **Vaadin 25**, **Spring Boot 4** and **Java 21**.

Open the same board in two browsers and move a card in one of them: the other updates instantly,
with a small toast saying who did what. No JavaScript was written for this; everything is plain
Java on top of Vaadin Flow's server push.

![Flowboard board view](docs/board.png)

| Live sync between two browsers | Card editor | Activity feed |
|---|---|---|
| ![Live sync](docs/live-sync.png) | ![Card editor](docs/card-editor.png) | ![Activity](docs/activity.png) |

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
- **Filtering.** Type in the filter box (or press `/`) to narrow the board down to matching cards.
- **Light and dark theme**, following the operating system on first visit, and an installable PWA manifest.
- **Export and import** boards as JSON files.
- **Checklists, comments and history** on every card; **deep links** to cards.
- **Insights** per board: completion rate, cycle time, velocity, load per lane, completions per day, work per person.
- **AI assistant** (optional, set `ANTHROPIC_API_KEY`): suggests checklist steps for a card, writes a
  standup summary of the board, and turns pasted meeting notes into cards for review.
- **Story points**, lane point totals and velocity; **templates** for new boards; **archive** with
  restore; a **calendar** of due dates; a cross-board **My work** view and **global search** (Ctrl+K).
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
