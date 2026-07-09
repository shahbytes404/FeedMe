# FeedMe

FeedMe is a full-stack social feed application built to demonstrate how modern feed systems work across backend, frontend, infrastructure, and observability.

The project includes:

- a Spring Boot `4.0.6` backend running on Java `17`
- a React `19.1.0` frontend built with TypeScript `5.9` and Vite `7`
- PostgreSQL `17` for persistent storage
- Redis `7` for caching and stream-based feed processing
- Nginx for serving the frontend in containers
- Docker Compose for local orchestration
- Prometheus `3.2.1` and Grafana `11.4.6` for monitoring

## Architecture

The app is split into a few main parts:

- `backend/`
  Spring Boot API for users, posts, follows, timelines, caching, and metrics.
- `frontend/`
  React dashboard for browsing feeds, following users, and creating posts.
- `infra/`
  Infrastructure and monitoring-related configuration.
- `docker-compose.yml`
  Starts the app stack locally with backend, frontend, PostgreSQL, Redis, Prometheus, and Grafana.

## Tech Stack

### Backend

- Spring Boot `4.0.6`
- Java `17`
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Spring Cache
- Spring Data Redis
- Micrometer + Prometheus
- Maven

### Frontend

- React `19.1.0`
- React DOM `19.1.0`
- React Router DOM `7.6.3`
- TypeScript `5.9.2`
- Vite `7.0.0`
- Vitest

### Infrastructure

- PostgreSQL `17`
- Redis `7`
- Nginx
- Docker
- Docker Compose
- Prometheus `3.2.1`
- Grafana `11.4.6`

## Main Features

- user listing and profile browsing
- home feed and user feed timelines
- follow and unfollow flows
- post creation
- paginated feed loading
- Redis-backed feed caching
- Prometheus metrics endpoint
- Grafana dashboards through the monitoring stack

## Local Development

### 1. Start the full stack with Docker Compose

```bash
docker compose up --build
```

This starts:

- frontend on `http://localhost:4200`
- backend on `http://localhost:8080`
- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- Prometheus on `http://localhost:9090`
- Grafana on `http://localhost:3000`

Grafana default credentials:

```text
username: admin
password: admin
```

### 2. Run backend separately

From the `backend` folder:

```bash
./mvnw spring-boot:run
```

or on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

### 3. Run frontend separately

From the `frontend` folder:

```bash
npm install
npm run dev
```

The Vite dev server proxies `/api` requests to the backend on port `8080`.

## Backend API Areas

The backend exposes endpoints for:

- `/api/users`
- `/api/posts`
- `/api/follows`
- `/api/feed/home`
- `/api/feed/user/{userId}`

These can be tested in Postman before wiring up the frontend.

## Frontend Flow

The main frontend flow is:

1. `src/main.tsx` mounts the React app.
2. `src/App.tsx` handles routing, loading, and error boundaries.
3. `src/components/AppShell.tsx` renders the dashboard layout.
4. `src/hooks/use-feedme-dashboard.ts` manages data fetching and UI state.
5. `src/lib/api.ts` sends requests to the backend.

## Monitoring

The backend exposes Prometheus-compatible metrics through Spring Boot Actuator and Micrometer.

The monitoring stack includes:

- Prometheus for scraping and storing metrics
- Grafana for dashboards and visualization

## Why This Project Exists

FeedMe is designed as a learning project for:

- full-stack application structure
- modern React patterns
- Spring Boot API design
- caching with Redis
- containerized local environments
- observability with Prometheus and Grafana

## Notes

- The frontend uses React `19.1.0`, which makes this a good project for exploring modern React APIs.
- The app includes both local development flow and containerized deployment flow.
- Postman is useful for validating backend endpoints before connecting the React UI.
