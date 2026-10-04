# Testrapport — 2–4 oktober 2026 (t/m ronde 9)

Toestellen: 2× Motorola moto e13 (Android 13 / API 33, 720×1600), beide met Bluetooth-naam "moto e13".
Builds: debug en release (R8-geminificeerd). De testtelefoons hebben geen internetverbinding; het spel werkt
volledig zonder (sinds de AdMob-banners op start- en eindscherm heeft de app wel de `INTERNET`-permissie).

## Geautomatiseerd (`./gradlew test`)

226 unit tests, 0 fouten. Android Lint: 0 waarschuwingen. Zie README voor de verdeling.

## Op de telefoons

| # | Scenario | Resultaat |
|---|---|---|
| 1 | Hoofdmenu, bot-setup, lokaal spel starten | ✅ |
| 2 | Ruilfase: handkaart ↔ open kaart, Klaar; bots ruilen en melden zich klaar | ✅ |
| 3 | Startspeler = laagste kaart (3♠, later 4♣) | ✅ |
| 4 | Meerdere gelijke kaarten tegelijk (2× boer), hand aanvullen van trekstapel | ✅ |
| 5 | Speelbare kaarten opgelicht, onspeelbare gedimd; tik op gedimde kaart → "Die kaart is te hoog." | ✅ |
| 6 | 7 → "7 of lager"; 10 verbrandt + "Jij mag nog een keer" | ✅ |
| 7 | Bot pakt de stapel; speler in open- en blinde-kaartenfase | ✅ |
| 8 | Lokaal spel bewaard na geforceerd stoppen van de app → "Doorgaan met spel" herstelt exact dezelfde kaarten (debug én release) | ✅ |
| 9 | Schermrotatie staand ↔ liggend: aangepaste layout, selectie blijft behouden | ✅ |
| 10 | App naar achtergrond en terug | ✅ |
| 11 | Permissie-dialogen Android 13 ("Apparaten in de buurt", meldingen) en zichtbaar-maken-dialoog | ✅ |
| 12 | Host-lobby: zichtbaar, wachten op spelers, foreground-service-notificatie | ✅ |
| 13 | Client vindt host via discovery + `HELLO(QUERY)`: "Zweeds Pesten · host Ivo · 1 speler" ondanks gelijke Bluetooth-namen | ✅ (na 2 fixes, zie ONTWERP §5) |
| 14 | Verbinden zonder koppel-dialoog; host ziet speler "Via Bluetooth"; regels zichtbaar bij client | ✅ |
| 15 | Spelstart synchroon; ieder ziet eigen hand, open kaarten van de ander, zelfde stapel/beurt/log | ✅ |
| 16 | Ruilen, spelen, verbranden door client én host over Bluetooth | ✅ |
| 17 | Client-app geforceerd gestopt → host: "Verbinding met Anna verbroken" + "Laat bot spelen"; na 60 s speelt bot automatisch | ✅ |
| 18 | Client opent app opnieuw → zoekt → verbindt met hetzelfde token → krijgt plek, kaarten en controle terug | ✅ |
| 19 | Volledig 2-spelerspotje via Bluetooth tot eindscherm; identieke eindstand op beide toestellen | ✅ |
| 20 | "Opnieuw spelen" door host → client gaat automatisch mee | ✅ |
| 21 | Host verlaat spel → client: "De host heeft het spel beëindigd." + knop naar menu, spelknoppen verborgen | ✅ |
| 22 | Release-build: host + Bluetooth-client + bot, volledig 3-spelerspotje, identieke eindstand | ✅ |
| 23 | "Terug naar lobby" door host → client gaat automatisch mee naar de lobby | ✅ |
| 24 | Sessies beëindigd → foreground-service gestopt | ✅ |

## Gevonden en opgeloste problemen

1. Bluetooth-broadcasts kwamen niet binnen (`RECEIVER_NOT_EXPORTED` → "Exported Denial"); nu `RECEIVER_EXPORTED`.
2. Bevragen van hosts faalde na een afgebroken verbinding met een BLE-apparaat; nu filter (Classic, met naam,
   telefoon/computer), telefoons eerst, pauze na mislukte poging.
