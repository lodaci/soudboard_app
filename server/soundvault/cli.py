"""Riga di comando: python -m soundvault <comando>"""
from __future__ import annotations

import argparse
from pathlib import Path

from . import config as cfgmod
from . import importer, indexer


def cmd_setup(a):
    path = Path(a.config)
    if path.exists() and not a.force:
        print(f"{path} esiste già (usa --force per riscriverlo). Token esistenti invariati.")
        cfg = cfgmod.load(path)
    else:
        cfg = cfgmod.Config(
            host="0.0.0.0" if a.lan else "127.0.0.1",
            port=a.port,
            folders=[Path(f).expanduser().resolve() for f in (a.library or ["library"])],
            data_dir=Path(a.data).expanduser().resolve(),
            path=path,
        )
        for f in cfg.folders:
            f.mkdir(parents=True, exist_ok=True)
        cfg.data_dir.mkdir(parents=True, exist_ok=True)
        cfgmod.write(cfg)
        print(f"Creato {path}")
    if not cfgmod.load_tokens(cfg):
        token = cfgmod.add_token(cfg, "principale")
        print("\nToken di accesso (inseriscilo nell'app, Esplora → Il mio archivio):\n")
        print(f"    {token}\n")
        print("Per vederlo di nuovo: python -m soundvault token list")


def cmd_index(a):
    indexer.index(cfgmod.load(Path(a.config)))


def cmd_import(a):
    cfg = cfgmod.load(Path(a.config))
    for src in a.sources:
        importer.import_source(cfg, src, a.collection)
    indexer.index(cfg)


def cmd_serve(a):
    import uvicorn

    from .api import create_app

    cfg = cfgmod.load(Path(a.config))
    if not cfgmod.load_tokens(cfg):
        raise SystemExit("Nessun token configurato: esegui 'python -m soundvault token add NOME'")
    print(f"SoundVault in ascolto su http://{cfg.host}:{cfg.port}")
    uvicorn.run(create_app(cfg), host=cfg.host, port=cfg.port, proxy_headers=True, log_level="info")


def cmd_token(a):
    cfg = cfgmod.load(Path(a.config))
    if a.action == "add":
        print(cfgmod.add_token(cfg, a.name))
    elif a.action == "revoke":
        print("Revocato" if cfgmod.revoke_token(cfg, a.name) else "Nessun token con quel nome")
    else:
        for t in cfgmod.load_tokens(cfg):
            print(f"{t['name']:<15} {t['token']}   (creato {t.get('created', '?')})")


def main(argv=None):
    p = argparse.ArgumentParser(prog="soundvault", description="Archivio privato di suoni per D&D Soundboard")
    p.add_argument("--config", default=str(cfgmod.DEFAULT_CONFIG), help="percorso di config.toml")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("setup", help="crea config.toml e il primo token")
    s.add_argument("--library", action="append", help="cartella della libreria (ripetibile)")
    s.add_argument("--data", default="data", help="cartella per indice/cache/token")
    s.add_argument("--port", type=int, default=8765)
    s.add_argument("--lan", action="store_true", help="ascolta anche sulla rete di casa (0.0.0.0)")
    s.add_argument("--force", action="store_true")
    s.set_defaults(func=cmd_setup)

    s = sub.add_parser("index", help="aggiorna l'indice dei file")
    s.set_defaults(func=cmd_index)

    s = sub.add_parser("import", help="importa zip/URL/cartelle nella libreria")
    s.add_argument("sources", nargs="+", help="URL, file .zip, file audio o cartella")
    s.add_argument("--collection", help="nome della raccolta (default: nome del file)")
    s.set_defaults(func=cmd_import)

    s = sub.add_parser("serve", help="avvia il server")
    s.set_defaults(func=cmd_serve)

    s = sub.add_parser("token", help="gestisci i token di accesso")
    s.add_argument("action", choices=["list", "add", "revoke"])
    s.add_argument("name", nargs="?", default="")
    s.set_defaults(func=cmd_token)

    a = p.parse_args(argv)
    if a.cmd == "token" and a.action in ("add", "revoke") and not a.name:
        p.error("serve un nome: token add NOME / token revoke NOME")
    a.func(a)
