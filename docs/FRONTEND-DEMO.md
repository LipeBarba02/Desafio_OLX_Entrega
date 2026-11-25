# Next.js frontend — demonstrator, not part of OLX challenge

## Run (Windows PowerShell)

From repository root:

```powershell
docker compose --profile demo up -d --build web
```

Open http://localhost:3001. This is **browser-only demo mode**: seeded ads, category filtering, search, pagination, details, statuses, rejection reason, and new-ad form. Data persists in browser localStorage. No backend call and no real moderation occurs. Reset via "Restaurar exemplos".

Alternatively:

```powershell
cd apps/web
npm install
npm run dev -- -p 3001
```

## Backend mode (future)

Set `NEXT_PUBLIC_API_BASE_URL` to the browser-accessible API origin (for example `http://localhost:8088`) **at build time**. It currently expects:

- `GET /api/ads`: `Ad[]`, `{ content: Ad[] }`, or `{ items: Ad[] }`.
- `POST /api/ads`: accepts `{title,description,price,category,images:[url]}` and returns an `Ad`.

`Ad` shape: `{id,title,description,price,category,images,status,reason?,createdAt}`.

This is a **provisional frontend adapter contract**, not an OLX specification. It must be aligned to the Java API once written. Browser CORS must also be handled, or a same-origin proxy introduced.

The demo filters and paginates locally. Backend mode currently fetches one list and filters locally: server pagination/filtering will be wired during the API lesson. No claim of completed end-to-end integration.

## NGINX load balancer (future)

`platform/load-balancer/nginx.conf` and the `edge` Compose profile define two Gateway instances behind NGINX on port 8088. **Do not enable the edge profile yet**: Java application entry points do not exist and those containers cannot start. This profile is a prepared laboratory, not a verified deployment.

## Distinguishing project scope

The official OLX challenge evaluates the backend. Next.js, NGINX, Gateway, Kafka and favorites are educational extensions.
