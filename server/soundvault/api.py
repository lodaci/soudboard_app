"""API HTTP (FastAPI) protetta da token."""
from __future__ import annotations

import asyncio
import hmac
import shutil
import subprocess
import threading
import time
from collections import defaultdict
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.responses import FileResponse

from . import __version__, indexer
from .config import Config, load_tokens

# formati che conviene convertire per il telefono (pesanti / poco compatibili)
HEAVY = {"wav", "flac", "aif", "aiff", "wma"}

MIME = {
    "mp3": "audio/mpeg", "ogg": "audio/ogg", "oga": "audio/ogg", "opus": "audio/ogg",
    "wav": "audio/wav", "flac": "audio/flac", "m4a": "audio/mp4", "aac": "audio/aac",
    "aif": "audio/aiff", "aiff": "audio/aiff", "wma": "audio/x-ms-wma",
}


def create_app(cfg: Config) -> FastAPI:
    conn = indexer.connect(cfg)
    db_lock = threading.Lock()
    failures: dict[str, int] = defaultdict(int)
    indexing = {"running": False, "last": None}

    def client_ip(request: Request) -> str:
        ip = request.client.host if request.client else "?"
        fwd = request.headers.get("x-forwarded-for")
        if fwd and ip in ("127.0.0.1", "::1"):  # dietro Caddy
            ip = fwd.split(",")[0].strip()
        return ip

    async def require_token(request: Request) -> str:
        auth = request.headers.get("authorization", "")
        given = auth[7:].strip() if auth.lower().startswith("bearer ") else request.query_params.get("token", "")
        ip = client_ip(request)
        if given:
            for t in load_tokens(cfg):
                if hmac.compare_digest(given.encode(), t["token"].encode()):
                    failures.pop(ip, None)
                    return t["name"]
        failures[ip] += 1
        # rallenta chi prova a indovinare il token
        await asyncio.sleep(min(0.2 * (2 ** min(failures[ip], 5)), 5.0))
        raise HTTPException(status_code=401, detail="Token mancante o non valido", headers={"WWW-Authenticate": "Bearer"})

    def run_index():
        if indexing["running"]:
            return
        indexing["running"] = True
        try:
            with db_lock:
                indexing["last"] = indexer.index(cfg, conn)
        finally:
            indexing["running"] = False

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        # all'avvio aggiorna l'indice in background: il server risponde subito
        threading.Thread(target=run_index, daemon=True).start()
        yield

    app = FastAPI(title="SoundVault", version=__version__, docs_url=None, redoc_url=None, lifespan=lifespan)

    @app.get("/api/v1/health")
    def health(user: str = Depends(require_token)):
        with db_lock:
            total = conn.execute("SELECT COUNT(*) FROM sounds").fetchone()[0]
        return {
            "status": "ok", "version": __version__, "sounds": total, "user": user,
            "indexing": indexing["running"], "ffmpeg": ffmpeg_path(cfg) is not None,
        }

    @app.get("/api/v1/categories")
    def get_categories(_: str = Depends(require_token)):
        with db_lock:
            return {"categories": indexer.categories(conn)}

    @app.get("/api/v1/search")
    def get_search(
        q: str = "",
        page: int = Query(1, ge=1),
        page_size: int = Query(30, ge=1, le=100),
        collection: str = "",
        category: str = "",
        _: str = Depends(require_token),
    ):
        with db_lock:
            return indexer.search(conn, q, page, page_size, collection, category)

    @app.post("/api/v1/reindex")
    def reindex(_: str = Depends(require_token)):
        threading.Thread(target=run_index, daemon=True).start()
        return {"started": True}

    @app.get("/api/v1/files/{sound_id}")
    def get_file(sound_id: int, compact: bool = False, _: str = Depends(require_token)):
        with db_lock:
            row = conn.execute("SELECT path, title, format, mtime FROM sounds WHERE id=?", (sound_id,)).fetchone()
        if row is None:
            raise HTTPException(404, "Suono non trovato")
        path = Path(row["path"])
        if not path.exists():
            raise HTTPException(410, "File non più presente sul disco")
        fmt = row["format"]
        if compact and fmt in HEAVY:
            mp3 = transcode(cfg, sound_id, path, row["mtime"])
            if mp3 is not None:
                return FileResponse(mp3, media_type="audio/mpeg", filename=f"{row['title']}.mp3")
        return FileResponse(path, media_type=MIME.get(fmt, "application/octet-stream"), filename=path.name)

    return app


def ffmpeg_path(cfg: Config) -> str | None:
    if cfg.ffmpeg and Path(cfg.ffmpeg).exists():
        return cfg.ffmpeg
    return shutil.which("ffmpeg")


_transcode_locks: dict[int, threading.Lock] = defaultdict(threading.Lock)


def transcode(cfg: Config, sound_id: int, src: Path, mtime: float) -> Path | None:
    """Converte in MP3 (VBR ~190 kbps) e tiene il risultato in cache."""
    ff = ffmpeg_path(cfg)
    if ff is None:
        return None
    cfg.cache_dir.mkdir(parents=True, exist_ok=True)
    out = cfg.cache_dir / f"{sound_id}_{int(mtime)}.mp3"
    with _transcode_locks[sound_id]:
        if out.exists() and out.stat().st_size > 0:
            return out
        tmp = out.with_suffix(".part.mp3")
        t0 = time.time()
        proc = subprocess.run(
            [ff, "-y", "-loglevel", "error", "-i", str(src), "-vn", "-codec:a", "libmp3lame", "-q:a", "2", str(tmp)],
            capture_output=True,
        )
        if proc.returncode != 0 or not tmp.exists():
            tmp.unlink(missing_ok=True)
            return None
        tmp.replace(out)
        print(f"Convertito {src.name} in MP3 ({time.time() - t0:.1f} s)")
        return out
