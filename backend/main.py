import json
import os
import tempfile
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Header, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

APP_DIR = Path(__file__).resolve().parents[1]
DATA_FILE = Path(os.getenv("CIFLIX_CATALOG_FILE", str(APP_DIR / "data" / "catalog.json")))
ADMIN_TOKEN = os.getenv("CIFLIX_ADMIN_TOKEN", "")
app = FastAPI(title="CifliX JSON Catalog API", version="1.0.0")
origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "*").split(",") if x.strip()]
app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=False,
                   allow_methods=["GET", "POST", "DELETE", "OPTIONS"], allow_headers=["*"])

def read_catalog() -> list[dict[str, Any]]:
    if not DATA_FILE.exists():
        return []
    try:
        payload = json.loads(DATA_FILE.read_text(encoding="utf-8"))
        rows = payload if isinstance(payload, list) else payload.get("items", [])
        return rows if isinstance(rows, list) else []
    except (OSError, json.JSONDecodeError):
        raise HTTPException(status_code=500, detail="Catalog JSON file is invalid")

def write_catalog(items: list[dict[str, Any]]) -> None:
    DATA_FILE.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix="catalog-", suffix=".json", dir=str(DATA_FILE.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump({"version": 1, "items": items}, handle, ensure_ascii=False, indent=2)
        os.replace(temp_name, DATA_FILE)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)

def normalize_item(raw: dict[str, Any], index: int = 0) -> dict[str, Any]:
    title = str(raw.get("title") or raw.get("name") or "").strip()
    if not title:
        raise HTTPException(status_code=422, detail=f"Item {index + 1} has no title")
    raw_type = str(raw.get("type") or "movie").lower()
    kind = "series" if raw_type in ("series", "tv", "مسلسل", "2") else ("anime" if raw_type in ("anime", "أنمي") else "movie")
    episodes = raw.get("episodes") or raw.get("seasons") or []
    if not isinstance(episodes, list):
        raise HTTPException(status_code=422, detail=f"Episodes for '{title}' must be an array")
    return {
        "id": str(raw.get("id") or raw.get("nb") or f"ciflix-{index+1}"),
        "title": title,
        "type": kind,
        "genre": raw.get("genre") or (raw.get("genres", [""])[0] if isinstance(raw.get("genres"), list) and raw.get("genres") else ""),
        "year": raw.get("year") or raw.get("release_year"),
        "rating": raw.get("rating") or raw.get("stars"),
        "poster": raw.get("poster") or raw.get("poster_url") or raw.get("image") or "",
        "backdrop": raw.get("backdrop") or raw.get("cover") or "",
        "overview": raw.get("overview") or raw.get("description") or raw.get("desc") or "",
        "video": raw.get("video") or raw.get("url") or raw.get("stream") or "",
        "streams": raw.get("streams") if isinstance(raw.get("streams"), list) else [],
        "subtitles": raw.get("subtitles") if isinstance(raw.get("subtitles"), list) else [],
        "episodes": episodes,
        "featured": bool(raw.get("featured", False)),
    }

def admin_required(token: str | None) -> None:
    if not ADMIN_TOKEN:
        raise HTTPException(status_code=503, detail="Set CIFLIX_ADMIN_TOKEN on the server before using admin endpoints")
    if token != ADMIN_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid admin token")

@app.get("/api/health")
async def health():
    return {"ok": True, "service": "CifliX JSON Catalog API", "catalog_items": len(read_catalog())}

@app.get("/api/home")
async def home(section: str = Query("latest-movies", pattern="^(latest-movies|latest-series|popular|popular-movies|popular-series|featured)$"),
               page: int = Query(1, ge=1), limit: int = Query(24, ge=1, le=60)):
    items = read_catalog()
    if section in ("latest-movies", "popular-movies"):
        items = [x for x in items if x.get("type") == "movie"]
    elif section in ("latest-series", "popular-series"):
        items = [x for x in items if x.get("type") in ("series", "anime")]
    elif section == "featured":
        items = [x for x in items if x.get("featured")]
    if section.startswith("popular"):
        items = sorted(items, key=lambda x: float(x.get("rating") or 0), reverse=True)
    else:
        items = list(reversed(items))
    start = (page - 1) * limit
    return {"items": items[start:start + limit], "page": page, "total": len(items), "section": section}

