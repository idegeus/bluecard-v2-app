# BlueCard — kaartspellen tegen bots of samen via Bluetooth

Offline kaartspelapp voor **Android en iPhone** met vier spellen: **Zweeds Pesten** (*Palace*, ook bekend als *Shithead*), gewoon
**Pesten** (kleur of waarde volgen, met pakkaarten), **Presidenten** (sets hoger leggen; president en sloeber,
kaarten ruilen) en **Hartenjagen** (*Hearts*: bekennen, harten en de schoppenvrouw ontwijken).
Speel tegen bots op één telefoon, of met meerdere telefoons samen via **Bluetooth** (iPhone: Multipeer) — zonder
internet, zonder server en zonder account. Op Android staat op het start- en eindscherm een advertentiebanner
(Google AdMob), nooit tijdens het spel. De iPhone-versie: zie `docs/IOS.md`.

* Kotlin Multiplatform · Compose Multiplatform (gedeelde UI voor Android en iOS) · Material 3 · Coroutines/StateFlow · DataStore · kotlinx.serialization
* Bluetooth Classic (RFCOMM), host-authoritative, versiegenummerd JSON-protocol
* Pure-Kotlin game-engine met configureerbare huisregels en bots
* minSdk 26 (Android 8.0), target/compileSdk 36

Het spel zelf gebruikt geen internet. De `INTERNET`-permissie komt alleen mee met de AdMob-SDK, voor de
banners op het start- en eindscherm en het toestemmingsformulier; zonder verbinding wordt er gewoon geen advertentie getoond.

---

## Bouwen en installeren

Vereisten: Android Studio (Narwhal of nieuwer) of alleen de Android SDK + JDK 17.

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Installeren op een aangesloten telefoon:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release-build (geminificeerd met R8, ~1,8 MB; nu getekend met de debug-sleutel zodat hij direct
installeerbaar is — vervang `signingConfig` in `app/build.gradle.kts` door je eigen sleutel voor publicatie):

```bash
./gradlew assembleRelease
```

In Android Studio: *File → Open* → deze map, wacht op de Gradle-sync en druk op *Run*.

## Testen

```bash
./gradlew test
```

Draait alle unit tests (JVM, geen telefoon nodig):

| Module | Wat | Aantal |
|---|---|---|
| `engine` | Zweeds Pesten, Pesten, Presidenten en Hartenjagen (o.a. 800 + 250 extra gesimuleerde rondes/spellen voor de nieuwe spellen): spelregels, speciale kaarten, huisregels, winnen, trekstapel/reshuffle, dubbele acties, verkeerde speler, views zonder informatielek, bots, "Vals!", "Laatste kaart!"/"Vergeten!", **1.200 + 800 gesimuleerde complete potjes** met invarianten (alle kaarten uniek en behouden, nooit een geweigerde botzet; bij Pesten ook willekeurige valsspelers en meldingen) | 168 |
| `multiplayer` | protocol-codec (alle berichttypes, versieverschil, corrupte/onbekende berichten), complete host+client-potjes over in-memory verbindingen (beide spellen), herverbinden, heartbeat-time-out, bot-overname, lobby vol, spel bezig, kick, dubbele/ongeldige acties, client van een ander spel geweigerd | 44 |
| `shared` | elke engine-/protocolcode heeft een tekst in alle 5 talen met dezelfde placeholders; meervoud en formattering van de eigen tekstlaag; ranglijst | 13 |
| `app` | filter welke Bluetooth-apparaten bevraagd worden; "rustige start" van de startscherm-advertentie | 7 |

Totaal 232 tests. Lint: `./gradlew :app:lintDebug` (0 waarschuwingen).

Alles in één keer (tests, lint, debug- en release-APK, App Bundle voor Play, bron-ZIP in `dist/`): `./package.sh`.
Publiceren in de Play Store: zie `docs/PLAY_STORE.md` (uploadsleutel, checklist, store-teksten, privacybeleid).

## Spelen

**Bediening** — zoals met echte kaarten:

* **Kaart spelen:** veeg/sleep een kaart omhoog naar de aflegstapel (een korte snelle veeg is genoeg).
  Heb je meer kaarten van dezelfde waarde, dan blijft de geveegde kaart even klaarliggen: veeg de andere erachteraan
  en ze worden samen gespeeld (na 3 s, of direct met *Speel*; *Terug* haalt ze terug). Aantikken + vegen of
  *Speel* werkt ook.
