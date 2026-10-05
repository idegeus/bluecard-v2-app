# Play Store-materiaal

| Bestand | Waar in de Play Console | Eisen |
|---|---|---|
| `icon-512.png` | Winkelvermelding → App-pictogram | 512×512 PNG |
| `feature-graphic-1024x500-nl.png` / `-en.png` | Winkelvermelding → Feature graphic (per taal) | 1024×500, geen transparantie |
| `phone-nl/01…08-*.png` | Screenshots telefoon, Nederlandse vermelding | 2–8 stuks, 9:16, 1080×1920 |
| `phone-en/*.png` | Screenshots telefoon, Engelse vermelding | idem |

De screenshots zijn opnames van de app (Nederlands en Engels; de Engelse via de per-app-taal van Android) in een kader met een kop, gemaakt met `bron/compose.py`
(headless Chrome). Het icoon en de feature graphics komen uit `bron/icon.html`, `bron/feature.html` en `bron/feature-en.html`.