3. Bot kon in de ruilfase eindeloos blijven ruilen (niet-totale sortering) — gevonden door de simulatietest.
4. Twee bots konden elkaar eindeloos "blokkeren" — nu blokkeren alleen tegen de volgende speler, met wat variatie.
5. UI: statusbalk-iconen op groen vilt, onzichtbare uitgeschakelde knop, geselecteerde kaart onder buurkaart,
   aftelling zichtbaarheid, spelknoppen bij verbroken verbinding.

## Aanpassing na feedback

* Bug: na een 7 kon je soms toch een hogere kaart spelen. Oorzaak: "altijd speelbare" kaarten (10 en de
  doorzichtige 9) sloegen de "7 of lager"-controle over. Nu geldt "7 of lager" voor alle kaarten; regressietests
  toegevoegd en de simulatietest controleert dit bij elke zet.
* De doorzichtige 9 is uit het spel gehaald (de 9 is een gewone kaart); de Shithead-voorinstelling vervalt daardoor.
* De stapel pakken mag nu altijd, ook als je kunt spelen.
* Regelversie verhoogd: een telefoon met een oude versie krijgt bij verbinden "andere versie" te zien.
* Vegen/slepen als bediening, en "vals spelen" als standaard met vinkje "Spelregels afdwingen".

### Op de telefoons getest (na feedback)

| Scenario | Resultaat |
|---|---|
| Na een 7: 9♥ gedimd, alleen ≤ 7 speelbaar; "Pak stapel" ook beschikbaar als je kunt spelen | ✅ |
| Ongeldige kaart naar stapel vegen (afdwingen aan) → springt terug + "Die kaart is te hoog." | ✅ |
| Korte flick omhoog speelt de kaart; 10 vegen verbrandt + extra beurt | ✅ |
| Eén zes aantikken + andere zes vegen → "Jij speelt 6♦ 6♥" | ✅ |
| Aflegstapel omlaag trekken → "Jij pakt de stapel (7)" | ✅ |
| Ruilfase: handkaart op open kaart slepen → geruild | ✅ |
| Vegen als Bluetooth-client: kaart landt op de stapel zonder flikkeren | ✅ |
| Vals spelen (standaard): V♥ op een aas geaccepteerd; "Vals spelen toegestaan" zichtbaar aan tafel; bots blijven eerlijk | ✅ |
| Host zet "Spelregels afdwingen" aan in de lobby → client ziet "Spelregels worden afgedwongen" | ✅ |
| Bij het testen gevonden en opgelost: geselecteerde kaart lag boven zijn buurman (tik raakte verkeerde kaart, "nogmaals tikken = spelen" speelde per ongeluk); nu natuurlijke volgorde en nogmaals tikken = deselecteren. Schakelaar alleen via het schuifje bedienbaar; nu de hele rij. | ✅ |

### Vals!, klaarleggen en vilt-instellingen

| Scenario | Resultaat |
|---|---|
| Vals gespeeld (4 op een V) → bot roept Vals! → "Vals! Bot Bas betrapt jou op 6♦. Je pakt 2 kaarten." (melding + log) | ✅ |
| "Vals!"-knop bij de stapel met "Bot Fien legde A♣ A♦ A♠" | ✅ |
| 4♦ vegen → klaargelegd (balkje, "Nog een 4? Veeg hem erbij"), 4♣ erachteraan vegen → "Jij speelt 4♣ 4♦" | ✅ |
| Instellingen en Huisregels in vilt-stijl | ✅ |
| Gevonden en opgelost: geveegde kaart bleef onzichtbaar aanraakbaar in de waaier (nu verwijderd, waaier schuift bij); aangetikte kaart ging niet mee bij *Speel* tijdens klaarleggen; "betrapt Jij" → "betrapt jou" | ✅ |

### Na pakken zelf beginnen en opengaande waaier