* **Hand doorbladeren:** veeg zijwaarts over je kaarten; de waaier gaat dan open (meer ruimte tussen de kaarten,
  hij mag van het scherm aflopen) zodat je makkelijk de goede kaart aantikt of omhoog veegt. Na je beurt, of na een
  paar seconden zonder geselecteerde kaart, gaat hij weer dicht.
* **Stapel pakken:** trek de aflegstapel omlaag naar je toe (of de knop *Pak stapel*). Mag altijd.
* **Ruilen:** kies in de ruilfase welke 3 van je 6 kaarten open liggen: tik een handkaart en een open kaart
  (in willekeurige volgorde) of sleep een handkaart op een open kaart.
* **Vals spelen:** standaard dwingt de app de regels niet af — je kunt elke kaart opleggen, net als aan tafel;
  passende kaarten worden wel opgelicht. Zie je iemand vals spelen, tik dan binnen **10 seconden** op **Vals!** — ook
  als er daarna al verder is gespeeld; staan er meerdere zetten open, dan kies je *wie* vals speelde. Meteen betrapt
  (er gebeurde nog niets na de zet): de valsspeler krijgt zijn kaarten terug, pakt de stapel erbij en zijn beurt is
  voorbij (ook een vals verbrande stapel wordt teruggedraaid). Later betrapt: de valsspeler pakt de stapel zoals die
  dan ligt (minstens zoveel kaarten als hij vals speelde). Klopt het niet, dan pak jij de stapel. Uitgaan met een valse
  laatste kaart kan niet. Bots spelen eerlijk maar betrappen je wel. Vink bij het starten *Spelregels afdwingen* aan
  om ongeldige kaarten helemaal te blokkeren.

**Een spel starten** (één knop voor alles): *Spel starten* op het beginscherm opent meteen je tafel. Daar kies je het
spel, voeg je bots toe (*Bot toevoegen*, met *Moeilijkheid* voor alle bots) en/of wacht je op mensen in de buurt;
dan *Start spel*. Zitten er alleen bots aan tafel, dan wordt het een gewoon spel tegen de bots: bewaard na iedere zet
(*Doorgaan met spel* in het menu) en zonder Bluetooth. Staat Bluetooth uit of heeft de app geen toestemming, dan
opent de tafel toch, alleen voor bots (met een knop *Toestaan*); komt Bluetooth terug, dan gaat de tafel vanzelf open
voor anderen.

**Via Bluetooth:**

1. Host: *Spel starten*. De allereerste keer vraagt de telefoon om 5 minuten zichtbaar te worden — tik *Toestaan*.
   Daarna niet meer: de eerste telefoon die verbindt vertelt de host zijn eigen Bluetooth-adres (een app kan dat zelf
   niet uitlezen) en vanaf dan zit dat adres in de BLE-aankondiging van de tafel, zodat anderen direct verbinden.
   Optioneel: spel kiezen, bots toevoegen, huisregels aanpassen.
2. Anderen: *Meedoen met een spel* (of direct in *Spellen in de buurt* op het beginscherm). Een balk laat zien wat
   er gebeurt (*telefoons zoeken → tafels bekijken → blijven kijken*) en loopt in 90 s af. De app zoekt telefoons in de buurt en vraagt elk apparaat of er een spel
   draait; je ziet bijvoorbeeld *"Zweeds Pesten · host Ivo · 1 speler"* — ook als alle telefoons dezelfde
   Bluetooth-naam hebben. Tik erop om mee te doen. Koppelen is niet nodig.
3. Host: *Start spel*.

