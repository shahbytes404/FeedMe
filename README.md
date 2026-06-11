# FeedMe

FeedMe is a production-style social feed system built to study how modern feeds are written, cached, paginated, and served under normal-user and hot-user traffic patterns.

This repository contains one app across two surfaces:

- `frontend/`: Angular SPA for composing posts, switching viewers, following users, and exploring feed behavior
- `backend/`: Spring Boot API backed by PostgreSQL and Redis

The goal is not generic CRUD. The goal is to make feed-system tradeoffs visible in working code.

## Stack

- `Angular 21`
- `Spring Boot 3`
- `PostgreSQL`
- `Redis`
- `Prometheus`
- `Grafana`
- `Docker Compose`

## What Is Implemented

Current working functionality:

- create a post
- fetch a post by id
- follow and unfollow users
- load a personalized home feed
- load a user timeline
- first-page Redis caching for the home feed
- keyset cursor pagination for home and user feeds
- outbox-backed async post propagation
- Redis Streams publisher + consumer-group worker flow
- idempotent worker-side event consumption
- worker retry-attempt tracking and dead-letter handling
- normal-user `fan-out-on-write` cache updates via async worker
- hot-user `hybrid-pull` handling
- read-time merge of hot-user content into the home feed
- DB-backed idempotent post creation
- Prometheus metrics for feed, cache, delivery-path, and idempotency behavior
- seeded demo users, follows, and posts
- Angular UI to exercise all of the above

## Current Feed Design

FeedMe currently models two delivery paths.

### Normal users

- post is written to PostgreSQL
- `PostCreated` is persisted to DB outbox in the same write transaction
- outbox publisher pushes events to Redis Stream
- worker consumer applies fan-out updates to cached first-page home feeds
- delivery strategy on feed items is labeled `fan-out-on-write`

### Hot users

- post is written to PostgreSQL
- system does not walk follower caches one by one
- hot-user posts are fetched fresh on read
- home feed merges:
  - cached non-hot base feed
  - fresh hot-user slice
- delivery strategy on feed items is labeled `hybrid-pull`

This keeps hot-user write amplification bounded while still making those posts visible in the home feed.

## Async Propagation Model

FeedMe now uses a first-stage event-driven propagation pipeline:

- API write path saves `Post` + durable `OutboxEvent`
- scheduled publisher reads pending outbox events and publishes to Redis Stream (`feed:events`)
- scheduled worker consumes stream events through consumer group (`feed-workers`)
- worker deduplicates by `eventId` before applying side effects
- successful events are ACKed in Redis Stream
- repeated worker failures are tracked and moved to dead-letter storage after max attempts

This decouples post creation latency from propagation work while preserving delivery correctness under retries.

## Pagination Model

FeedMe uses keyset pagination, not offset pagination.

Cursor format:

```text
<createdAtEpochMillis>|<postId>
```

Why:

- feeds change while users paginate
- offset pagination causes duplicates and skipped items
- keyset pagination stays stable under new writes

Current query order:

- `createdAt DESC`
- `postId DESC`

The backend fetches `limit + 1` rows to determine whether another page exists and builds `nextCursor` from the last visible returned item.

## Caching Model

Redis currently stores only the canonical first page of the non-hot home-feed base slice.

That means:

- first-page home feed reads can hit Redis
- later pages bypass cache
- hot-user content is not stored as the final cached page
- hot-user content is merged fresh on every home-feed read

This is the core production-style tradeoff already implemented in the repo:

- cache stable, cheap-to-maintain base content
- pull hot-user content at read time

## Ranking Model

Feed items currently expose explainable ranking metadata:

- recency score
- followed-author affinity boost
- hot-user penalty
- delivery strategy
- ranking reason

The scoring is intentionally simple so the delivery and caching behavior stays easy to inspect.

## API

Implemented endpoints:

```http
POST   /api/posts
GET    /api/posts/{postId}
GET    /api/feed/home?userId=...&cursor=...&limit=...
GET    /api/feed/user/{userId}?cursor=...&limit=...
POST   /api/follows/{userId}?followerId=...
DELETE /api/follows/{userId}?followerId=...
GET    /api/health
```

Operational endpoints:

```http
GET    /actuator/health
GET    /actuator/metrics
GET    /actuator/prometheus
```

## Frontend

The Angular app is a systems playground, not just a thin feed viewer.

Current UI behavior:

- switch active viewer
- switch posting author
- switch viewed timeline user
- create posts
- follow and unfollow users
- load more home feed pages
- load more user feed pages
- inspect delivery flow panels for publish and read paths
- view ranking score, ranking reason, and delivery strategy on feed items

