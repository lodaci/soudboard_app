# Raccolte da importare nell'archivio

Tutti i comandi si lanciano dalla cartella `server` sul NUC. `soundvault.bat import` accetta un **URL**, un file **.zip**, un file audio o una **cartella**. Dopo l'import l'indice si aggiorna da solo.

## Sonniss – GDC Game Audio Bundle (royalty‑free)

Ogni anno Sonniss regala un pacchetto da decine di GB di effetti professionali: armi, magie, ambienti, mostri, folle e altro. La licenza permette l'uso anche commerciale, ma non la redistribuzione dei file così come sono.

1. Vai su <https://sonniss.com/gameaudiogdc> e scarica i bundle. Ci sono link diretti o torrent: per i pacchetti più grandi il torrent è più affidabile.
2. Importa gli zip scaricati, un anno per raccolta:
   ```bat
   soundvault.bat import "D:\Download\Sonniss.com - GDC 2024 - Game Audio Bundle" --collection "Sonniss GDC 2024"
   soundvault.bat import "D:\Download\GDC2023_part1.zip" "D:\Download\GDC2023_part2.zip" --collection "Sonniss GDC 2023"
   ```
   I bundle Sonniss sono organizzati per studio e libreria e i nomi dei file sono descrittivi (es. `SWORD_Metal_Clash_01.wav`), quindi si trovano bene con la ricerca.

> I WAV Sonniss sono spesso a 24 bit / 96 kHz e pesano molto. Con **ffmpeg** installato (`winget install Gyan.FFmpeg`), l'app li riceve convertiti in MP3 (~10 volte più leggeri), mentre l'originale resta sul NUC.

## Kenney (CC0, pubblico dominio)

Pacchetti piccoli e puliti, molto adatti ai giochi. Dalla pagina di ogni pacchetto copia il link del pulsante *Download* e importalo:

- RPG Audio: <https://kenney.nl/assets/rpg-audio> (passi, monete, porte, armi)
- Impact Sounds: <https://kenney.nl/assets/impact-sounds>
- Interface Sounds: <https://kenney.nl/assets/interface-sounds>
- Casino Audio (dadi!): <https://kenney.nl/assets/casino-audio>
- Elenco completo: <https://kenney.nl/assets/category:Audio>

```bat
soundvault.bat import "https://kenney.nl/media/pages/assets/rpg-audio/.../kenney_rpg-audio.zip" --collection "Kenney RPG Audio"
```

## OpenGameArt (licenze varie: CC0, CC‑BY, …)

Su <https://opengameart.org/art-search-advanced?field_art_type_tid[]=13> (filtro *Sound Effect*) ci sono molti pacchetti fantasy, per esempio "RPG Sound Pack" e "80 CC0 creature SFX". Controlla la licenza di ciascuno: per i CC‑BY va citato l'autore.

```bat
soundvault.bat import "https://opengameart.org/sites/default/files/rpg_sound_pack.zip" --collection "OGA RPG Sound Pack"
```

## Le tue cartelle

Puoi aggiungere altre cartelle in `config.toml` → `folders = ["C:/SoundVault/library", "D:/Musica/D&D"]` e poi lanciare `soundvault.bat index`. L'indice si aggiorna anche a ogni avvio del server.
