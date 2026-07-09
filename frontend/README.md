# FeedMe Frontend

This folder contains the FeedMe frontend built with React, TypeScript, and Vite.

## Scripts

```bash
npm install
npm run dev
npm run build
npm run lint
```

## Local Dev

The Vite dev server runs on `http://localhost:5173` by default.

API requests use the `/api` prefix and are proxied to `http://localhost:8080` during development. See [vite.config.ts](/C:/Users/tramb/OneDrive/Documents/ShahBytes/FeedMe/frontend/vite.config.ts:1).

## UI Capabilities

- switch the active viewer
- switch the posting author
- browse followed and suggested users
- follow and unfollow users
- create new posts
- view the home feed
- view a selected user's timeline
- paginate home and user timelines

## Key Files

- [src/App.tsx](/C:/Users/tramb/OneDrive/Documents/ShahBytes/FeedMe/frontend/src/App.tsx:1)
- [src/components/AppShell.tsx](/C:/Users/tramb/OneDrive/Documents/ShahBytes/FeedMe/frontend/src/components/AppShell.tsx:1)
- [src/lib/api.ts](/C:/Users/tramb/OneDrive/Documents/ShahBytes/FeedMe/frontend/src/lib/api.ts:1)
