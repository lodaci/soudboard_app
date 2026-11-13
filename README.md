# D&D Soundboard (Android)

App Android per gestire musiche ed effetti sonori durante le sessioni di D&D.

## Funzioni

- **Libreria**: importa file audio dal telefono, oppure aggiungi un link (YouTube e centinaia di altri siti tramite yt-dlp, o link diretti a mp3/ogg/…) da **riprodurre in streaming** o da **scaricare e salvare in locale**. Puoi anche usare *Condividi → D&D Soundboard* direttamente dall'app YouTube.
- **Riproduzione multipla**: tutte le tracce possono suonare contemporaneamente, ognuna con il proprio volume. Musiche in loop, effetti one‑shot.
- **Dissolvenze**: avvio e stop con fade in/fade out (durata regolabile 0–10 s), per una traccia o per più tracce insieme; "Stop tutto (fade)" e "Stop immediato".
- **Soundboard**: griglia di tasti rapidi (più schede, 2–6 colonne). Ogni tasto ha traccia, emoji, testo e colore. Tocca per suonare, tieni premuto per modificare.
  - tracce in loop: il tasto avvia/ferma (con fade)
  - suoni non in loop: il tasto li fa ripartire da capo
- **Uscita Bluetooth**: mostra dove esce l'audio e include la guida per usare le casse di un laptop Windows.
- La riproduzione continua a schermo spento (notifica con "Stop tutto").

## Installazione

1. Su GitHub apri **Actions → Build APK**, scegli l'ultima esecuzione riuscita e scarica l'artefatto **DnD-Soundboard-apk** (è uno zip con dentro l'APK).
2. Copia `DnD-Soundboard.apk` sul telefono e aprilo (consenti l'installazione da fonti sconosciute).
3. Gli APK sono firmati sempre con la stessa chiave (`app/debug.keystore`), quindi le versioni nuove si installano sopra le vecchie senza perdere i dati.

Requisiti: Android 8.0+, telefono ARM (praticamente tutti).

## Audio sulle casse del laptop (Windows 10/11)

Android può inviare audio via Bluetooth solo a un dispositivo che fa da "cassa". Windows può farlo così:

1. Sul PC: *Impostazioni → Bluetooth e dispositivi → Aggiungi dispositivo → Bluetooth* e accoppia il telefono.
2. Installa dal Microsoft Store l'app gratuita **Bluetooth Audio Receiver**.
3. Aprila, seleziona il telefono e premi **Open Connection**.
4. Nella scheda **Uscita** dell'app dovrebbe comparire "Bluetooth: *nome del PC*". Premi *Suono di prova*.

## Note sui link

- I link in streaming richiedono internet durante la sessione; quelli scaricati funzionano offline.
- Se YouTube cambia qualcosa e i link smettono di funzionare, usa **Uscita → Aggiorna yt-dlp**.
- Scaricare contenuti da YouTube può violarne i Termini di servizio: usalo per audio di cui hai i diritti o per uso personale.

## Sviluppo

Kotlin + Jetpack Compose, Media3 ExoPlayer (un player per traccia), Room, youtubedl-android.

```bash
./gradlew assembleRelease
```

Struttura principale (`app/src/main/java/it/lodaci/soundboard/`):

- `playback/MixerEngine.kt` – mixer multi‑traccia, volumi, fade
- `playback/PlaybackService.kt` – servizio in primo piano
- `remote/YtDlpManager.kt`, `remote/LinkTasks.kt` – streaming e download da link
- `data/` – database Room (tracce, schede, tasti)
- `ui/` – schermate Compose (Soundboard, Libreria, Mixer, Uscita)