The frontend talks directly to the Spring Boot API at `http://localhost:8080/api`.

## Project Structure

```text
feedme/
|-- backend/
|   |-- src/main/java/com/feedme/backend/
|   |-- src/main/resources/
|   |-- Dockerfile
|   `-- pom.xml
|-- frontend/
|   |-- src/
|   |-- Dockerfile
|   `-- package.json
|-- docs/
|-- infra/
|   |-- grafana/
|   `-- prometheus/
|-- docker-compose.yml
`-- README.md
```

## Local Run

### Option 1: run everything with Docker Compose

```bash
docker compose up --build
```

Services:

- frontend: `http://localhost:4200`
- backend: `http://localhost:8080`
- postgres: `localhost:5432`
- redis: `localhost:6379`
- prometheus: `http://localhost:9090`
- grafana: `http://localhost:3000`

### Option 2: run backend and frontend separately

Start infrastructure:

```bash
docker compose up postgres redis prometheus grafana
```

Run backend:

```bash
cd backend
mvn spring-boot:run
```

Run frontend:

```bash
cd frontend
npm install
npm start
```

If browser reload behaves poorly on your machine, use:

```bash
ng serve --no-hmr
```

## Observability

The backend now exposes Micrometer metrics through Spring Boot Actuator and Prometheus.

Prometheus scrape endpoint:

```http
GET http://localhost:8080/actuator/prometheus
```

Prometheus is configured with two backend targets so it works in both workflows without edits:

- `backend:8080` when backend runs in Docker Compose
- `host.docker.internal:8080` when backend runs locally on your host

If one backend mode is not running, that target will appear `DOWN` in Prometheus and this is expected.
If both are running at the same time, you will see duplicate metric series unless you filter by `instance` in dashboard queries.

Local tools:

- Prometheus UI: `http://localhost:9090`
- Grafana UI: `http://localhost:3000`
- Grafana default login: `admin / admin`

Grafana boots with Prometheus preconfigured as the default datasource.
It also provisions a `FeedMe Observability` dashboard automatically on startup.

Current metric families include:

- home feed requests and latency
- home and user requested page sizes
- user feed requests and latency
- home feed cache lookup outcomes
- home feed merge modes
- follow and unfollow request counts and latency
- post creation latency
- idempotency outcomes
- service error counts by operation and status
- delivery path selection
- cache mutation counts
- async pipeline errors (publisher/consumer service-error tags)
- async worker retry/dead-letter counters

Provisioned observability assets:

- Grafana dashboard: `FeedMe / FeedMe Observability`
- Prometheus alert rules:
  - `FeedHomeLatencyHigh`
  - `FeedHomeCacheHitRatioLow`
  - `FeedServiceErrorsSpiking`
  - `IdempotencyConflictsDetected`

## Seed Data

The backend seeds:

- demo users
- follow relationships
- demo posts

This makes the home feed and user timelines immediately usable after startup.

## Useful Endpoints To Try

Health:

```http
GET http://localhost:8080/api/health
```

Home feed:

```http
GET http://localhost:8080/api/feed/home?userId=u1&limit=5
```

Home feed next page:

```http
GET http://localhost:8080/api/feed/home?userId=u1&cursor=<cursor>&limit=5
```

User timeline:

```http
GET http://localhost:8080/api/feed/user/u2?limit=5
```

Create post:

```http
POST http://localhost:8080/api/posts
Content-Type: application/json

{
  "idempotencyKey": "create-post-u1-001",
  "authorId": "u1",
  "content": "Home feed pagination should stay stable while new posts arrive."
}
```

Follow:

```http
POST http://localhost:8080/api/follows/u2?followerId=u1
```

Unfollow:

```http
DELETE http://localhost:8080/api/follows/u2?followerId=u1
```

## Testing

Backend tests:

```bash
cd backend
mvn test
```

The test suite currently covers:

- pagination behavior
- normal-user feed delivery
- hot-user read-time merge behavior
- idempotent post creation
- outbox event persistence on post create
- Prometheus metric exposure

## Current Architecture Summary

Today, the system is intentionally simple but already follows real feed design patterns:

- PostgreSQL is the source of truth
- Redis accelerates first-page home-feed reads
- outbox events bridge write path to async propagation
- normal-user fan-out is processed by async worker
- hot users do not trigger follower-by-follower invalidation
- home feed merges cached base content with fresh hot-user content
- post creation is idempotent under retried requests
- Prometheus and Grafana are part of the local stack
- pagination is keyset-based and stable under new writes

