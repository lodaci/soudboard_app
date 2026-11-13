"""Configurazione (config.toml) e token di accesso (tokens.json)."""
from __future__ import annotations

import json
import os
import secrets
import tomllib
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_CONFIG = Path(os.environ.get("SOUNDVAULT_CONFIG", "config.toml"))


@dataclass
class Config:
    host: str = "127.0.0.1"
    port: int = 8765
    folders: list[Path] = field(default_factory=list)
    data_dir: Path = Path("data")
    ffmpeg: str = ""
    path: Path = DEFAULT_CONFIG

    @property
    def db_path(self) -> Path:
        return self.data_dir / "index.sqlite"

    @property
    def cache_dir(self) -> Path:
        return self.data_dir / "cache"

    @property
    def downloads_dir(self) -> Path:
        return self.data_dir / "downloads"

    @property
    def tokens_path(self) -> Path:
        return self.data_dir / "tokens.json"

    @property
    def import_root(self) -> Path:
        """Le raccolte importate finiscono nella prima cartella della libreria."""
        if not self.folders:
            raise SystemExit("Nessuna cartella di libreria configurata in config.toml")
        return self.folders[0]


def load(path: Path = DEFAULT_CONFIG) -> Config:
    if not path.exists():
        raise SystemExit(f"Configurazione non trovata: {path}. Esegui prima: python -m soundvault setup")
    raw = tomllib.loads(path.read_text(encoding="utf-8"))
    base = path.resolve().parent

    def resolve(p: str) -> Path:
        q = Path(p).expanduser()
        return q if q.is_absolute() else (base / q)

    server = raw.get("server", {})
    lib = raw.get("library", {})
    return Config(
        host=server.get("host", "127.0.0.1"),
        port=int(server.get("port", 8765)),
        folders=[resolve(f) for f in lib.get("folders", [])],
        data_dir=resolve(lib.get("data_dir", "data")),
        ffmpeg=lib.get("ffmpeg", ""),
        path=path,
    )


def _toml_str(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def write(cfg: Config) -> None:
    folders = ", ".join(_toml_str(f.as_posix()) for f in cfg.folders)
    text = f"""# Configurazione SoundVault
[server]
# 127.0.0.1 = raggiungibile solo dal NUC (usa Caddy per l'accesso da internet)
# 0.0.0.0   = raggiungibile anche dagli altri dispositivi della rete di casa
host = {_toml_str(cfg.host)}
port = {cfg.port}

[library]
# Cartelle con i file audio. Le raccolte importate vanno nella prima.
folders = [{folders}]
# Indice, cache delle conversioni, download temporanei e token
data_dir = {_toml_str(cfg.data_dir.as_posix())}
# Percorso di ffmpeg (vuoto = cercalo nel PATH). Serve per convertire i WAV in MP3 leggeri.
ffmpeg = {_toml_str(cfg.ffmpeg)}
"""
    cfg.path.write_text(text, encoding="utf-8")


# ---------------- token ----------------

def load_tokens(cfg: Config) -> list[dict]:
    if not cfg.tokens_path.exists():
        return []
    return json.loads(cfg.tokens_path.read_text(encoding="utf-8"))


def save_tokens(cfg: Config, tokens: list[dict]) -> None:
    cfg.data_dir.mkdir(parents=True, exist_ok=True)
    cfg.tokens_path.write_text(json.dumps(tokens, indent=2), encoding="utf-8")


def add_token(cfg: Config, name: str) -> str:
    tokens = load_tokens(cfg)
    if any(t["name"] == name for t in tokens):
        raise SystemExit(f"Esiste già un token chiamato '{name}'")
    value = secrets.token_urlsafe(32)
    tokens.append({"name": name, "token": value, "created": datetime.now(timezone.utc).isoformat(timespec="seconds")})
    save_tokens(cfg, tokens)
    return value


def revoke_token(cfg: Config, name: str) -> bool:
    tokens = load_tokens(cfg)
    kept = [t for t in tokens if t["name"] != name]
    save_tokens(cfg, kept)
    return len(kept) != len(tokens)