Tafels in de buurt verschijnen ook vanzelf op het **beginscherm** (*Spellen in de buurt*, met *Meedoen*/*Kijk mee*):
de telefoon blijft zoeken zolang het beginscherm open is, dus opent iemand een tafel, dan staat die er zo.

**Hoe snel een tafel gevonden wordt** (gemeten op twee Moto e13's): een host die al eens gevonden is, staat binnen
±2 s op het beginscherm en binnen ±5 s op *Meedoen* (BLE-aankondiging met adres, geen Bluetooth-zoekactie). Een host
die nog nooit gevonden is: ±8 s (de zoekactie stopt bij elke nieuwe telefoon om die meteen te controleren, in plaats
van eerst 20 s te zoeken). Voorheen 25 s tot een minuut.

Valt een verbinding weg, dan probeert de client automatisch opnieuw te verbinden (4 pogingen) en krijgt hij
zijn plek en kaarten terug. Lukt dat niet binnen 60 seconden, dan speelt een bot verder voor die speler
(de host kan dit ook direct doen met *Laat bot spelen*). Terugkomen kan altijd: via *Meedoen* opnieuw
verbinden met hetzelfde spel.

## Spelregels (standaard "Klassiek")

* Iedereen krijgt 3 blinde kaarten, daarop 3 open kaarten, en 3 handkaarten. Vooraf mag je handkaarten met
  open kaarten ruilen.
* Speel een kaart gelijk aan of hoger dan de bovenste kaart (aas hoogst); meerdere gelijke kaarten mag.
  Vul daarna je hand aan van de trekstapel.
* Kun je niet: pak de hele aflegstapel (straf).
* Hand leeg en trekstapel op → open kaarten, daarna blind.
* **2** reset · **7** volgende speelt 7 of lager (ook geen 10) · **10** verbrandt de stapel (en je mag nog eens) ·
  **vier dezelfde** verbranden ook.
* Wie als eerste alles kwijt is wint; de laatste is de pestkop.

Alles is instelbaar onder *Instellingen → Huisregels*: effect per kaart (gewoon, reset, lager,
verbranden, overslaan, richting draaien), handgrootte 3–5, meerdere kaarten, vier-dezelfde, extra beurt,
"lager" inclusief/exclusief, ruilfase, gokken van de trekstapel, herschudden van verbrande
kaarten, doorspelen tot de pestkop, startspeler. Voorinstellingen: *Klassiek (NL)*, *TIS-modus* (klassiek + *Na pakken zelf beginnen*) en *Extra pesten*.
De stapel pakken mag altijd, ook als je wel kunt spelen. Optioneel: *Na pakken zelf beginnen* — wie de stapel
pakt (ook na een mislukte blinde kaart of gok) mag direct zelf een kaart opleggen; een "Vals!"-straf telt niet.

## Pesten (standaard "Klassiek (NL)")

* Iedereen krijgt 7 kaarten (2 jokers in het spel); de bovenste kaart van de trekstapel gaat open.
* Leg een kaart van **dezelfde kleur of dezelfde waarde**. Kun of wil je niet: pak een kaart (tik op de
  trekstapel of *Pak kaart*); past die, dan mag je hem meteen opleggen, anders *Pas*.
* **2** volgende pakt 2 · **joker** volgende pakt 5 en past op alles · pakkaarten mag je **stapelen** (2 of joker
  erop, de volgende pakt alles) · **7** en **heer** nog een keer · **8** volgende slaat over · **boer** past
  altijd, jij kiest de kleur · **aas** keer (met 2 spelers: nog een keer).
* **Laatste kaart!** melden bij twee kaarten (knop). Vergeten? Dan kan iedereen **Vergeten!** roepen: 2 strafkaarten.
* Uitgaan op een pestkaart mag standaard niet (je pakt er één bij).
* Vals spelen werkt als bij Zweeds Pesten: elke kaart mag, anderen roepen **Vals!** tot 10 s na de zet. Meteen
  betrapt: kaarten terug + wat je ontweek + 2 strafkaarten en je beurt is voorbij; later betrapt: 2 strafkaarten.
  Onterecht: 2 strafkaarten.
* Instelbaar onder *Instellingen → Huisregels Pesten*: effect per kaart, aantal jokers (0–2) en hoeveel een joker
  laat pakken, kaarten per speler (5–8), stapelen, meerdere tegelijk, laatste kaart melden, uitgaan op pestkaart,
  doorspelen tot de pestkop. Voorinstellingen: *Klassiek (NL)*, *Simpel*, *Zonder jokers*. 2–6 spelers.

Het spel kies je in je eigen tafel (na *Spel starten*); wie meedoet speelt
automatisch het spel van de host.

## Samen aan tafel

* **Host weg? Spel gaat door.** De host stuurt de eerste verbonden speler (de *opvolger*) steeds een kopie van de
  hele tafel. Gaat de host weg (✕ of verbinding weg), dan wordt de opvolger host met precies hetzelfde spel, de
  anderen verbinden vanzelf met hem en een bot speelt verder voor de oude host (die later als speler terug kan).
  Zonder andere mensen aan tafel stopt het spel zoals vroeger.
* **Meekijken en later instappen**: wie tijdens een spel (of bij een volle tafel) aansluit, kijkt mee zonder kaarten
  te zien en krijgt in de volgende ronde een plek; na het potje ga je mee naar de lobby (ook als je vanaf het
  beginscherm instapte). Stoppen met kijken (✕) kost niets. Meekijkers en spelers die al uit zijn kunnen geen *Vals!* roepen,
  wel reageren.
* **Buzzer**: staat iemand 5 s stil terwijl hij aan de beurt is, dan verschijnt er bij de anderen een 🔔 bovenin;
  daarmee schudt zijn scherm en trilt zijn telefoon — ook met het scherm uit in de broekzak: dan komt de buzz als
  melding met trilpatroon en buzzergeluid (eigen meldingskanaal *Buzzer*). Max. 3× per minuut per persoon (host controleert, ook de 5 s).
* **Melding "tafel in de buurt"** (Android): zolang de host een tafel heeft zendt hij een klein BLE-signaal uit
  (niet verbindbaar, met naam, spel, of er gespeeld wordt en — zodra bekend — zijn Bluetooth-adres; een melding komt
  alleen voor een open lobby). Andere telefoons hebben één *gefilterd* achtergrond-scan
  bij Android geregistreerd: de Bluetooth-chip zelf luistert, de app draait niet en wordt alleen gewekt bij een tafel,
  en toont dan één melding (*"Ivo heeft een tafel geopend — Zweeds Pesten · tik om mee te doen"*). Tik je erop, dan
  opent BlueCard, zoekt die tafel en doe je meteen mee in de lobby; een aflopende balk laat zien hoe lang er nog
  gezocht wordt (90 s). Zolang de tafel blijft
  uitzenden wordt de melding stil verlengd (Android levert het signaal ±elke 20–35 s); gaat de lobby dicht of begint
  het spel, dan verdwijnt hij ±2 minuten later vanzelf. Weggeveegd blijft weg. Na herstart van de
  telefoon of een app-update wordt de scan opnieuw geregistreerd. Uit te zetten onder *Instellingen*. Op iOS niet
  (iOS laat BLE op de achtergrond nauwelijks toe).
* **Zichtbaar maken** (📡 bij de host, aan tafel en op het eindscherm): Android-telefoons vinden een niet-gekoppelde
  host alleen als die zichtbaar is (max. 5 min per keer); zo kan iemand die later komt toch meekijken/instappen.
  Komt de host terug in de lobby, dan wordt automatisch opnieuw gevraagd om zichtbaar te worden.
* **Live reacties**: emoji zweven over ieders scherm, ook van meekijkers. Je begint met ❤️ 👍 😢 😂; meer emoji
  speel je vrij met uiteenlopende doelen (3× gewonnen, 10 potjes gespeeld, 5× Zweeds Pesten gewonnen, 3× Presidenten
  gewonnen, 3× ongezien vals gespeeld, 10× Pesten gespeeld, 5× terecht *Vals!* geroepen) en bij 50 overwinningen heb
  je **vrije keuze uit alle emoji** (± 270). Onder *Skins → Emoji's* zie je per groep je voortgang (*"1/5 · keer
  Zweeds Pesten gewonnen"*) en kies je welke (max. 6) in je reactiebalk staan. De host stuurt alleen emoji uit de vaste
  lijst door.
* **Chat**: 💬 bovenin aan tafel (met teller voor ongelezen berichten) en een chatpaneel in de lobby; nieuwe berichten
  verschijnen even bovenin, met snelknoppen (*Goed gespeeld!*, *Schiet op!*, …). Max. 140 tekens en 5 berichten per
  10 s per persoon; meekijkers kunnen ook chatten.
* **Spelersprofiel**: tik aan tafel op een tegenstander voor zijn ranglijstcijfers (gespeeld/gewonnen/verloren, per
  spel, vals roepen) en jouw onderlinge stand.
* **Schudden**: voor elke ronde schudt één speler (om de beurt, bots doen het zelf) door 3 s met de telefoon te
  schudden (versnellingsmeter; zonder sensor: snel tikken). Uit te zetten: *Instellingen → Schudden met je telefoon*.
* **Tablet (of telefoon) als tafel**: kies op het Bluetooth-scherm *📺 Als tafel meedoen* en sluit aan bij een
  tafel. Dat scherm speelt niet mee en neemt geen plek in, maar laat het spel groot zien voor iedereen: alle spelers
  met hun open kaarten bovenin, de stapels groot in het midden (meeschalend tot 2,6× op een tablet), de laatste
  gebeurtenis en wie er aan de beurt is. Het scherm blijft aan. In de lobby ziet iedereen *📺 Tafel: naam*. Werkt
  voor alle vier de spellen.
* **Android Auto (host, alleen debug-build)**: op het autoscherm zie je je tafel: wie er zit, wie aan de beurt is en
  hoeveel kaarten iedereen nog heeft; in de lobby *Bot toevoegen* / *Start spel*, na afloop *Opnieuw spelen*, en
  zonder tafel *Tafel openen*. Gebouwd met de Android Auto-sjablonen (max. 6 regels, geen kaarten), dus ook tijdens
  het rijden toegestaan. Android Auto kent geen categorie voor kaartspellen, daarom zit het niet in de Play
  Store-release; met een debug-build werkt het als je in Android Auto (ontwikkelaarsinstellingen) *Onbekende
  bronnen* aanzet.
* **Kaarten en tafel van de host**: wie aansluit ziet de kaartrug en tafel die de host gekozen heeft.

## Extra's

* **Animaties**: kaarten van tegenstanders vliegen naar de stapel, getrokken kaarten vliegen van de trekstapel
  (jouw kaarten draaien om), de aflegstapel vliegt naar wie hem pakt ("pot leeg"), blinde kaarten maken een flip,
  een 10 of vier dezelfde laat de stapel in vlammen opgaan, er wordt gedeeld bij de start, confetti voor de winnaar,
  plus kleinere overgangen (statusregel, beurt, panelen, geselecteerde kaart, menulogo). Gedeeld systeem:
  `TableFx` + `FxOverlay`; per spel vertaalt `ZweedsTableEffects` / `PestenTableEffects` de gebeurtenissen.
* **Jouw beurt op de achtergrond** (Bluetooth): trillen + melding, tot 4× herhaald elke 30 s zolang je niet
  reageert (`TurnAlerts`).
* **Opgeven** (✕ → *Opgeven*): tegen bots eindig je als pestkop; via Bluetooth neemt een bot je plaats over en
  spelen de anderen door (telt als verloren).
* **Ranglijst**: elk afgelopen potje wordt als `MatchRecord` bewaard (spelers met hun publieke device-id). Telefoons
  aan dezelfde tafel wisselen hun potjes tussen mensen uit bij het verbinden (`MATCH_HISTORY`) en krijgen elk nieuw
  potje (`MATCH_RECORDED`), zo is de ranglijst overal gelijk. Potjes tegen bots tellen alleen voor jezelf.
* **Skins**: kaartruggen en tafels vrijspelen met verschillende doelen, bijv. *Kersenrood* na 3 overwinningen,
  *Smaragd* na 10× Pesten gespeeld, *Middernacht goud* na 5× Presidenten gewonnen, *Oranje leeuw* na 10× Zweeds Pesten
  gewonnen, *Blauw vilt* na 5× Hartenjagen, *Bordeaux* na 10× terecht *Vals!*, *Leisteen* na 3× Zweeds Pesten gewonnen,
  *Nacht* na 50 potjes; *Rainbow Road* als kaartrug na 20 overwinningen en als tafel na 5× ongezien vals spelen
  (ninja). Elke vergrendelde skin toont je voortgang (*"0/5 · keer Presidenten gewonnen"*). *Diamant Holo* is een
  premium-kaartrug (€ 99, nog niet te koop). Groen vilt is de standaard; *OLED-zwart* is altijd beschikbaar. Tik op
  een skin voor een voorbeeld dat je met je vinger kantelt (ook voor nog niet vrijgespeelde skins): bij een kaartrug
  alleen de achterkant van de kaart, bij een tafel een tafeltje met kaarten.
* **Vistitels** (Zweeds Pesten): de plaatsen hebben vistitels — 1 Kibbeling, 2 Sardientje, 3 Makreel, 4 Haring,
  5 Spiering, de laatste is de Stinkvis 🐡.
* **Aflegstapel**: een teller met het aantal kaarten, en de twee kaarten eronder schuiven zichtbaar opzij, zodat je
  kunt zien of iemand vals speelde. De open kaart ligt altijd op dezelfde plek; de dikte van de stapel groeit eronder.
* **Vals spelen, strenger**: *Steeds zwaardere straf* (standaard aan): de eerste keer betrapt pak je de stapel, de
  tweede keer de stapel plus een kaart, daarna steeds een kaart meer. Bij Zweeds Pesten mag je een opgepakte kaart
  (bijv. een 7) hooguit 3× meteen terugleggen, zodat het niet eindeloos heen en weer gaat. Bij Pesten moet je na het
  pakken opnieuw *Laatste kaart!* roepen.
* **Laatste blinde kaarten**: draaien groot en langzaam om in het midden van de tafel (2,4 s, met tromgeroffel),
  groen of rood bij raak of mis.
* **Plaatjes**: heer (kroon), vrouw (tiara), boer (pet met veer) en joker (narrenkap) hebben een eigen symbool.
* **Na afloop**: *Leuke feitjes* (ninja: vals gespeeld zonder betrapt te worden, grootste valsspeler, scherpste oog, vals alarm, laatste kaart geroepen/vergeten,
  stapelvreter, …) en voor de winnaar *Deel je overwinning*: een plaatje (4:5, Instagram-formaat) via het
  deelmenu van de telefoon.
* **Ranglijst**: ook hoe vaak je terecht en onterecht *Vals!* riep en hoe vaak je betrapt bent.
* **Profielfoto**: na het kiezen bijsnijden (slepen en knijpen in een cirkel); foto's van de camera staan altijd
  rechtop (EXIF-oriëntatie).
* Knoppen onderin staan altijd op één regel; de tekst krimpt op smalle schermen.
* **Kleine schermen**: alle schermen zijn nagelopen op 320×568 (kleinste iPhone SE) en 360×640; tafels krijgen
  onder 600 dp hoogte een extra compacte stand (kleinere kaarten, geen gebeurtenisregel), tegenstanders verdelen de
  breedte en lopen bij grote tafels door op een tweede rij. Debug-builds kunnen een klein scherm nabootsen:
  `adb shell am start -n nl.bluecard.app/.MainActivity --ei sim_w 320 --ei sim_h 568`.
* **Geluid**: wie betrapt wordt hoort een sirene uit de eigen telefoon (die ook trilt) — aan een gedeelde tafel maken
  de andere telefoons dan geen geluid, zodat iedereen hoort wie het was; verder een stempelklap + zoemer bij betrapt vals spelen, "wah-wah" bij onterecht Vals!, een fluitje bij
  Vergeten! (zelf gesynthetiseerd, `res/raw`); uit te zetten in de instellingen, stil als de telefoon op stil staat.
* **Bots** wachten 1,25 s per zet (2,5 s na gelegde kaarten zodat je Vals! kunt roepen); instelbaar Rustig/Snel.
* **Talen**: Nederlands, Engels (standaard voor overige talen), Frans, Duits, Catalaans en Baskisch; kiezen onder
  *Instellingen → Taal* (per-app-taal, Android 13+ via `LocaleManager`, ouder via `AppLanguage.wrap`). Pesten heet
  in het Engels *Crazy Eights*, Frans *Huit américain*, Duits *Mau-Mau*, Catalaans *Vuit boig*, Baskisch *Zortzi eroa*;
  Zweeds Pesten heet in het Engels *Palace* (net als *Shithead*, maar zonder scheldwoord in de Play Store). Kaartletters (B/V/H, J/Q/K, V/D/R …) en voorgelezen kaartnamen komen uit de resources
  (`CardLabels`). In het Nederlands staat "Jij" in het spelverloop, in andere talen je eigen naam (dan klopt de
  werkwoordsvorm; `R.bool.events_address_viewer`). `StringResourcesTest` controleert dat elke vertaling alle
  teksten en dezelfde placeholders heeft. Het App Bundle splitst niet op taal, zodat de taalkeuze altijd werkt.
* **Avatar**: emoji + kleur of een eigen foto (128 px JPEG), zichtbaar aan tafel, in de lobby, het logboek en de
  uitslag, en via Bluetooth gedeeld met de andere spelers.
* **Lobby**: de host kan de speelvolgorde aanpassen (▲/▼) en het spel wisselen (Pesten ↔ Zweeds Pesten); de
  clients verbinden dan automatisch opnieuw en de volgorde en bots blijven behouden. Ook na een potje kun je op het
  eindscherm een ander spel kiezen.
* **Winnaar ruilt met verliezer** (huisregel, beide spellen): in het volgende potje geeft de winnaar een kaart naar
  keuze aan de verliezer en krijgt diens beste kaart terug.
* **Vals!-moment**: na elke zet zit er altijd ±1,6 s tussen de beurten; *Vals!* roepen kan daarna nog tot 10 s na
  de zet (de host houdt de tijd bij).
  Wordt iemand betrapt, dan slaat er een "VALS!"-stempel op tafel en vliegt de kaart terug (ook bij "Onterecht!" en
  "Vergeten!").
* **Tafel**: realistisch vilt met korrel en vignet, een trekstapel en aflegstapel die zichtbaar hoger worden met het
  aantal kaarten, een intro-animatie op het beginscherm en schuif-overgangen tussen schermen.
* **Logboek** (ⓘ): onderblad met tabbladen *Verloop* (tot 150 regels, met avatar, mini-kaarten en kleuren voor
  vals spelen, verbranden, winst) en *Spelregels* (de speciale kaarten plus de volledige regels met de huisregels
  van deze tafel).

## Projectstructuur

```
engine/        Kotlin Multiplatform (JVM + iOS) — kaartmodel (incl. jokers), generiek GameModule-contract en de vier
               spellen: zweedspesten/, pesten/, presidenten/, hartenjagen/ (regels, engine, views, bot)
multiplayer/   Kotlin Multiplatform — protocol + codec, Link-abstractie, GameHost, HostSession, ClientSession, LobbyProbe
shared/        Kotlin Multiplatform + Compose Multiplatform — álle schermen, sessies, opslag, teksten (5 talen),
               skins; platformzaken via nl.bluecard.app.platform.Platform. iosMain: Multipeer, geluid, fotokiezer.
app/           Android-schil — Bluetooth (RFCOMM, discovery, permissies), AdMob, foreground service, beurtmeldingen
iosApp/        Xcode-project van de iPhone-app (zie docs/IOS.md)
docs/ONTWERP.md  architectuur, datamodel, regels, protocol, schermen, teststrategie
```

Teksten staan in `shared/src/commonMain/strings/values[-nl|fr|de|es]/strings.xml` (Android-formaat; `values` =
Engels). Een Gradle-taak maakt daar `nl.bluecard.app.R` en opzoektabellen van, zodat Android en iOS dezelfde teksten
synchroon lezen (`nl.bluecard.app.res.Resources`, `stringResource`).

Belangrijkste klassen:

| Laag | Klasse | Rol |
|---|---|---|
| engine | `ZpEngine` | valideert en past acties toe (immutable state) |
| engine | `ZpRules` | pure regelfuncties (effectieve kaart, eis, speelbaarheid) |
| engine | `ZpViews` | per-speler view + legale zetten (UI beslist nooit zelf) |
| engine | `ZpBot` | bot die alleen uit legale zetten kiest |
| engine | `ZpModule` | koppelt Zweeds Pesten aan het generieke `GameModule` |
| engine | `PsEngine` / `PsRules` / `PsViews` / `PsBot` / `PsModule` | hetzelfde voor Pesten |
| engine | `PrEngine` / `PrRules` / `PrViews` / `PrBot` / `PrModule` | Presidenten |
| engine | `HjEngine` / `HjRules` / `HjViews` / `HjBot` / `HjModule` | Hartenjagen |
| multiplayer | `GameHost` | enige echte spelstatus, laat bots spelen |
| multiplayer | `HostSession` / `ClientSession` | lobby, verbindingen, heartbeat, herverbinden |
| multiplayer | `ProtocolCodec` | JSON-envelope met versie, defensief decoderen |
| app | `BluetoothController` | discovery, RFCOMM-server/-client, adapterstatus |
| app | `GameKind` / `GameBinding` | welke spellen er zijn; koppelt elk spel aan zijn module en huisregels |
| app | `SessionManager` | actieve lokale/host/client-sessie (van elk spel), autosave, service |
| app | `GameViewModel` / `GameScreen` | spelscherm Zweeds Pesten |
| app | `PestenGameViewModel` / `PestenGameScreen` | spelscherm Pesten |
| shared | `PresidentGameScreen`, `HeartsGameScreen` (+ ViewModels) | spelschermen Presidenten en Hartenjagen |
| shared | `Platform` / `AndroidPlatform` / `IosPlatform` | wat de gedeelde app van Android of iOS nodig heeft |
| app | `CardFan`, `TableParts` | gedeelde tafelonderdelen (waaier, beurtgloed, …) |

## Een ander kaartspel toevoegen

Zo is Pesten toegevoegd:

1. Implementeer `GameModule<Config, State, Action, View>` in `engine` (regels, view met legale zetten, bot,
   serializers). Gebruik `Card`, `Deck`, `DiscardPile` uit `engine.model`.
2. Registreer het in `GameCatalog`, voeg een `GameKind` + `GameBinding` toe (app) en bewaar de huisregels in
   `AppSettings`.
3. Maak een spelscherm dat met `PlayerPort<View, Action>` praat (hergebruik `CardFan`) en laat `GameRoute` het
   kiezen; voeg teksten en een huisregelscherm toe.

Protocol, lobby, Bluetooth, herverbinden en bots-planning zijn generiek en werken zonder aanpassingen.

## Bluetooth-protocol (kort)

Eén JSON-bericht per regel: `{"v":2,"seq":17,"msg":{"type":"PLAYER_ACTION",...}}`. Types: `HELLO`, `LOBBY_INFO`,
`JOIN_ACCEPTED`, `JOIN_REJECTED`, `PLAYER_LIST`, `GAME_START`, `GAME_STATE`, `PLAYER_ACTION`, `ACTION_RESULT`,
`GAME_END`, `SYNC_REQUEST`, `PING`, `PONG`, `DISCONNECT`, `ERROR`, `MATCH_RECORDED`, `MATCH_HISTORY`, `SWITCH_GAME`,
en sinds protocol v2 `SOCIAL` (buzz/reacties), `SHUFFLED` (schudronde), `SUCCESSOR` en `HANDOVER` (host-overdracht).
Oudere app-versies (v1) krijgen netjes "andere versie" te zien. Spelacties in `PLAYER_ACTION`:
`PLAY_CARD`, `PLAY_BLIND`, `PICK_UP`, `DRAW_CARD` (gokken), `SWAP`, `READY`. Details: `docs/ONTWERP.md`.
Debuggen: `adb logcat -s BlueCardNet`.

## Advertenties

* **Waar:** een adaptieve banner onderaan het startscherm en onderaan het eindscherm (winnaar en eindstand),
  ruim onder de knoppen. Nooit tijdens het spel, in de lobby of in andere menu's. Neemt geen ruimte in zolang er
  geen advertentie geladen is.
* **Rustige start:** de eerste keer dat het startscherm verschijnt nadat het 30 minuten niet in beeld is geweest
  (en de allereerste keer), blijft de banner weg (`HomeAdPolicy`, getest in `HomeAdPolicyTest`).
* **Toestemming (EER/VK):** bij het opstarten vraagt Google's User Messaging Platform (UMP) zo nodig om
  toestemming; pas daarna worden advertenties opgevraagd. Is wijzigen verplicht, dan staat onder
  *Instellingen → Over* de knop *Privacy-instellingen advertenties*.
* **ID's:** debug-builds gebruiken altijd Google's test-ID's (zo klik je nooit per ongeluk op echte
  advertenties). De release gebruikt de echte ID's uit `gradle.properties`: `bluecard.admob.appId`,
  `bluecard.admob.bannerId` (eindscherm) en `bluecard.admob.homeBannerId` (startscherm, "MainMenuBanner");
  zonder die regels valt ook de release terug op test-ID's.
* **Testapparaten:** de eigen telefoons staan in `AdsManager.TEST_DEVICES` en krijgen altijd testadvertenties
  ("Test Ad"), ook in de release. Een nieuw telefoon toevoegen: de hash staat in logcat bij
  `setTestDeviceIds`. Een nieuw advertentieblok levert de eerste uren/dagen vaak niets ("Ad failed to load : 3"
  = no fill); dat is normaal.
* **Voor publicatie:** in AdMob een GDPR-bericht (*Privacy & messaging*) aanmaken, een privacybeleid-URL in
  de Play Console invullen, bij *Data safety* advertenties/advertentie-ID aangeven en `app-ads.txt` op je
  website zetten.
* Code: `app/.../ads/AdsManager.kt` (toestemming, SDK starten) en `ads/AdBanner.kt`. Debuggen:
  `adb logcat -s BlueCardAds UserMessagingPlatform Ads`.

## Permissies

| Android | Host | Meedoen |
|---|---|---|
| 12+ | `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` | `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT` |
| 8–11 | (installatie-permissies) | `ACCESS_FINE_LOCATION` (alleen voor zoeken) |
| 13+ | optioneel `POST_NOTIFICATIONS` voor de "spel actief"-melding | idem |

Zonder Bluetooth (of zonder toestemming) blijft spelen tegen bots volledig beschikbaar.
