# SoundVault: archivio privato di suoni sul NUC

Un piccolo server che indicizza le tue cartelle di effetti sonori e musiche e le rende cercabili e scaricabili dall'app **D&D Soundboard** (scheda *Esplora → Il mio archivio*). L'accesso è protetto da token.

```
Telefono ──HTTPS──▶ router (443) ──▶ Caddy (certificato automatico) ──▶ SoundVault 127.0.0.1:8765 ──▶ cartelle audio
```

## 1. Installazione sul NUC (Windows)

1. Installa Python 3.12 o più recente, e facoltativamente ffmpeg:
   ```powershell
   winget install Python.Python.3.12
   winget install Gyan.FFmpeg
   ```
2. Copia la cartella `server` del repository sul NUC, per esempio in `C:\SoundVault\server`.
3. Apri **PowerShell come amministratore** in quella cartella e lancia:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Library "D:\Suoni" -Lan
   ```
   - `-Library`: dove tenere i file audio (puoi usare il disco dati del NUC).
   - `-Lan`: rende il server raggiungibile anche dai dispositivi di casa, su `http://IP-del-NUC:8765`.
   - Lo script crea l'ambiente Python, `config.toml`, il **token di accesso** (annotalo) e l'avvio automatico all'accensione.

## 2. Riempire l'archivio

Vedi [collections.md](collections.md) (Sonniss GDC, Kenney, OpenGameArt, cartelle tue). Per esempio:

```bat
soundvault.bat import "D:\Download\kenney_rpg-audio.zip" --collection "Kenney RPG Audio"
```

## 3. Accesso da fuori casa (dominio + HTTPS)

Il token viaggia in ogni richiesta, quindi **da internet usa sempre HTTPS**. Il modo più semplice è Caddy:

1. Ti serve un nome di dominio che punti all'IP di casa. Se l'IP cambia, puoi usare un DNS dinamico gratuito come [DuckDNS](https://www.duckdns.org).
2. Sul router inoltra le porte **TCP 80 e 443** verso l'IP del NUC (meglio se fisso o riservato nel DHCP).
3. Scarica Caddy per Windows da <https://caddyserver.com/download> e mettilo in `C:\SoundVault\caddy\caddy.exe`.
4. Copia `Caddyfile` accanto a `caddy.exe` e sostituisci `suoni.esempio.it` con il tuo dominio.
5. Prova con `caddy.exe run --config Caddyfile`. Al primo avvio Caddy ottiene il certificato da solo.
6. Per l'avvio automatico, in PowerShell come amministratore:
   ```powershell
   $a = New-ScheduledTaskAction -Execute "C:\SoundVault\caddy\caddy.exe" -Argument "run --config Caddyfile" -WorkingDirectory "C:\SoundVault\caddy"
   Register-ScheduledTask -TaskName "Caddy" -Action $a -Trigger (New-ScheduledTaskTrigger -AtStartup) -User "SYSTEM" -RunLevel Highest -Force
   New-NetFirewallRule -DisplayName "Caddy HTTPS" -Direction Inbound -Protocol TCP -LocalPort 80,443 -Action Allow
   ```

Nell'app: **Esplora → Il mio archivio → ⚙** e inserisci URL `https://suoni.tuodominio.it` e il token. Poi premi *Prova connessione*.
In casa puoi usare anche `http://192.168.x.y:8765`, se hai installato con `-Lan`.

## Comandi utili

| Comando | Cosa fa |
|---|---|
| `soundvault.bat index` | Aggiorna l'indice (succede anche a ogni avvio del server) |
| `soundvault.bat import <zip/url/cartella> --collection Nome` | Importa una raccolta |
| `soundvault.bat token list` | Mostra i token |
| `soundvault.bat token add Mario` | Crea un token per un giocatore |
| `soundvault.bat token revoke Mario` | Revoca un token |
| `start.bat` | Avvio manuale (se non usi l'avvio automatico) |

## API (per curiosi)

Tutte le rotte vogliono `Authorization: Bearer <token>`.

- `GET /api/v1/health`: stato e numero di suoni.
- `GET /api/v1/search?q=spada&page=1&page_size=30&collection=&category=`: ricerca.
- `GET /api/v1/categories`: raccolte e categorie.
- `GET /api/v1/files/{id}?compact=true`: il file. Con `compact=true` i WAV/FLAC arrivano convertiti in MP3. Supporta le richieste Range.
- `POST /api/v1/reindex`: aggiorna l'indice.

## Sviluppo

```bash
pip install -r requirements-dev.txt
pytest -q
```
