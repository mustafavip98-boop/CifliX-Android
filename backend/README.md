# CifliX Cinemana Connector

A small FastAPI adapter based on the supplied ShabakatyCinemanaProvider.kt endpoint patterns.

## Run locally
```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

Health: `GET /api/health`

## Endpoints
- `GET /api/home?section=latest-movies&page=1`
- `GET /api/home?section=latest-series&page=1`
- `GET /api/home?section=popular&page=1`
- `GET /api/home?section=popular-movies&page=1`
- `GET /api/home?section=popular-series&page=1`
- `GET /api/search?q=...`
- `GET /api/title/{id}`
- `GET /api/title/{id}/streams`
- `GET /api/title/{id}/streams?episode_id={episodeId}`

## Important
- The upstream API host and route patterns were read from the user-supplied Kotlin provider source. The live upstream could not be verified from this environment; runtime responses, availability, and upstream policy may change.
- This backend is not an open proxy; it calls only fixed route templates on the configured upstream host.
- Stream URLs are returned to the browser, not re-hosted. The browser may not play every format. HLS playback support is included in the frontend via hls.js fallback.
- Deploy the backend on HTTPS. Set `CORS_ORIGINS` to the exact public frontend origin before production.
- Do not expose admin secrets or add unauthorized/DRM bypass functionality.
