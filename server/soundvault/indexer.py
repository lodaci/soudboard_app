"""Indicizzazione delle cartelle audio in SQLite + FTS5."""
from __future__ import annotations

import re
import sqlite3
import time
from pathlib import Path

from .config import Config

AUDIO_EXT = {".wav", ".mp3", ".ogg", ".oga", ".flac", ".m4a", ".aac", ".opus", ".aif", ".aiff", ".wma"}

SCHEMA = """
CREATE TABLE IF NOT EXISTS sounds (
    id INTEGER PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    collection TEXT NOT NULL,
    category TEXT NOT NULL,
    title TEXT NOT NULL,
    keywords TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    size INTEGER NOT NULL,
    mtime REAL NOT NULL,
    format TEXT NOT NULL
);
CREATE VIRTUAL TABLE IF NOT EXISTS sounds_fts USING fts5(
    title, keywords, collection, category,
    content='sounds', content_rowid='id',
    tokenize='unicode61 remove_diacritics 2'
);
CREATE TRIGGER IF NOT EXISTS sounds_ai AFTER INSERT ON sounds BEGIN
    INSERT INTO sounds_fts(rowid, title, keywords, collection, category)
    VALUES (new.id, new.title, new.keywords, new.collection, new.category);
END;
CREATE TRIGGER IF NOT EXISTS sounds_ad AFTER DELETE ON sounds BEGIN
    INSERT INTO sounds_fts(sounds_fts, rowid, title, keywords, collection, category)
    VALUES ('delete', old.id, old.title, old.keywords, old.collection, old.category);
END;
CREATE TRIGGER IF NOT EXISTS sounds_au AFTER UPDATE ON sounds BEGIN
    INSERT INTO sounds_fts(sounds_fts, rowid, title, keywords, collection, category)
    VALUES ('delete', old.id, old.title, old.keywords, old.collection, old.category);
    INSERT INTO sounds_fts(rowid, title, keywords, collection, category)
    VALUES (new.id, new.title, new.keywords, new.collection, new.category);
END;
"""


