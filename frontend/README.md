# Waitlist Frontend

React 18 + TypeScript + Vite SPA for the waitlist platform.

## Development

```bash
cd frontend
npm install
npm run dev          # starts on http://localhost:5173
```

The Vite dev server proxies all API traffic so there are no CORS issues:
- `/api/public/*` → `http://localhost:8081` (ingestion-service)
- `/api/admin/*` → `http://localhost:8082` (admin-service)

The backend must be running for API calls to work. Start it with `docker compose up -d --build` from the repo root.

## Running with Docker Compose

From the repo root:

```bash
./run.sh     # macOS / Linux
.\run.ps1    # Windows
```

The frontend is served by nginx on **http://localhost:8080**. The same proxy rules apply — nginx routes `/api/public/*` and `/api/admin/*` to the backend services on the internal Docker network.

## Scripts

| Command | Description |
|---|---|
| `npm run dev` | Start dev server on :5173 with HMR |
| `npm run build` | Type-check and produce optimized `dist/` |
| `npm run preview` | Serve `dist/` locally to test the production build |
| `npm run lint` | ESLint (zero warnings tolerated) |
| `npm run typecheck` | TypeScript strict check without emitting |

## Environment variables

Create `frontend/.env.local` to override defaults (optional):

| Variable | Default | Description |
|---|---|---|
| `VITE_INGESTION_BASE` | `/api/public` | Base URL for the public API |
| `VITE_ADMIN_BASE` | `/api/admin` | Base URL for the admin API |

In production the defaults point at relative paths so nginx routes them. In dev the Vite proxy handles the same paths.

## Pages

| Path | Description |
|---|---|
| `/` | Landing page with signup form and weekly top-referrers preview |
| `/leaderboard` | Full leaderboard with all-time / this-week toggle |
| `/profile` | Look up your referral code by email |
| `/admin/login` | Admin login (username: `admin`, password: `admin123`) |
| `/admin` | Protected dashboard — filter, search, bulk-action entries |
| `/admin/entries/:id` | Single entry detail and status transitions |
