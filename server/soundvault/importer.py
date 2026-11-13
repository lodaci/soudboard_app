"""Import di raccolte scaricabili (zip o singoli file) nella libreria."""
from __future__ import annotations

import re
import shutil
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

from .config import Config
from .indexer import AUDIO_EXT

USER_AGENT = "SoundVault/1.0 (+https://github.com)"


def safe_name(name: str) -> str:
    name = re.sub(r'[\\/:*?"<>|\x00-\x1f]+', "_", name).strip(" .")
    return name or "raccolta"


def download(url: str, dest_dir: Path, log=print) -> Path:
    """Scarica con ripresa (se il server supporta Range)."""
    dest_dir.mkdir(parents=True, exist_ok=True)
    name = Path(urllib.parse.unquote(urllib.parse.urlparse(url).path)).name or "download.zip"
    dest = dest_dir / safe_name(name)
    part = dest.with_name(dest.name + ".part")
    if dest.exists():
        log(f"Già scaricato: {dest}")
        return dest
    start = part.stat().st_size if part.exists() else 0
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    if start:
        req.add_header("Range", f"bytes={start}-")
    with urllib.request.urlopen(req, timeout=60) as resp:
        if start and resp.status != 206:
            start = 0  # il server non supporta la ripresa
        total = resp.headers.get("Content-Length")
        total = int(total) + start if total else None
        mode = "ab" if start else "wb"
        done = start
        last = -1
        with open(part, mode) as out:
            while chunk := resp.read(1024 * 1024):
                out.write(chunk)
                done += len(chunk)
                if total:
                    pct = int(done * 100 / total)
                    if pct != last and pct % 5 == 0:
                        log(f"  {pct}% ({done // (1024 * 1024)} MB)")
                        last = pct
    part.replace(dest)
    return dest


def extract_zip(zip_path: Path, target: Path, log=print) -> int:
    """Estrae solo i file audio, proteggendosi da percorsi malevoli (../)."""
    count = 0
    target = target.resolve()
    with zipfile.ZipFile(zip_path) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            rel = Path(info.filename.replace("\\", "/"))
            if rel.suffix.lower() not in AUDIO_EXT and rel.suffix.lower() != ".zip":
                continue
            parts = [safe_name(p) for p in rel.parts if p not in ("", ".", "..")]
            if parts and parts[0] == "__MACOSX":
                continue
            dest = target.joinpath(*parts).resolve()
            if target not in dest.parents:
                continue
            dest.parent.mkdir(parents=True, exist_ok=True)
            with z.open(info) as src, open(dest, "wb") as out:
                shutil.copyfileobj(src, out)
            if dest.suffix.lower() == ".zip":  # zip dentro zip (capita nei bundle Sonniss)
                count += extract_zip(dest, dest.with_suffix(""), log)
                dest.unlink()
            else:
                count += 1
    return count


def import_source(cfg: Config, source: str, collection: str | None = None, log=print) -> Path:
    """source = URL http(s), file .zip, file audio o cartella locale."""
    if source.startswith(("http://", "https://")):
        log(f"Download di {source} …")
        path = download(source, cfg.downloads_dir, log)
    else:
        path = Path(source).expanduser()
        if not path.exists():
            raise SystemExit(f"Non trovato: {path}")
    name = safe_name(collection or path.stem)
    target = cfg.import_root / name
    target.mkdir(parents=True, exist_ok=True)
    if path.is_dir():
        n = 0
        for f in path.rglob("*"):
            if f.is_file() and f.suffix.lower() in AUDIO_EXT:
                dest = target / f.relative_to(path)
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, dest)
                n += 1
    elif path.suffix.lower() == ".zip":
        log(f"Estrazione in {target} …")
        n = extract_zip(path, target, log)
    elif path.suffix.lower() in AUDIO_EXT:
        shutil.copy2(path, target / path.name)
        n = 1
    else:
        raise SystemExit(f"Formato non supportato: {path.suffix} (usa .zip, un file audio o una cartella)")
    log(f"Importati {n} file audio in '{name}'")
    return target