def connect(cfg: Config) -> sqlite3.Connection:
    cfg.data_dir.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(cfg.db_path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.executescript(SCHEMA)
    return conn


_CAMEL = re.compile(r"(?<=[a-z])(?=[A-Z])")
_SPLIT = re.compile(r"[^0-9A-Za-zÀ-ÿ]+")


def words(text: str) -> list[str]:
    """'SWORD_Metal-Clash01' -> ['sword', 'metal', 'clash']"""
    text = _CAMEL.sub(" ", text)
    out = []
    for w in _SPLIT.split(text):
        w = re.sub(r"\d+", "", w).lower()
        if len(w) > 1:
            out.append(w)
    return out


def pretty_title(stem: str) -> str:
    t = re.sub(r"[_]+", " ", stem)
    t = re.sub(r"\s+", " ", t).strip()
    return t or stem


def duration_ms(path: Path) -> int:
    try:
        import mutagen

        f = mutagen.File(path)
        if f is not None and f.info is not None and getattr(f.info, "length", None):
            return int(f.info.length * 1000)
    except Exception:
        pass
    if path.suffix.lower() == ".wav":
        try:
            import wave

            with wave.open(str(path)) as w:
                return int(w.getnframes() * 1000 / w.getframerate())
        except Exception:
            pass
    return 0


def describe(root: Path, path: Path) -> dict:
    rel = path.relative_to(root)
    parts = rel.parts
    collection = parts[0] if len(parts) > 1 else root.name
    category = parts[1] if len(parts) > 2 else ""
    kw = []
    for p in parts[:-1]:
        kw += words(p)
    kw += words(path.stem)
    tags = ""
    try:
        import mutagen

        f = mutagen.File(path, easy=True)
        if f is not None and f.tags:
            tags = " ".join(str(v) for k in ("title", "comment", "genre") for v in (f.tags.get(k) or []))
    except Exception:
        pass
    kw += words(tags)
    return {
        "collection": collection,
        "category": category,
        "title": pretty_title(path.stem),
        "keywords": " ".join(dict.fromkeys(kw)),
        "format": path.suffix.lower().lstrip("."),
    }


def index(cfg: Config, conn: sqlite3.Connection | None = None, log=print) -> dict:
    """Aggiorna l'indice: aggiunge i file nuovi/modificati, rimuove quelli spariti."""
    own = conn is None
    conn = conn or connect(cfg)
    t0 = time.time()
    existing = {r["path"]: (r["id"], r["mtime"], r["size"]) for r in conn.execute("SELECT id, path, mtime, size FROM sounds")}
    seen: set[str] = set()
    added = updated = 0
    for root in cfg.folders:
        if not root.exists():
            log(f"  attenzione: cartella inesistente {root}")
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in AUDIO_EXT:
                continue
            key = str(path.resolve())
            seen.add(key)
            st = path.stat()
            old = existing.get(key)
            if old and abs(old[1] - st.st_mtime) < 1e-6 and old[2] == st.st_size:
                continue
            meta = describe(root, path)
            row = (meta["collection"], meta["category"], meta["title"], meta["keywords"],
                   duration_ms(path), st.st_size, st.st_mtime, meta["format"])
            if old:
                conn.execute(
                    "UPDATE sounds SET collection=?, category=?, title=?, keywords=?, duration_ms=?, size=?, mtime=?, format=? WHERE id=?",
                    row + (old[0],),
                )
                updated += 1
            else:
                conn.execute(
                    "INSERT INTO sounds(collection, category, title, keywords, duration_ms, size, mtime, format, path) VALUES (?,?,?,?,?,?,?,?,?)",
                    row + (key,),
                )
                added += 1
            if (added + updated) % 500 == 0:
                conn.commit()
                log(f"  … {added + updated} file elaborati")
    removed = 0
    for key, (sid, _, _) in existing.items():
        if key not in seen:
            conn.execute("DELETE FROM sounds WHERE id=?", (sid,))
            removed += 1
    conn.commit()
    total = conn.execute("SELECT COUNT(*) FROM sounds").fetchone()[0]
    if own:
        conn.close()
    stats = {"added": added, "updated": updated, "removed": removed, "total": total, "seconds": round(time.time() - t0, 1)}
    log(f"Indice aggiornato: +{added} nuovi, {updated} modificati, -{removed} rimossi, {total} totali ({stats['seconds']} s)")
    return stats


def fts_query(q: str) -> str:
    """Testo libero -> query FTS5 sicura: ogni parola come prefisso, tutte richieste."""
    toks = [t for t in re.split(r"[^0-9A-Za-zÀ-ÿ]+", q.lower()) if t]
    return " AND ".join(f'"{t}"*' for t in toks)


def search(conn: sqlite3.Connection, q: str = "", page: int = 1, page_size: int = 30,
           collection: str = "", category: str = "") -> dict:
    page = max(1, page)
    page_size = max(1, min(100, page_size))
    where, args = [], []
    if collection:
        where.append("s.collection = ?")
        args.append(collection)
    if category:
        where.append("s.category = ?")
        args.append(category)
    match = fts_query(q)
    if match:
        base = "FROM sounds_fts f JOIN sounds s ON s.id = f.rowid WHERE sounds_fts MATCH ?"
        args = [match] + args
        order = "ORDER BY bm25(sounds_fts, 10.0, 3.0, 1.0, 2.0), s.title"
    else:
        base = "FROM sounds s WHERE 1=1"
        order = "ORDER BY s.collection, s.category, s.title"
    if where:
        base += " AND " + " AND ".join(where)
    total = conn.execute(f"SELECT COUNT(*) {base}", args).fetchone()[0]
    rows = conn.execute(
        f"SELECT s.id, s.title, s.collection, s.category, s.duration_ms, s.size, s.format {base} {order} LIMIT ? OFFSET ?",
        args + [page_size, (page - 1) * page_size],
    ).fetchall()
    return {"results": [dict(r) for r in rows], "page": page, "page_size": page_size, "total": total}


def categories(conn: sqlite3.Connection) -> list[dict]:
    rows = conn.execute(
        "SELECT collection, category, COUNT(*) AS count FROM sounds GROUP BY collection, category ORDER BY collection, category"
    ).fetchall()
    return [dict(r) for r in rows]