| Scenario | Resultaat |
|---|---|
| Huisregel *Na pakken zelf beginnen* (unit tests): na pakken/mislukte blinde kaart/mislukte gok blijft de beurt bij jou; na een Vals!-straf niet | ✅ |
| Dichte waaier met 9 en 14 kaarten: volle schermbreedte, niets valt van het scherm, alle waarde-hoeken leesbaar | ✅ |
| Zijwaarts vegen → waaier gaat open, loopt van het scherm af en scrollt tot beide uiteinden (ook de gedraaide hoek van de buitenste kaart komt volledig in beeld) | ✅ |
| In de open waaier: kaart aantikken selecteert (Speel 1), nogmaals tikken deselecteert; kaart omhoog vegen speelt hem | ✅ |
| Na de beurt sluit de waaier weer; zonder geselecteerde kaart ook na 3 s; met een geselecteerde kaart blijft hij open | ✅ |
| Opgeschoond spelscherm: veeg-hint en "trek omlaag"-hint weg; Vals!, Pak stapel en Speel samen onderin (ook Vals! buiten je beurt); klaarleg-balk onderin | ✅ |
| Ruilfase: *Klaar*-knop en uitleg weer zichtbaar (verdwenen na het samenvoegen van de knoppen; ruilfase heeft nu een eigen rij) | ✅ |
| AdMob zonder internet: toestemmingsupdate faalt netjes ("Error making request" in log), geen advertentie, app en spel werken normaal | ✅ |
| AdMob mét internet (debug, test-ID's): toestemmingsformulier bij eerste start; eerste startscherm zonder banner; terug op het startscherm → "Test Ad"-banner onderaan | ✅ |
| AdMob met de echte ID's: aanvraag gaat goed, maar "Ad failed to load : 3" (no fill) — normaal voor een nieuw advertentieblok/nog niet gepubliceerde app | ⏳ wacht op AdMob |
| Eindscherm zonder internet: geen advertentie, geen lege ruimte, knoppen werken | ✅ |
| Release gebruikt de echte AdMob-ID's, debug de test-ID's (gecontroleerd in gegenereerde BuildConfig/manifest) | ✅ |
| Meedoen: "nog geen spel" pas na het controleren; daarna blijft de app ±2,5 min kijken (elke 5 s opnieuw vragen, elke 15 s opnieuw zoeken naar nieuwe telefoons) | ✅ zonder host; met een host die pas later een tafel opent: nog door gebruiker te testen |
| Pesten tegen 2 bots: spelkeuze in "Spelen tegen bots" met regeloverzicht (pestkaarten als mini-kaarten, joker), tafel met trekstapel, aflegstapel, eis ("♠ of 7", "Pak 2 of leg een pakkaart"), richting, "Vals!" en "Pak 2"/"Pak kaart" onderin; bots spelen 7 (nog een keer), 2, 8 (overslaan), joker | ✅ |
| Pesten: 192 unit tests (o.a. 600 gesimuleerde potjes met willekeurige regels, valsspelers en meldingen; volledig Bluetooth-potje host + client + bot via in-memory verbindingen) | ✅ |
| Verbeteringenronde (animaties, meldingen, opgeven, ranglijst + Bluetooth-sync, skins, knoppen op één regel, Pesten "na pakken zelf beginnen"): unit- en sessietests groen; op toestel nog niet getest (telefoons waren in gebruik) | ⏳ |
| Dubbele status weg: "X legde …" onder Vals! verwijderd (staat al in de statusbalk) | ✅ |

### Ronde 3 (3 oktober, beide telefoons, debug én release)

| Scenario | Resultaat |
|---|---|
| Talen: testtelefoon (Engels systeem) toont Engels; *Instellingen → Taal* wisselt direct naar Français / Deutsch en terug naar Systeem; spel, regels, huisregels, logboek, uitslag en dialogen vertaald; kaartletters V/D/R (fr) en B/D/K (de) | ✅ |
| Spelverloop in andere talen: eerst "Toi commence" / "You plays" (fout) → nu je eigen naam ("Anna commence"); Nederlands houdt "Jij" | ✅ opgelost |
| Lange Duitse teksten (ruilfase-uitleg, knoppen, richting) passen op 720 px | ✅ |
| Lint na vertalen: ontbrekende meervoudsvorm *many* (fr/es) aangevuld; waarschuwing over taalsplitsing in het App Bundle → splitsen uitgezet | ✅ |
| Avatar: emoji kiezen, eigen foto via de systeemfotokiezer (bijgesneden, rond), zichtbaar aan tafel, in de lobby en via Bluetooth op de andere telefoon | ✅ |
| Logboek-onderblad met tabbladen, avatars, mini-kaarten en gekleurde regels (vals/terug) | ✅ (bots tonen nu het robot-icoon i.p.v. een letter) |
| Vals spelen (Pesten, 3 bots): bot roept Vals! → "VALS!"-stempel + kaart vliegt terug + uitleg | ✅ |
| Winnaar ruilt met verliezer: potje gewonnen (via automatische speler), *Opnieuw spelen* → "Geef Bot Bas een kaart: tik er een aan" → 3♦ weggegeven, ♣B (beste kaart bot) ontvangen, regel in logboek | ✅ |
| Lobby via Bluetooth (testtelefoon host in het Frans, eigen telefoon client in het Nederlands): speler omhoog/omlaag schuiven, ook zichtbaar bij de client | ✅ |
| Spel wisselen in de lobby (Zweeds Pesten ↔ Pesten): client verbindt automatisch opnieuw; bot blijft | ✅ — volgorde ging eerst verloren, nu hersteld op device-id |
| Pesten-potje via Bluetooth starten, tafel op beide telefoons; client geeft op → bot speelt voor hem verder op de host; host verlaat | ✅ |
| Eerste keer *Meedoen* direct na het zoeken mislukte één keer ("Verbinden mislukt") → nu één automatische herhaalpoging | ✅ opgelost (tweede poging lukte) |
| Release-APK (R8) op beide telefoons: start, menu in het Engels, potje tegen bots zonder crash | ✅ |

### Ronde 4 (3 oktober, ochtend)

| Scenario | Resultaat |
|---|---|
| Skins-scherm: tegels in een echt raster (gelijke breedte en hoogte per rij, status onderaan), ook op 320 dp | ✅ |
| Stempel "ONTERECHT!" / "¡FALSA ALARMA!" altijd op één regel (tekst krimpt) | ✅ (code; stempel zelf eerder op toestel gezien) |
| Geluidseffecten bij betrapt / onterecht / vergeten; schakelaar in Instellingen | ✅ gebouwd; geluid zelf niet afgespeeld (nacht) |
| Bots trager (1,25 s / 2,5 s na een zet) | ✅ |
| Verhuizing naar Kotlin Multiplatform + gedeelde Compose-UI: Android-app ziet er identiek uit, instellingen, avatarfoto en bewaard spel behouden, taalwissel (nl/en/fr/de/es) direct | ✅ |
| Presidenten tegen 3 bots: sets automatisch selecteren (tik = hele set), Pas, slag winnen, eindscherm met titels, 2e ronde met kaartenruil (sloeber gaf 2 beste kaarten, ik gaf er 2 terug) | ✅ |
| Hartenjagen tegen 3 bots: bekennen afgedwongen, slag in het midden met winnende kaart, scoreoverzicht na elke ronde, rondes tot 50 punten | ✅ |
| Hartenjagen via Bluetooth (testtelefoon host, eigen telefoon client, + bot): 3 × 17 kaarten, zet van de host direct zichtbaar bij de client | ✅ |
| Engine: 400 × 2 gesimuleerde Presidenten-rondes, 250 complete Hartenjagen-spellen (kaarten behouden, altijd een einde, JSON round-trip) | ✅ |
| Kleine schermen (gesimuleerd 320×568 en 360×640): menu past zonder scrollen; alle vier de tafels inclusief Zweeds Pesten-ruilfase passen; eindscherm-knoppen altijd zichtbaar; 6 tegenstanders op twee rijen | ✅ na fixes |
| iOS: alle gedeelde + iOS-code compileert voor iosArm64 en iosSimulatorArm64; Xcode-project gegenereerd en structureel gevalideerd | ✅ compileren / ⏳ linken en draaien (geen Xcode op deze Mac) |

### Ronde 5 en 6 (3 oktober, avond)

Geautomatiseerd: 466 tests, 0 fouten; Android Lint: geen meldingen; iOS-code compileert voor beide doelen.
Nieuwe tests: escalerende straf, opnieuw laatste kaart roepen, geen Vals! als je uit bent, max. 3× terugleggen,
statistieken (incl. ninja), meekijk-views voor alle vier de spellen, meekijkers/buzz/reacties/schudronde/stijl
(`TableExtrasTest`), host-overdracht (`HandoverTest`), ranglijst met vals-roepen, Catalaans/Baskisch
(alle teksten en placeholders, meervoudsregels).

| Scenario | Resultaat |
|---|---|
| Aflegstapel: teller + twee vorige kaarten zichtbaar opzij (Kibbeling) | ✅ |
| Beginscherm: paneel *Spellen in de buurt* (zonder Bluetooth-toestemming: uitleg + doorverwijzing) | ✅ (zoeken zelf niet: Bluetooth stond uit/geen toestemming) |
| Skins: OLED-zwart, Rainbow Road (bewegend); voorbeeld kantelen met de vinger, ook voor vergrendelde skins; kaartrug-voorbeeld toont alleen de achterkant | ✅ (lichte tafels na feedback weer verwijderd) |
| Schudronde tegen bots: "Jij schudt!" met voortgangsbalk; na 30 s zonder schudden wordt toch gedeeld | ✅ (het schudden zelf kon ik niet doen) |
| Ruilen in omgekeerde volgorde (eerst open kaart, dan handkaart) | ✅ |
| ⓘ: tabblad *Spelregels* met speciale kaarten én volledige regels | ✅ |
| Plaatjes heer/vrouw/boer/joker met symbool | ✅ (tiara daarna verbeterd) |
| Volledig potje Kibbeling tegen 1 bot: vistitels (Kibbeling / Stinkvis), *Leuke feitjes* met echte tellingen | ✅ |
| Release-build (R8) op de testtelefoon: start, schudronde, delen, ruilfase | ✅ |

### Ronde 7 (4 oktober: tafelscherm, unlocks, melding → meedoen, BLE, chat)

| Scenario | Resultaat |
|---|---|
| Host opent lobby → melding op de andere telefoon binnen 5 s (app op achtergrond, scherm vergrendeld) | ✅ |
| Lobby dicht → melding verdwijnt ±2 min later vanzelf (time-out) | ✅ |
| Tik op de melding → app opent, vindt de tafel en zit meteen in de lobby | ✅ |
| *Terug* uit zo'n lobby | ❌ → ✅ (bleef hangen omdat het Bluetooth-scherm niet op de stapel stond; gaat nu naar het beginscherm) |
| *📺 Als tafel meedoen*: lobby toont *"Dit scherm is de tafel…"*, host ziet *📺 Tafel: Speler*, geen plek bezet | ✅ |
| Tafelscherm in een Kibbeling-potje (host + 2 bots): alle spelers met open kaarten bovenin, stapels in het midden, *"Testhost is aan de beurt…"* | ✅ |
| Tafelscherm ging in de lobby op zwart | ❌ → ✅ (scherm blijft nu aan in lobby en spel) |
| Skins/emoji: voortgang per doel (*1/3 · keer gewonnen*, *0/5 · keer Presidenten gewonnen*, ninja, terecht Vals!) | ✅ |
| Lobby-chat, profielkaart van een bot, emoji-paneel, Bluetooth-scherm met *Meedoen* bovenaan | ✅ |

### Ronde 8 (4 oktober: nieuw beginscherm, Vals!-venster, buzzer in de broekzak, Android Auto)

Geautomatiseerd: alle tests groen (nieuw: Vals! na verder spelen, keuze welke zet, venster sluit na 10 s + ninja,
Pesten idem, bot-niveau voor alle bots), Android Lint schoon, iOS compileert.

| Scenario | Resultaat |
|---|---|
| Beginscherm: *Spel starten* + *Meedoen met een spel*, geen *Spelen tegen bots* meer; naam *Zweeds Pesten* | ✅ |
| *Spel starten* → eigen tafel met spelkeuze bovenaan, *Bot toevoegen*, *Moeilijkheid* (bot wordt *Bot · Makkelijk*) | ✅ |
| Alleen bots aan tafel → *Start spel* wordt een gewoon bewaard spel (*"Je spel wordt bewaard"*, *Doorgaan met spel*) | ✅ |
| Meekijker die vanaf het beginscherm instapte → na het potje (host: *Terug naar lobby*) in de lobby, krijgt een plek | ✅ (was: terug naar het beginscherm) |
| Zoeken op *Meedoen*: aflopende balk *"Zoeken naar telefoons… nog 1:25"* | ✅ |
| Open kaart op de aflegstapel: zelfde plek bij 2, 3 en 4 kaarten | ✅ |
| *Vals!* blijft na een botzet zichtbaar terwijl jij al aan de beurt bent (binnen 10 s) | ✅ |
| Buzz met het scherm van de ontvanger uit: trilling (als meldingstrilling) + melding *"Testhost buzzt je: jouw beurt!"* met buzzergeluid | ✅ (gezien in `dumpsys vibrator_manager`/`notification`) |
| Meekijker/tafel ✕: tekst was "een bot neemt je plaats over… telt als verloren" | ❌ → ✅ (nu *"Je kijkt mee. Stoppen met kijken?"*) |

### Ronde 9 (4 oktober: tafels sneller vinden)

Gemeten met de testtelefoon als host en de andere als zoeker (tijd tot *"Tafel van Testhost"* in beeld, inclusief
±1 s meetvertraging van het testscript):

| Situatie | Voor | Na |
|---|---|---|
| *Meedoen*, host nog nooit gevonden | 25,9 s (zoekactie 19,5 s, dan pas controleren) | 7,9 s (zoeken stopt bij de eerste telefoon) |
| *Meedoen*, host eerder gevonden (adres in de aankondiging) | 25,9 s | 4,9 s, zonder zoekactie |
| Beginscherm *Spellen in de buurt* | tot ±60 s (zoekactie maar elke 3e ronde) | 2,2 s na het openen van de app |
| Host vraagt om *zichtbaar maken* | elke lobby | alleen zolang zijn adres nog onbekend is |

De host leerde zijn adres van de eerste controle (`own_address` = 74:BE:F3:15:99:9B, klopt). Nieuwe tests:
aankondiging met adres/lopend spel, lange naam past nog, verborgen/ongeldige adressen weg, versie 1 wordt nog gelezen.

## Niet op toestel getest

* Android 8–11 (locatiepermissie voor zoeken) — code-pad aanwezig, geen toestel beschikbaar.
* De iPhone-app (geen Xcode op deze Mac): linken, simulator, Multipeer tussen iPhones, fotokiezer en geluid op iOS.
* Presidenten via Bluetooth (wel Hartenjagen via Bluetooth en Presidenten tegen bots).
* Winnaar-ruil via Bluetooth (wel met bots op het toestel en in unit tests).
* Meer dan 2 telefoons tegelijk (een volledig potje met host, twee clients en een bot is als unit test over in-memory verbindingen gedekt).
* **Ronde 5/6 via Bluetooth**: meekijken/later instappen, host-overdracht en *Spellen in de buurt* zijn later wel
  op twee telefoons getest; gedeelde skins van de host en schudden door een andere speler alleen in unit tests.
* Tafelscherm op een echte tablet (alleen op een telefoon getest; de schaal tot 2,6× zit in code) en in Pesten,
  Presidenten en Hartenjagen (zelfde layout-component, op het toestel alleen Kibbeling).
* Chat en emoji-reacties tussen twee telefoons tijdens een potje (wel in unit tests); de buzzer wel (ronde 8).
* Een *late* Vals! op het toestel (na een zet van een ander) en de keuzelijst bij meerdere verdachte zetten — de
  regels zelf zitten in unit tests; op het toestel alleen gezien dat de knop binnen 10 s blijft staan.
* Een drukke omgeving met veel Bluetooth-apparaten (hier alleen de twee testtelefoons, een laptop en de playtest-telefoon).
* **Android Auto**: geen Android Auto-app op de testtelefoons en geen autoscherm-emulator (DHU) op deze Mac; alleen
  gecompileerd. Testen: Android Auto installeren, ontwikkelaarsmodus + *Onbekende bronnen*, debug-build.
* *Spel starten* zonder Bluetooth-toestemming / met Bluetooth uit (alleen-bots-tafel met *Toestaan*): de toestemming
  stond al aan en Bluetooth uitzetten is een systeeminstelling.
* Fysiek schudden (sensor) en trillen; het "betrapt"-geluid uit de eigen telefoon; het deelmenu met Instagram
  (ik verloor het testpotje; delen verschijnt alleen voor de winnaar); bijsnijden van een profielfoto (fotokiezer).
* Bluetooth uit- en aanzetten tijdens een spel (automatisch heropenen van de server is geïmplementeerd,
  maar niet op het toestel geprobeerd om de Bluetooth-instelling van de telefoons niet te wijzigen).