@app.get("/api/search")
async def search(q: str = Query(..., min_length=1, max_length=120), page: int = Query(1, ge=1)):
    term = q.strip().casefold()
    found = [x for x in read_catalog() if term in str(x.get("title", "")).casefold()
             or term in str(x.get("overview", "")).casefold()
             or term in str(x.get("genre", "")).casefold()]
    start = (page - 1) * 30
    return {"items": found[start:start + 30], "page": page, "total": len(found), "query": q}

@app.get("/api/title/{item_id}")
async def title_detail(item_id: str):
    item = next((x for x in read_catalog() if str(x.get("id")) == item_id), None)
    if not item:
        raise HTTPException(status_code=404, detail="Title not found")
    return item

@app.get("/api/title/{item_id}/streams")
async def title_streams(item_id: str, episode_id: str | None = None):
    item = next((x for x in read_catalog() if str(x.get("id")) == item_id), None)
    if not item:
        raise HTTPException(status_code=404, detail="Title not found")
    streams = item.get("streams") or ([{"url": item.get("video"), "quality": "Auto"}] if item.get("video") else [])
    if episode_id:
        episode = next((ep for ep in item.get("episodes", []) if str(ep.get("id")) == episode_id), None)
        if not episode:
            raise HTTPException(status_code=404, detail="Episode not found")
        streams = episode.get("streams") or ([{"url": episode.get("video") or episode.get("url"), "quality": "Auto"}] if episode.get("video") or episode.get("url") else [])
        subtitles = episode.get("subtitles", item.get("subtitles", []))
    else:
        subtitles = item.get("subtitles", [])
    valid = [s for s in streams if isinstance(s, dict) and str(s.get("url", "")).startswith(("https://", "http://"))]
    return {"streams": valid, "subtitles": subtitles}

@app.post("/api/admin/catalog/import")
async def import_catalog(payload: dict[str, Any], x_ciflix_admin_token: str | None = Header(default=None)):
    admin_required(x_ciflix_admin_token)
    rows = payload if isinstance(payload, list) else payload.get("items")
    if not isinstance(rows, list):
        raise HTTPException(status_code=422, detail="JSON must be an array or an object containing 'items'")
    normalized = [normalize_item(x, i) for i, x in enumerate(rows) if isinstance(x, dict)]
    if len(normalized) != len(rows):
        raise HTTPException(status_code=422, detail="Every item must be a JSON object")
    existing = {str(x.get("id")): x for x in read_catalog()}
    for item in normalized:
        existing[item["id"]] = item
    merged = list(existing.values())
    write_catalog(merged)
    return {"ok": True, "imported": len(normalized), "total": len(merged)}

@app.post("/api/admin/catalog")
async def add_or_update_title(item: dict[str, Any], x_ciflix_admin_token: str | None = Header(default=None)):
    admin_required(x_ciflix_admin_token)
    normalized = normalize_item(item)
    items = read_catalog()
    items = [x for x in items if str(x.get("id")) != normalized["id"]]
    items.append(normalized)
    write_catalog(items)
    return {"ok": True, "item": normalized}

@app.delete("/api/admin/catalog/{item_id}")
async def delete_title(item_id: str, x_ciflix_admin_token: str | None = Header(default=None)):
    admin_required(x_ciflix_admin_token)
    items = read_catalog()
    remaining = [x for x in items if str(x.get("id")) != item_id]
    if len(remaining) == len(items):
        raise HTTPException(status_code=404, detail="Title not found")
    write_catalog(remaining)
    return {"ok": True, "total": len(remaining)}

# Keep static site serving available when the frontend is deployed beside this API.
if (APP_DIR / "index.html").exists():
    app.mount("/", StaticFiles(directory=str(APP_DIR), html=True), name="frontend")
