# FeedMe

FeedMe is a production-style social feed demo built to explore feed delivery, caching, pagination, async propagation, and ranking tradeoffs in a small full-stack app.

This repository has two applications:

- `frontend/`: React 19 + TypeScript + Vite dashboard
- `backend/`: Spring Boot 4 API with PostgreSQL, Redis, and Prometheus metrics

## Stack

- `React 19`
- `TypeScript`
- `Vite`
- `Spring Boot 4.1`
- `Java 17`
- `PostgreSQL`
- `Redis`
- `Micrometer + Prometheus`

## What It Does

- create posts with idempotency protection
- fetch a personalized home feed
- fetch a user timeline
- follow and unfollow users
- use keyset pagination with cursors
- cache the first home-feed page in Redis
- publish post events through an outbox + Redis Streams pipeline
- support a hybrid strategy for hot users
- expose Prometheus-compatible operational metrics
- seed demo users, follows, and posts on first startup

## Project Layout

```text
FeedMe/
|-- backend/
|   |-- pom.xml
|   `-- src/
|-- frontend/
|   |-- package.json
|   `-- src/
`-- README.md
```

## Backend

The backend is a Spring Boot service under `backend/`.

Main API routes:

```http
GET    /api/users
GET    /api/follows?followerId=...
POST   /api/follows/{userId}?followerId=...
DELETE /api/follows/{userId}?followerId=...
POST   /api/posts
GET    /api/posts/{postId}
GET    /api/feed/home?userId=...&cursor=...&limit=...
GET    /api/feed/user/{userId}?cursor=...&limit=...
GET    /actuator/health
GET    /actuator/metrics
GET    /actuator/prometheus
```

Default local configuration is in [backend/src/main/resources/application.yaml](/C:/Users/tramb/OneDrive/Documents/ShahBytes/FeedMe/backend/src/main/resources/application.yaml:1).

Defaults:

- PostgreSQL: `jdbc:postgresql://localhost:5432/feedme`
- DB username: `feedme`
- DB password: `feedme`
- Redis host: `localhost`
- Redis port: `6379`

## Frontend

The frontend is a React/Vite app under `frontend/`.

It uses:

- Vite dev server
- a `/api` proxy to `http://localhost:8080`
- query-param based viewer switching with `?user=<id>`

The API proxy is configured in [frontend/vite.config.ts](/C:/Users/tramb/OneDrive/Documents/ShahBytes/FeedMe/frontend/vite.config.ts:1).

## Run Locally

Start PostgreSQL and Redis first, then run the backend and frontend separately.

Backend:

```bash
cd backend
./mvnw.cmd spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Local URLs:

- frontend: `http://localhost:5173`
- backend: `http://localhost:8080`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`

## Example Requests

Home feed:

```http
GET http://localhost:8080/api/feed/home?userId=u1&limit=5
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

Follow a user:

```http
POST http://localhost:8080/api/follows/u2?followerId=u1
```

## Notes

- The repo currently contains extra `Part 3/` files that do not match the main app structure.
- The frontend README has been replaced with project-specific setup notes.
- I confirmed the frontend production build succeeds with `npm run build`.
