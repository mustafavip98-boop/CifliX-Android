# CifliX JSON Catalog

CifliX reads its movies and series from a JSON catalogue. The API never scrapes or rehosts third-party media; add only artwork and playback URLs you are authorized to use.

## Required server environment

- `CIFLIX_ADMIN_TOKEN`: long random secret used by the admin panel.
- `CIFLIX_CATALOG_FILE`: optional path for the catalogue file. Defaults to `data/catalog.json` inside the project.

Deploy the FastAPI service using `uvicorn backend.main:app --host 0.0.0.0 --port $PORT`. Configure a persistent disk/volume at the directory containing the catalogue file; otherwise a host restart or redeploy may reset a local JSON file. For production, back up the catalogue regularly.

## Admin workflow

1. Open the admin page.
2. Enter the API base URL (the domain, without `/api`) and the same `CIFLIX_ADMIN_TOKEN` configured on the server.
3. Paste JSON or upload a `.json` file.
4. Review the imported titles, then select **مزامنة المكتبة** to send the catalogue to the API.

The import endpoint merges by item ID and keeps existing entries that are not included in the import.

## JSON example

See `catalog.example.json`.

Supported top-level shapes: an array of title objects, or an object containing an `items` array.

Movie fields: `id`, `title`, `type: "movie"`, `genre`, `year`, `rating`, `poster`, `backdrop`, `overview`, `featured`, `video`, `streams[]`, `subtitles[]`.

Series fields: all common fields plus `episodes[]`. Each episode can contain `id`, `season`, `episode`, `title`, `video`, `streams[]`, and `subtitles[]`.

Playback URLs should be HTTPS HLS (`.m3u8`) or MP4 links that work in Android Media3. Subtitle links should use a supported WebVTT or SRT format. The current Android player starts the first available stream; quality selection and subtitle selection are still planned work.
