import math
import struct
import wave
import zipfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from soundvault import config as cfgmod
from soundvault import importer, indexer
from soundvault.api import create_app


def make_wav(path: Path, seconds: float = 0.5, rate: int = 8000):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        frames = b"".join(
            struct.pack("<h", int(8000 * math.sin(2 * math.pi * 440 * i / rate))) for i in range(int(rate * seconds))
        )
        w.writeframes(frames)


@pytest.fixture()
def cfg(tmp_path):
    lib = tmp_path / "library"
    make_wav(lib / "SonnissGDC" / "Weapons" / "SWORD_Metal_Clash_01.wav", 1.0)
    make_wav(lib / "SonnissGDC" / "Weapons" / "BOW_Arrow_Release.wav")
    make_wav(lib / "Kenney" / "Ambience" / "TavernCrowdLoop.wav", 2.0)
    make_wav(lib / "Kenney" / "dragon roar.wav")
    c = cfgmod.Config(folders=[lib], data_dir=tmp_path / "data", path=tmp_path / "config.toml")
    cfgmod.write(c)
    return cfgmod.load(c.path)


def test_words():
    assert indexer.words("SWORD_Metal-Clash01") == ["sword", "metal", "clash"]
    assert indexer.words("TavernCrowdLoop") == ["tavern", "crowd", "loop"]


def test_config_roundtrip(cfg):
    assert cfg.folders[0].name == "library"
    assert cfg.port == 8765


def test_index_and_search(cfg):
    stats = indexer.index(cfg, log=lambda *_: None)
    assert stats["added"] == 4
    conn = indexer.connect(cfg)
    r = indexer.search(conn, "sword")
    assert r["total"] == 1
    hit = r["results"][0]
    assert hit["collection"] == "SonnissGDC" and hit["category"] == "Weapons"
    assert 900 <= hit["duration_ms"] <= 1100
    assert indexer.search(conn, "tav")["total"] == 1  # prefisso
    assert indexer.search(conn, "weapons")["total"] == 2  # dalla cartella
    assert indexer.search(conn, "", collection="Kenney")["total"] == 2
    assert indexer.search(conn, 'drag"*(:')["total"] == 1  # input "sporco" non rompe la query
    # incrementale: nulla cambia
    again = indexer.index(cfg, conn, log=lambda *_: None)
    assert again["added"] == 0 and again["updated"] == 0
    # rimozione
    (cfg.folders[0] / "Kenney" / "dragon roar.wav").unlink()
    assert indexer.index(cfg, conn, log=lambda *_: None)["removed"] == 1


def test_import_zip(cfg, tmp_path):
    src = tmp_path / "pack"
    make_wav(src / "Impacts" / "door_slam.wav")
    zpath = tmp_path / "kenney_impact-sounds.zip"
    with zipfile.ZipFile(zpath, "w") as z:
        z.write(src / "Impacts" / "door_slam.wav", "Impacts/door_slam.wav")
        z.writestr("License.txt", "CC0")
        z.writestr("../evil.wav", b"x")
    importer.import_source(cfg, str(zpath), "Kenney Impact", log=lambda *_: None)
    assert (cfg.folders[0] / "Kenney Impact" / "Impacts" / "door_slam.wav").exists()
    assert not (cfg.folders[0] / "evil.wav").exists()
    assert not (cfg.folders[0].parent / "evil.wav").exists()
    indexer.index(cfg, log=lambda *_: None)
    assert indexer.search(indexer.connect(cfg), "door slam")["total"] == 1


@pytest.fixture()
def client(cfg):
    token = cfgmod.add_token(cfg, "test")
    indexer.index(cfg, log=lambda *_: None)
    app = create_app(cfg)
    with TestClient(app) as c:
        c.headers["Authorization"] = f"Bearer {token}"
        yield c


def test_auth_required(client):
    bad = TestClient(client.app)
    assert bad.get("/api/v1/search?q=sword").status_code == 401
    assert bad.get("/api/v1/health", headers={"Authorization": "Bearer nope"}).status_code == 401


def test_api(client):
    h = client.get("/api/v1/health").json()
    assert h["status"] == "ok" and h["sounds"] == 4
    r = client.get("/api/v1/search", params={"q": "sword"}).json()
    assert r["total"] == 1
    sid = r["results"][0]["id"]
    cats = client.get("/api/v1/categories").json()["categories"]
    assert {"collection": "SonnissGDC", "category": "Weapons", "count": 2} in cats

    full = client.get(f"/api/v1/files/{sid}")
    assert full.status_code == 200 and full.content[:4] == b"RIFF"
    part = client.get(f"/api/v1/files/{sid}", headers={"Range": "bytes=0-99"})
    assert part.status_code == 206 and len(part.content) == 100
    assert client.get("/api/v1/files/999999").status_code == 404


def test_token_query_param(client, cfg):
    token = client.headers["Authorization"][7:]
    plain = TestClient(client.app)
    assert plain.get(f"/api/v1/health?token={token}").status_code == 200


@pytest.mark.skipif(__import__("shutil").which("ffmpeg") is None, reason="ffmpeg non installato")
def test_compact_mp3(client):
    sid = client.get("/api/v1/search", params={"q": "tavern"}).json()["results"][0]["id"]
    r = client.get(f"/api/v1/files/{sid}?compact=true")
    assert r.status_code == 200 and r.headers["content-type"] == "audio/mpeg"
    assert r.content[:3] == b"ID3" or r.content[:2] == b"\xff\xfb"
