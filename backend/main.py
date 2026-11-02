import os
import asyncio
from typing import Any
from urllib.parse import quote
from pathlib import Path

import httpx
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

UPSTREAM = "https://cinemana.shabakaty.cc"
API = f"{UPSTREAM}/api/android"
TIMEOUT = httpx.Timeout(20.0, connect=10.0)
app = FastAPI(title="CifliX Cinemana Connector", version="0.1.0")
origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "*").split(",") if x.strip()]
app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=False, allow_methods=["GET", "OPTIONS"], allow_headers=["*"])

async def upstream_json(path: str) -> Any:
    url = f"{API}/{path.lstrip('/')}"
    headers = {"User-Agent": "CifliX/0.1 (web client)", "Referer": UPSTREAM + "/", "Accept": "application/json, text/plain, */*"}
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT, follow_redirects=True) as client:
            response = await client.get(url, headers=headers)
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as exc:
        raise HTTPException(status_code=502, detail=f"Upstream returned HTTP {exc.response.status_code}")
    except (httpx.RequestError, ValueError) as exc:
        raise HTTPException(status_code=502, detail=f"Could not read upstream JSON: {type(exc).__name__}")

def content_item(item: dict) -> dict | None:
    item_id = item.get("nb")
    if item_id is None: return None
    try: kind = int(item.get("kind", 1))
    except (ValueError, TypeError): kind = 1
    return {"id": str(item_id), "title": item.get("en_title") or "بدون عنوان", "poster": item.get("imgObjUrl"), "type": "series" if kind == 2 else "movie"}

@app.get("/api/health")
async def health(): return {"ok": True, "service": "CifliX Cinemana Connector", "upstream": UPSTREAM}

@app.get("/api/home")
async def home(section: str = Query("latest-movies", pattern="^(latest-movies|latest-series|popular|popular-movies|popular-series)$"), page: int = Query(1, ge=1, le=500), limit: int = Query(24, ge=1, le=60)):
    p = page - 1
    if section == "latest-movies": path = f"latestMovies/level/0/itemsPerPage/{limit}/page/{p}"
    elif section == "latest-series": path = f"latestSeries/level/0/itemsPerPage/{limit}/page/{p}"
    else:
        kind = {"popular": "", "popular-movies": "/videoKind/1", "popular-series": "/videoKind/2"}[section]
        path = f"video/V/2/itemsPerPage/{limit}/level/0{kind}/sortParam/desc/pageNumber/{p}"
    data = await upstream_json(path)
    if not isinstance(data, list): raise HTTPException(status_code=502, detail="Unexpected upstream catalogue format")
    return {"items": [x for x in (content_item(i) for i in data if isinstance(i, dict)) if x], "page": page, "section": section}

@app.get("/api/search")
async def search(q: str = Query(..., min_length=1, max_length=120), page: int = Query(1, ge=1, le=3)):
    from datetime import datetime
    year = f"1900,{datetime.now().year}"
    safe_q = quote(q.strip(), safe="")
    p = page - 1
    async def one(kind: str):
        path = f"AdvancedSearch?level=0&page={p}&year={year}&type={kind}&videoTitle={safe_q}"
        data = await upstream_json(path)
        if not isinstance(data, list): return []
        return [x for x in (content_item(i) for i in data if isinstance(i, dict)) if x]
    movies, series = await asyncio.gather(one("movies"), one("series"))
    return {"items": movies + series, "page": page, "query": q}

@app.get("/api/title/{item_id}")
async def title_detail(item_id: str):
    if not item_id or len(item_id) > 100 or "/" in item_id: raise HTTPException(status_code=400, detail="Invalid title id")
    info, seasons = await asyncio.gather(upstream_json(f"allVideoInfo/id/{quote(item_id, safe='')}"), upstream_json(f"videoSeason/id/{quote(item_id, safe='')}"))
    if not isinstance(info, dict): raise HTTPException(status_code=502, detail="Unexpected title details format")
    try: year = int(info.get("year")) if info.get("year") is not None else None
    except (TypeError, ValueError): year = None
    try: rating = float(info.get("stars")) if info.get("stars") is not None else None
    except (TypeError, ValueError): rating = None
    categories = info.get("categories") or []
    genres = [x.get("en_title") for x in categories if isinstance(x, dict) and x.get("en_title")]
    lang_obj = info.get("videoLanguages") or {}
    if isinstance(lang_obj, dict) and lang_obj.get("en_title"): genres.append(lang_obj["en_title"])
    actors = [x.get("name") for x in (info.get("actorsInfo") or []) if isinstance(x, dict) and x.get("name")]
    episodes = []
    if isinstance(seasons, list):
        for ep in seasons:
            if not isinstance(ep, dict) or ep.get("nb") is None: continue
            try: season_no, episode_no = int(ep.get("season") or 1), int(ep.get("episodeNummer") or 1)
            except (TypeError, ValueError): season_no, episode_no = 1, 1
            episodes.append({"id": str(ep["nb"]), "season": season_no, "episode": episode_no, "title": f"الموسم {season_no} - الحلقة {episode_no}"})
    episodes.sort(key=lambda x: (x["season"], x["episode"]), reverse=True)
    return {"id": item_id, "title": info.get("en_title") or "بدون عنوان", "poster": info.get("imgObjUrl"), "year": year, "rating": rating, "likes": info.get("Likes"), "dislikes": info.get("DisLikes"), "overview": info.get("en_content"), "genres": genres, "actors": actors, "episodes": episodes, "type": "series" if episodes else "movie"}

@app.get("/api/title/{item_id}/streams")
async def title_streams(item_id: str, episode_id: str | None = None):
    target = episode_id or item_id
    if not target or len(target) > 100 or "/" in target: raise HTTPException(status_code=400, detail="Invalid stream id")
    videos, subtitles = await asyncio.gather(upstream_json(f"transcoddedFiles/id/{quote(target, safe='')}"), upstream_json(f"translationFiles/id/{quote(target, safe='')}"), return_exceptions=True)
    if isinstance(videos, Exception) or not isinstance(videos, list): raise HTTPException(status_code=502, detail="Stream list unavailable from upstream")
    subtitle_items = []
    if isinstance(subtitles, dict) and isinstance(subtitles.get("translations"), list):
        for sub in subtitles["translations"]:
            if isinstance(sub, dict) and sub.get("file"): subtitle_items.append({"url": sub["file"], "label": " - ".join(str(x) for x in [sub.get("name"), sub.get("extention")] if x)})
    stream_items = []
    for video in videos:
        if isinstance(video, dict) and video.get("videoUrl"):
            stream_items.append({"url": video["videoUrl"], "quality": video.get("resolution") or "Default", "type": "hls" if ".m3u8" in str(video["videoUrl"]).lower() else "auto"})
    return {"streams": stream_items, "subtitles": subtitle_items}

FRONTEND_DIR = Path(__file__).resolve().parents[1]
app.mount("/", StaticFiles(directory=str(FRONTEND_DIR), html=True), name="frontend")
