# BlueCard — Ontwerp

Offline Android-kaartspelapp met twee spellen: **Zweeds Pesten** (internationaal bekend als *Shithead*) en
**Pesten** (kleur of waarde volgen, pakkaarten, "Laatste kaart!").
Multiplayer via **Bluetooth Classic (RFCOMM)**, host-authoritative. Geen internet nodig, geen server, geen account. AdMob-banner op start- en eindscherm (zie README, "Advertenties").

---

## 1. Projectstructuur

```
bluecard-v2/
├── settings.gradle.kts            # modules: :engine, :multiplayer, :app
├── build.gradle.kts               # root: plugin-declaraties
├── gradle.properties
├── gradle/libs.versions.toml      # versiecatalogus (alle versies op één plek)
├── gradle/wrapper/…               # Gradle 8.14.3
├── docs/ONTWERP.md                # dit document
├── engine/                        # PURE KOTLIN (geen Android) — kaartmodel, generieke game-API, Zweeds Pesten, bots
│   └── src/main/kotlin/nl/bluecard/engine/
│       ├── model/                 # Card, Rank, Suit, Deck, DiscardPile, PlayerInfo
│       ├── core/                  # GameModule (generiek contract), ActionResult, GameResult, BotDifficulty, GameCatalog
│       └── zweedspesten/          # ZpHouseRules, ZpGameState, ZpAction, ZpEngine, ZpRules, ZpPlayerView, ZpBot, ZpModule
├── multiplayer/                   # PURE KOTLIN — protocol, transport-abstractie, host/client-sessies
│   └── src/main/kotlin/nl/bluecard/multiplayer/
│       ├── protocol/              # NetMessage, Envelope, ProtocolCodec, ProtocolVersion
│       ├── transport/             # Link, LinkAcceptor, InMemoryLink (tests/loopback)
│       └── session/               # GameHost, HostSession, ClientSession, PlayerPort, LobbyState, SeatInfo
└── app/                           # ANDROID — Bluetooth-implementatie, opslag, service, Compose UI
    └── src/main/java/nl/bluecard/app/
        ├── BlueCardApp.kt, AppContainer.kt, MainActivity.kt
        ├── bluetooth/             # RFCOMM Link/Acceptor, discovery, probe, permissies, adapterstatus
        ├── data/                  # SettingsRepository (DataStore), SavedGameRepository (lokaal spel hervatten)
        ├── settings/              # AppSettings-model
        ├── service/               # MultiplayerService (foreground service tijdens BT-sessie)
        ├── session/               # SessionManager: actieve lokale/host/client-sessie
        └── ui/                    # theme, components (kaarten), navigation, screens (menu, setup, lobby, game, result, rules, settings)
```

**Waarom drie modules?** De compiler *dwingt* af dat engine en multiplayer-logica geen Android-API's gebruiken.
Daardoor zijn ze volledig met snelle JVM-unit-tests te testen, inclusief complete multiplayer-potjes over in-memory verbindingen.

## 2. Architectuur

```
 ┌──────────────── app (Android) ────────────────┐
 │  Compose UI  ──►  ViewModels (StateFlow)      │
 │                      │                        │
 │                SessionManager                 │
 │        ┌────────────┼──────────────┐          │
 │   Lokaal spel    Host-sessie    Client-sessie │
 │        │            │  ▲             │ ▲      │
 │        │   BluetoothLinkAcceptor   BluetoothLink (RFCOMM)
 └────────┼────────────┼──┼─────────────┼─┼──────┘
          ▼            ▼  │             ▼ │
 ┌──────── multiplayer (pure Kotlin) ─────────────┐
 │ GameHost (authoritative) · HostSession ·       │
 │ ClientSession · ProtocolCodec · Link           │
 └────────────────────┬───────────────────────────┘
                      ▼
 ┌──────── engine (pure Kotlin) ──────────────────┐
 │ GameModule<C,S,A,V>  ◄── ZpModule (Zweeds Pesten)
 │                      ◄── PsModule (Pesten)     │
 │ ZpEngine · PsEngine · bots · Card/Deck/…       │
 └────────────────────────────────────────────────┘
```

* **Engine**: deterministische, immutable state-machine. `apply(state, playerId, action)` geeft `Accepted(newState)` of `Rejected(reason)`.
  De UI bepaalt nooit zelf of een zet geldig is: de engine levert per speler een `view` met `legal` (speelbare kaarten, mag pakken, …)
  en valideert iedere ingediende actie opnieuw.
* **GameModule** is het generieke contract voor een kaartspel (config, state, actie, view, bot, serializers).
  Een nieuw kaartspel = een nieuw `GameModule` + een spelscherm. Protocol, sessies, lobby en Bluetooth blijven ongewijzigd.
* **GameHost** houdt de enige echte spelstatus bij, laat bots spelen en publiceert per stoel een view.
  *Lokaal tegen bots* gebruikt exact dezelfde `HostSession`, alleen zonder Bluetooth-acceptor.
* **HostSession/ClientSession** vertalen tussen `Link` (regel-gebaseerde tekstverbinding) en GameHost/UI.
* **Bluetooth-laag** (app) implementeert alleen `Link` en `LinkAcceptor` op RFCOMM-sockets + discovery/permissies.
* **UI** praat uitsluitend met `PlayerPort<V, A>` (view + submit), ongeacht of het spel lokaal, als host of als client loopt.

**Meerdere spellen in de app**: `GameKind` (Zweeds Pesten, Pesten) + `GameBinding<C,S,A,V>` koppelen een spel aan
zijn `GameModule` en aan de plek van zijn huisregels in de instellingen. `SessionManager` en `ActiveSession` zijn
spel-onafhankelijk (`ActiveSession.game`); generieke hulpfuncties maken een `HostSession`/`ClientSession` voor de
gekozen binding. Een client kent het spel van de host uit de lobby-info (`gameId`) en kiest daarmee de juiste
module; een client met het verkeerde spel wordt geweigerd. Het opgeslagen spel bewaart `gameId` + de state als
JSON (formaat 2; formaat 1 = Zweeds Pesten blijft leesbaar). `GameRoute` toont het spelscherm van het lopende spel;
het eindscherm is spel-onafhankelijk (`ResultViewModel` + `GameBinding.summarize`).

**Pesten-engine** (`engine/pesten`): `PsGameState` (handen, trekstapel, aflegstapel, richting, `pendingDraw`
voor pakkaarten, `wishedSuit` na een boer, `drawnCard` na het pakken, `forgottenId` voor "Vergeten!", `lastPlay`
voor "Vals!"). Acties: `Play(cards, suit)`, `Draw`, `Pass`, `CallLastCard`, `CatchLastCard`, `Challenge` — de laatste
drie ook buiten je beurt. `Draw`/`Pass` sluiten het venster voor "Vals!" en "Vergeten!". Lege trekstapel → de
aflegstapel (behalve de bovenste kaart) wordt geschud. Bots spelen eerlijk, melden hun laatste kaart (makkelijke
bots vergeten het soms), roepen "Vergeten!" en "Vals!" op basis van wat er op tafel lag.

Toestand leeft in een app-brede `SessionManager` (overleeft schermrotatie). Tijdens een Bluetooth-sessie draait een
foreground service (`connectedDevice`) zodat de verbinding blijft bestaan als de app naar de achtergrond gaat.
Lokale spellen worden na iedere zet als JSON opgeslagen en kunnen na het herstarten van de app worden hervat.

## 3. Datamodel

| Klasse | Omschrijving |
|---|---|
| `Suit` | CLUBS ♣, DIAMONDS ♦, HEARTS ♥, SPADES ♠ |
| `Rank` | TWO(2) … TEN(10), JACK(11, "B"), QUEEN(12, "V"), KING(13, "H"), ACE(14, "A") |
| `Card` | rank + suit; JSON-vorm compact `"10H"`, `"QS"` |
| `Deck` | immutable trekstapel (bovenkant = laatste element), `draw(n)`, `shuffled(random)` |
| `DiscardPile` | immutable aflegstapel, `top`, `topRun` (vier dezelfde) |
| `PlayerInfo` | id + naam |
| `ZpHouseRules` | effect per rang, toggles, handgrootte, startregel |
| `ZpPlayerState` | hand, open kaarten, blinde kaarten, klaar-status, eindpositie |
| `ZpTurnState` | huidige speler, richting |
| `ZpGameState` | fase, spelers, trekstapel, aflegstapel, verbrand, beurt, log, versie, seed |
| `ZpAction` | Swap, Ready, Play(cards), PlayBlind(index), PickUp, Gamble |
| `ZpEvent` | logregels: gespeeld, gepakt, verbrand, overgeslagen, richting, klaar, uit, … |
| `ZpPlayerView` | wat één speler mag zien + `ZpLegalMoves` |
| `GameResult` | eindstand (ranking) |

## 4. Spelregels & engine (Zweeds Pesten)

**Opzet**: 52 kaarten, 2–5 spelers. Ieder krijgt 3 blinde kaarten, daarop 3 open kaarten, en 3 handkaarten (instelbaar 3–5).
De rest is de trekstapel.

**Ruilfase** (aan/uit): iedereen mag handkaarten met open kaarten ruilen en meldt zich daarna *klaar*.

**Startspeler**: de speler met de laagste "gewone" handkaart (klaveren 3 vóór ruiten 3 …), of willekeurig.

**Beurt**:
1. Bron: zolang je handkaarten hebt speel je uit je hand, dan je open kaarten, dan je blinde kaarten.
2. Je speelt één kaart, of (indien toegestaan) meerdere kaarten van dezelfde rang, die **gelijk of hoger** is dan de effectieve bovenste kaart.
3. Na het spelen vul je je hand aan tot de handgrootte zolang de trekstapel niet leeg is.
4. Kun je niet spelen: je pakt de hele aflegstapel (dit is de straf). Je beurt is voorbij.
5. Blinde kaart: je kiest blind een kaart. Is hij geldig dan wordt hij gespeeld; anders pak je de stapel plus die kaart.

**Speciale kaarten** — per rang configureerbaar (`ZpEffect`):

| Effect | Werking | Standaard |
|---|---|---|
| `RESET` | altijd speelbaar; daarna mag alles | 2 |
| `LOWER` | volgende speler moet deze waarde of lager spelen — geldt voor álle kaarten, ook een 10 | 7 |
| `BURN` | speelbaar op alles behalve na een `LOWER`-kaart; aflegstapel wordt verbrand, zelfde speler nog een keer | 10 |
| `SKIP` | volgende speler(s) slaan over (één per gespeelde kaart) | — (optioneel, bv. 8) |
| `REVERSE` | speelrichting draait om | — (optioneel) |

**Overige huisregels**: meerdere gelijke kaarten tegelijk; vier dezelfde verbrandt de stapel; extra beurt na verbranden;
*gokken van de trekstapel* (bovenste kaart omdraaien: geldig → gespeeld, anders stapel pakken);
verbrande kaarten herschudden tot nieuwe trekstapel als die leeg is; doorspelen tot de laatste of stoppen bij de eerste winnaar.

**Einde**: wie al zijn kaarten (hand, open, blind) kwijt is, is uit en krijgt een positie. De eerste is de winnaar;
de laatste overgebleven speler is de *pestkop*. De laatste kaart wordt correct afgehandeld: als een speler uitgaat met een
verbrandingskaart gaat de extra beurt naar de volgende actieve speler.

**Validatie**: elke actie wordt gecontroleerd op fase, beurt, eigendom van kaarten, bron, rang-gelijkheid, waarde-eis
en huisregels. Afwijzing gebeurt met een `ZpRejectReason`-code die de app naar een Nederlandse melding vertaalt.

**Vals spelen** (`enforceRules`, standaard uit): zonder afdwingen accepteert de engine elke kaart uit je huidige bron,
zoals aan een echte tafel. Beurt, eigendom, bron en "zelfde waarde" blijven altijd gelden; blinde kaarten en gokken
worden altijd volgens de regels beoordeeld. De view geeft `playableCards` (wat geaccepteerd wordt) én `fittingCards`
(wat volgens de regels past; voor hints). Bots spelen alleen `fittingCards`. De host kiest bij het starten.

**"Vals!"**: na elke `Play` zonder afdwingen bewaart de state `lastPlay` (wie, welke kaarten, of het legaal was, de
eis op dat moment en een momentopname van de state ervóór). Tot de volgende beurtactie mag iedere andere speler
`Challenge` sturen, ook buiten zijn beurt. Terecht: de momentopname wordt teruggezet (ook burn/skip/aanvullen),
de valsspeler krijgt zijn kaarten terug plus de stapel en verliest de beurt. Onterecht: de beschuldiger pakt de
stapel (en verliest zijn beurt als hij aan de beurt was). Clients zien nooit of de zet legaal was (`ZpChallengeView`
bevat alleen openbare info). Uitgaan met een valse laatste kaart wordt geweigerd. Bots betrappen valse zetten (85% /
35%) en wachten in deze situatie langer zodat mensen kunnen reageren (`GameModule.botDelayMs`).

**Klaarleggen**: veeg je een kaart terwijl je meer kaarten van die waarde hebt, dan legt de app hem lokaal klaar op
de stapel (`staged`) en wacht 3 s op meer; daarna, of bij *Speel*, gaat één `Play` met alle klaargelegde kaarten naar
de host. De engine ziet dus gewoon één zet met meerdere kaarten.

## 5. Bluetooth-protocol

* Transport: **RFCOMM (Bluetooth Classic)**, insecure socket met vaste service-UUID → geen koppel-dialoog nodig,
  betrouwbare geordende stream, ruim voldoende bandbreedte. BLE is niet nodig.
* Framing: één JSON-bericht per regel (`\n`), UTF-8, max 256 KB. Leesbaar met logcat.
* Envelope: `{"v":1,"seq":17,"msg":{"type":"PLAYER_ACTION", …}}`. `v` = protocolversie, eerst gecontroleerd.

| Type | Richting | Inhoud |
|---|---|---|
| `HELLO` | C→H | naam, token (voor herverbinden), intent JOIN of QUERY, app-versie |
| `LOBBY_INFO` | H→C | antwoord op QUERY (host, spel, aantal spelers, fase) |
| `JOIN_ACCEPTED` | H→C | toegekende speler-id, herverbonden ja/nee |
| `JOIN_REJECTED` | H→C | LOBBY_FULL / GAME_IN_PROGRESS / VERSION_MISMATCH |
| `PLAYER_LIST` | H→C | lobby: stoelen, verbindingsstatus, regels, fase |
| `GAME_START` | H→C | spel begint (stoelen) |
| `GAME_STATE` | H→C | persoonlijke view + statusversie |
| `PLAYER_ACTION` | C→H | actionId + spelactie (Play/PickUp/PlayBlind/Gamble/Swap/Ready) |
| `ACTION_RESULT` | H→C | geaccepteerd/afgewezen + reden |
| `GAME_END` | H→C | eindstand |
| `SYNC_REQUEST` | C→H | vraag volledige status opnieuw |
| `PING`/`PONG` | beide | heartbeat (4 s), time-out 15 s |
| `DISCONNECT` | beide | netjes afmelden met reden |
| `ERROR` | beide | MALFORMED / UNKNOWN_TYPE / VERSION_MISMATCH / … |

**Host authoritative**: clients sturen alleen intenties; de host valideert met de engine en stuurt iedereen zijn eigen view.
Clients zien nooit andermans handkaarten. **Dubbele acties**: per speler oplopend `actionId`; herhaalde id's worden genegeerd.
**Herverbinden**: client stuurt hetzelfde token; host koppelt de stoel opnieuw en stuurt de volledige status.
Een verbroken speler kan door de host aan een bot worden overgedragen.
**Corrupte/onbekende berichten**: worden gelogd en beantwoord met `ERROR`, verbinding blijft open (na 20 fouten verbroken).

**Vinden van spellen**: de host maakt zich zichtbaar (systeemdialoog) en luistert op de service-UUID.
De client zoekt apparaten, en vraagt ieder gevonden apparaat met `HELLO(QUERY)` om lobby-info. Zo ziet de client
"Zweeds Pesten — host Ivo — 2/5" ook als alle telefoons dezelfde Bluetooth-naam hebben.
Alleen apparaten die een host kúnnen zijn worden bevraagd (Classic Bluetooth, met naam, apparaatklasse
telefoon/computer/onbekend), telefoons eerst, met een korte pauze na een mislukte poging.

### Bevindingen bij testen op echte toestellen (2× Moto e13, Android 13)

* Bluetooth-broadcasts (`ACTION_FOUND`, `ACTION_DISCOVERY_FINISHED`, `ACTION_STATE_CHANGED`) worden door het
  Bluetooth-proces (uid 1002) verstuurd. Een ontvanger met `RECEIVER_NOT_EXPORTED` krijgt ze **niet**
  ("Exported Denial" in logcat). Het zijn beschermde broadcasts, dus `RECEIVER_EXPORTED` is veilig en vereist.
* Classic discovery levert ook veel naamloze BLE-apparaten op. Een afgebroken RFCOMM-verbindingspoging naar
  zo'n apparaat houdt de controller even bezet: de volgende pogingen falen dan direct ("read failed, socket
  might closed"). Vandaar het filter, de volgorde en de pauze hierboven.
* Insecure RFCOMM verbindt zonder koppel-dialoog (de stack doet kort een "just works"-bond).
* Een client die >60 s weg is wordt automatisch door een bot vervangen; bij herverbinden met hetzelfde token
  krijgt de speler direct weer de controle.

## 6. Schermen

1. **Hoofdmenu** — Spelen tegen bots · Bluetooth spel · Spelregels · Instellingen (+ "Doorgaan" bij opgeslagen spel).
2. **Bot-setup** — aantal bots (1–4), moeilijkheid, huisregels → Start.
3. **Bluetooth** — keuze *Spel aanmaken* / *Meedoen*, eigen naam.
   * Host-lobby: zichtbaar maken, verbonden spelers, bots toevoegen, regels, Start.
   * Client: zoeken, gevonden spellen, verbinden, wachten op host.
4. **Spelscherm** — tegenstanders (handaantal, open/blind), aflegstapel (effectieve kaart + eis), trekstapel, verbrand,
   actieve effecten, log, eigen tafelkaarten, hand, beurtmelding. Alle knoppen die je tijdens het spel nodig hebt
   (*Vals!*, *Pak stapel*, *Gok*, *Speel*, en tijdens klaarleggen *Terug*/*Speel*) staan samen in één balk onderin;
   het midden toont alleen stapels, eis en verbrand-teller, en "Vals spelen toegestaan" staat klein onder de titel.
   Bediening zoals aan tafel: kaart omhoog vegen/slepen naar de stapel = spelen (aangetikte kaarten van dezelfde
   waarde gaan mee), aflegstapel naar je toe trekken = pakken, blinde of open tafelkaart vegen = spelen.
   Ruilfase: handkaart op een open kaart slepen (of na elkaar tikken) + Klaar. Tikken + knoppen blijven werken.
   Layout v2: de hand is een gebogen, overlappende waaier (`FanHand`; afstand en hoek krimpen met de handgrootte en
   houden rekening met de gedraaide hoeken, zodat ook 15+ kaarten zonder scrollen passen). Dicht gebruikt de waaier
   de volle schermbreedte zonder van het scherm af te lopen; zijwaarts vegen opent hem verder
   (`fanSlotsOpen`: vaste ruime afstand, vlakkere boog, mag van het scherm aflopen en scrollt mee). Hij sluit zodra
   je beurt voorbij is, of 3 s na het laatste vegen als er geen kaart geselecteerd is. De eigen tafelkaarten
   liggen half achter de waaier (alleen de waarde-hoeken zichtbaar); in de ruilfase staan ze vrij erboven en zodra de
   hand leeg is komen ze groot naar voren. Beurt = warme gloed onder de hand. Tegenstanders tonen hun hand als klein
   waaiertje kaartruggen naast hun open kaarten.
   Techniek: `CardDragState` + `DraggableCard` + `DragOverlay` (kaart wordt boven alles getekend, alleen
   omhoog gerichte bewegingen starten een sleep zodat de hand horizontaal blijft scrollen; fling telt als spelen).
5. **Einde** — winnaar, eindstand, Opnieuw spelen, Terug naar menu.
6. **Spelregels** — uitleg + actuele effecttabel.
7. **Instellingen** — naam, avatar (emoji/kleur/foto), taal, botsnelheid, standaardmoeilijkheid, hints, scherm aan
   houden, huisregels per spel.
8. **Ranglijst** en **Skins** — vanuit het hoofdmenu.

**Talen** (ronde 3): `values/` is Engels (valt terug voor alle niet-ondersteunde talen), `values-nl/`,
`values-fr/`, `values-de/`, `values-es/`. Alle teksten staan in resources, ook kaartletters en voorgelezen
kaartnamen (`CardLabels`). De vertalingen zijn gegenereerd uit één tabel per taal met een controle op ontbrekende
namen en afwijkende placeholders; `StringResourcesTest` bewaakt dat bij elke build.

**Spelwissel in de lobby**: `HostSession.switchGame` stuurt `SWITCH_GAME`; de host opent een nieuwe sessie voor het
andere spel (bots gaan mee), clients verbinden automatisch opnieuw (`SessionManager.rejoin`) en
`restoreSeatOrder` zet iedereen terug in de volgorde die de host had ingesteld (op device-id).

**Stijl**: alle schermen op weg naar het spel (Bluetooth, Jouw spel, Meedoen, Spelen tegen bots) gebruiken dezelfde
"kaarttafel"-look als het beginscherm en het spelscherm: groen vilt, lichte tekst, halfdoorzichtige panelen, gele
hoofdknop, spelers als avatars aan tafel en vrije plekken als gestippelde stoelen (`ui/components/Felt.kt`,
`ui/screens/LobbyComponents.kt`). Instellingen, Huisregels en Spelregels blijven lichte leesschermen.

## 7. Teststrategie

* `engine` (JUnit, JVM): kaart spelen, ongeldige kaart, trekken/aanvullen, beurtwissel, alle speciale effecten,
  meerdere spelers, winnen/eindstand, lege trekstapel, reshuffle, huisregels, dubbele acties, verkeerde speler,
  views lekken geen verborgen kaarten, en **simulaties van honderden complete bot-potjes** met willekeurige regels
  (kaartbehoud: altijd precies 52 kaarten, geen afwijzingen, spel eindigt altijd).
* `multiplayer`: codec round-trip voor alle berichttypes, versieverschil, corrupte/onbekende berichten,
  complete host+client-potjes over in-memory links, dubbele acties, actie van verkeerde speler, verbreken/herverbinden,
  lobby vol / spel bezig.
* `app`: build + lint; handmatige/geautomatiseerde test op twee fysieke telefoons via adb.

## 8. Consistentiecheck van het ontwerp

* Max 5 spelers: 5 × (3+3+3) = 45 ≤ 52. Handgrootte 5 → max 4 spelers (4 × 11 = 44); de engine valideert dit.
* `LOWER` gaat vóór "altijd speelbaar": na een 7 mag alleen 7 of lager (een 2 dus wel, een 10 niet).
* De stapel pakken mag altijd zolang er iets op ligt, ook als je kunt spelen en ook in de open/blinde fase.
* Extra beurt na verbranden + speler gaat uit: beurt gaat naar volgende actieve speler.
* `SKIP` bij 2 spelers: tegenstander overslaan = zelf weer. `REVERSE` bij 2 spelers: geen effect op volgorde.
* Ruilfase is gelijktijdig voor alle spelers → geen "stale state"-afwijzing op basis van versie, maar engine-validatie + `actionId`.
* Herschudden alleen uit verbrande kaarten (aflegstapel is in dit spel altijd "levend").
* Het spel zelf gebruikt geen netwerk; `INTERNET` komt alleen mee met de AdMob-SDK (banners op start- en eindscherm,
  toestemmingsformulier). Zonder verbinding of toestemming: geen advertentie, verder niets anders.

## 9. Ronde 4 — vier spellen, gedeelde UI en iOS

**Architectuur.** `engine` en `multiplayer` zijn Kotlin Multiplatform (JVM voor Android, iosArm64 en
iosSimulatorArm64). Alle UI en app-logica is verhuisd naar `shared` (Compose Multiplatform). Wat per platform
verschilt komt binnen via `nl.bluecard.app.platform.Platform`: bestandsopslag (`FileStore`), verbinding met
telefoons in de buurt (`GameTransport`: Bluetooth RFCOMM op Android, Multipeer Connectivity op iOS), geluid,
advertenties, taal, en een paar UI-haken (`PlatformUi`: multiplayer-schermen, terugknop, fotokiezer, scherm aan,
statusbalk). De Android-app (`app`) is daardoor een dunne schil; de iOS-app (`iosApp`) toont alleen
`MainViewController()`. Instellingen blijven in hetzelfde DataStore-bestand; opgeslagen spel en ranglijst in
dezelfde bestanden, dus bestaande installaties houden hun gegevens.

**Teksten.** In plaats van Android-resources (niet bruikbaar op iOS) genereert een Gradle-taak uit dezelfde
`strings.xml`-bestanden een `R`-object plus tabellen per taal met eigen meervoudsregels (CLDR voor nl/en/de/fr/es)
en Android-achtige formattering (`%1$s`, `%2$d`, `%%`). Zo bleef de UI-code vrijwel ongewijzigd en is alles
synchroon beschikbaar (ook buiten Compose, bv. meldingen).

**Presidenten** (`engine/presidenten`). Alle kaarten worden gedeeld. Wie uitkomt legt een set (1–4 gelijke, jokers
vullen aan); de volgende moet even veel kaarten van een hogere waarde leggen of passen. Niemand meer hoger → wie het
laatst legde wint de slag en komt opnieuw uit; een onverslaanbare set (2 zonder jokers) wint de slag meteen.
Huisregels: 2 hoog/laag, 0–2 jokers, kaartenruil, "pas is pas", "gelijk is overslaan". Volgende ronde
(`newGame(previous)`): titels uit de vorige uitslag; de sloeber geeft zijn 2 beste kaarten aan de president (vanaf
4 spelers ook vice's 1 kaart), die zelf kiest wat hij teruggeeft; daarna komt de sloeber uit.

**Hartenjagen** (`engine/hartenjagen`). Slagen van één kaart per speler, bekennen verplicht. Strafpunten: harten 1,
schoppenvrouw 5 (NL) of 13, klaverboer 2 (NL) of 0. Optioneel: 3 kaarten doorgeven (links/rechts/over/geen), harten
pas uitspelen als ze gebroken zijn, geen punten in de eerste slag, de maan schieten, laagste klaveren komt uit. Er
worden rondes gespeeld tot iemand de grens haalt (1 ronde, 30, 50 of 100); de laagste score wint
(`RankingEntry.score`). Met 3/5/6 spelers gaan er lage ruiten/schoppen uit zodat iedereen even veel krijgt.

**Vals spelen** bestaat alleen in de twee pestspellen; Presidenten en Hartenjagen dwingen de regels altijd af.


## 10. Ronde 5/6 — samen aan tafel, protocol v2

**Protocol v2** (`ProtocolVersion.CURRENT = 2`, minimaal 2): telefoons met v1 krijgen "andere versie". Nieuw:

| Bericht | Richting | Doel |
|---|---|---|
| `SOCIAL(kind, to?, from)` | client → host → iedereen | buzz (`BUZZ`, met doelspeler) of reactie (`HEART`, `THUMBS_UP`, `CRY`, `LAUGH`). De host vult `from` in, controleert de limiet (`RateLimiter`: 3 buzzes/min, 6 reacties/10 s per persoon) en stuurt het door. |
| `SHUFFLED(entropy)` | schudder → host | de schudder is klaar; `entropy` (uit de sensorwaarden) gaat mee in de seed van het delen. |
| `SUCCESSOR(seatId, address, name)` | host → iedereen | wie de tafel overneemt als de host wegvalt en hoe die te bereiken is (Bluetooth-adres / Multipeer-naam via `Link.remoteAddress`). |
| `HANDOVER(HostSnapshot)` | host → opvolger | de hele tafel: spelstate, stoelen, meekijkers, tokens, regels, vorige uitslag, schudbeurt, stijl. Bij elke statewijziging opnieuw. |

`LobbySnapshot` kreeg `spectators`, `style` (kaartrug + tafel van de host, toegepast door `Skins.applyHost`) en
`shufflerId`; `SessionPhase` kreeg `SHUFFLING`; `JoinAccepted.spectator`; `DisconnectReason.HOST_MOVED`.

**Meekijkers**: een `HELLO(JOIN)` tijdens een spel (of bij een volle tafel) wordt een meekijker (max. 4) in plaats
van een weigering. Ze krijgen views via `module.view(state, id)` — elke engine geeft voor een onbekende kijker een
view zonder handkaarten en zonder zetten (getest in `SpectatorViewTest`); acties worden geweigerd (`NOT_PLAYING`).
Bij `startGame`/`returnToLobby` schuiven meekijkers door naar vrije stoelen. Spelers die al uit zijn mogen geen
*Vals!*/*Vergeten!* roepen (engine: `NOT_PLAYING`).

**Schudronde**: `HostSession.startGame()` zet bij `shuffleRitual` de fase op `SHUFFLING` met een schudder (om de beurt
in stoelvolgorde), wacht (buiten de lock) op `SHUFFLED`, een bot (2,5 s), het verdwijnen van de schudder of 30 s, en
deelt dan. De app toont dit centraal (`SessionOverlay` boven alle schermen): de schudder ziet een voortgangsbalk die
alleen vult zolang de versnellingsmeter > 5 m/s² meet (3 s nodig); anderen zien "Ivo schudt de kaarten…", de host kan
na 10 s overslaan. Zonder sensor: tikken.

**Host-overdracht**: de opvolger is de eerste verbonden menselijke speler met een bekend adres. Bij `leaveWithHandover`
(de host verlaat de tafel) krijgt iedereen `DISCONNECT(HOST_MOVED)`; bij een weggevallen host merkt de client het na de
mislukte herverbindpogingen (`Lost(CONNECTION_LOST)`). `SessionManager.hostGone`:
* de opvolger maakt een `HostSession`, roept `adopt(snapshot, eigenStoel)` aan (eigen stoel wordt HOST, de oude host
  wordt een stoel die een bot speelt tot hij terugkomt met zijn token, de anderen staan op "verbinding weg") en opent
  de server;
* de anderen verbinden met hetzelfde token met de opvolger (15 pogingen, eerst 2,5 s wachten); de oude sessie blijft
  op het scherm tot dat gelukt is, met de melding "Anna neemt de tafel over…".
De host-stoel-id is daardoor niet meer altijd `"host"` (`hostSeatId`). Getest in `HandoverTest`.

**Spellen in de buurt op het beginscherm**: `NearbyDiscovery` bestaat nu ook op Android (`BluetoothNearbyDiscovery`):
zolang het beginscherm zichtbaar is worden gekoppelde telefoons en (elke 3e ronde) gevonden telefoons gevraagd of er
een tafel is (`HELLO(QUERY)`); een nieuwe tafel verschijnt met een tikje. Alleen zolang de app open is (zoeken op de
achtergrond kost veel accu en mag op Android niet zonder voorgrond-service).

**Statistieken**: elke engine telt in zijn `log()`-helper per speler (`PlayerStats`: betrapt, terecht/onterecht
vals geroepen, laatste kaart geroepen/vergeten, stapels gepakt, verbrand, getrokken, slagen, punten, …).
`GameResult.stats` gaat mee naar het eindscherm (*Leuke feitjes*, deelplaatje) en naar `MatchPlayer`
(`rightCalls`, `wrongCalls`, `caught`) voor de ranglijst.

**Platformlaag**: `Platform` kreeg `haptics` (buzz/tik), `motion` (schudsterkte), `encodePng`/`encodeJpeg`,
`shareImage` (Android: FileProvider + deelmenu; iOS: `UIActivityViewController`) en `PlatformUi.rememberPhotoPicker`
(rechtop gezette foto, daarna bijsnijden in gedeelde code, `AvatarCropDialog`).

## 11. Tafels in de buurt melden (BLE), chat en emoji

**BLE-baken** (`app/.../nearby`): de host adverteert alleen in de LOBBY-fase manufacturer data onder company id
0xFFFF (`"BC"`, versie, spel, willekeurig tafel-id, naam tot ±19 bytes UTF-8; `TableBeaconFormat`), met
`ADVERTISE_MODE_LOW_POWER`, niet verbindbaar. Ontvangers registreren bij het starten van de app (en na
BOOT_COMPLETED/MY_PACKAGE_REPLACED, en als Bluetooth weer aangaat) één `BluetoothLeScanner.startScan(filters,
settings, PendingIntent)` met een filter op die manufacturer data en `SCAN_MODE_LOW_POWER`. Het filter draait in de
Bluetooth-controller; Android start `TableFoundReceiver` alleen bij een treffer. Per tafel-id hoogstens één melding
per 3 uur; geen melding als je zelf al aan een tafel zit. Getest op twee toestellen: melding binnen 5 s, app op de
achtergrond, scherm vergrendeld.

**Chat**: `NetMessage.Chat` (client → host → iedereen), de host nummert de regels en bewaart de laatste 100;
`ChatLimits`: 140 tekens, 5 per 10 s, één regel. **Emoji-reacties**: `SocialKind.REACTION` met een emoji uit
`EmojiCatalog` (± 270, de host weigert alles daarbuiten); welke je mag sturen bepalen `EmojiUnlocks`-tiers
(zie §12, bij 50 overwinningen alles).

## 12. Tafelscherm, gevarieerde unlocks en meedoen vanuit de melding

**Tafelscherm**: `NetMessage.Hello.role` is `PLAYER` of `TABLE`. De host behandelt een `TABLE`-verbinding als vaste
meekijker (`SeatInfo.table = true`): die krijgt de meekijk-view (alle open informatie, geen handkaarten), wordt nooit
naar een plek gepromoveerd en kan geen *Vals!* roepen. `SessionManager.joinAsTable` bepaalt de rol bij `join`, ook
bij opnieuw verbinden of het volgen van een nieuwe host. In de vier spelschermen vervangt `TableDisplayLayout`
(boven/midden/onder; het midden schaalt met `graphicsLayer` tot 2,6× afhankelijk van de schermgrootte) de gewone
indeling als `isTableDisplay()`; de beurtpil toont *"X is aan de beurt…"* in plaats van de meekijkbanner. Het scherm
blijft aan (lobby en spel), ongeacht de instelling.

**Unlocks**: `Progress.of(records, deviceId)` telt uit de ranglijst-potjes: gespeeld/gewonnen (totaal en per spel),
terecht *Vals!* geroepen en ongezien vals gespeeld. `Requirement` (Free, Wins, Played, GameWins, GamePlayed,
RightCalls, Ninja) heeft `current(p)`, `target` en `met(p)`; skins en emoji-tiers hebben elk een requirement en
`RequirementTexts.progress` maakt er *"3/10 · keer Kibbeling gewonnen"* van. Een eerder gekozen skin die (nog) niet
vrij is, valt terug op de standaard.

**Melding → meedoen**: de `contentIntent` van de BLE-melding draagt `join_host`/`join_game` mee. `MainActivity`
zet die (als je nog niet aan een tafel zit) in `AppContainer.pendingJoin`; de navigatie gaat naar het beginscherm,
waar `NearbyTablesPanel` zoekt en de eerste tafel van die host met dat spel automatisch joint (90 s time-out,
annuleerbaar). Na het verbinden wacht het menu op de eerste lobby-info en opent dan de lobby of het spel. *Terug*
uit een lobby die niet via het Bluetooth-scherm bereikt is, gaat naar het beginscherm.

## 13. Ronde 8 — één startknop, Vals!-venster, buzzer, Android Auto

**Beginscherm**: *Spel starten* opent direct de host-lobby (`HostLobbyRoute`); *Meedoen met een spel* opent het
meedoe-scherm (speler of 📺 tafel). `PlatformUi.rememberHostPreparer` vraagt op Android eerst de
Bluetooth-toestemming (één "apparaten in de buurt"-vraag) en gaat daarna altijd door. `SessionManager.startHosting`
faalt niet meer zonder radio: de tafel opent *offline* (alleen bots) en `reopenServer` volgt zodra Bluetooth terug
is of de toestemming er is. Zitten bij *Start spel* alleen bots aan tafel (geen REMOTE-stoelen, geen kijkers), dan
start `HostLobbyViewModel` een `ActiveSession.Local` (bewaard, hervatbaar, zonder server) en verdwijnt de lobby van
de stapel. `HostSession.setBotDifficulty` zet het niveau van alle bots. *Terug naar lobby* opent het juiste
lobby-scherm ook als dat niet onder het spel op de stapel lag (instappen vanaf het beginscherm of een melding).

**Vals!-venster**: `GameModule` kreeg `tick(state, nowMs)` en `nextTickAt(state)`; `GameHost` roept `tick` aan voor
en na elke actie en op het volgende tijdstip (met de host-klok; in tests telt de verstreken wachttijd). Zweeds
Pesten en Pesten houden `plays` bij (alle zetten van de laatste `CheatWindow.MS` = 10 s, met tijdstempel van de host;
`lastPlay` is de nieuwste). `Challenge(playId)` noemt een zet (null = nieuwste van een ander; de UI laat kiezen bij
meerdere). Meteen betrapt (`nextLogSeq == logSeqAfter`, alleen de nieuwste zet heeft nog een `before`-snapshot):
volledig terugdraaien zoals voorheen. Later betrapt: Zweeds Pesten — valsspeler pakt de stapel zoals die ligt
(aangevuld tot het aantal valse kaarten), verliest de beurt als die van hem is; Pesten — de strafkaarten. Een zet
die verloopt of waarvan de speler uit is, telt als ontsnapt (ninja). Bots wachten alleen extra direct na een zet.

**Buzzer**: `AndroidHaptics` trilt met `USAGE_NOTIFICATION_EVENT` (Android negeert gewone trillingen van apps die
niet op het scherm staan); `BuzzAlerts` toont bij scherm uit of app op de achtergrond een melding op kanaal
`buzz` (hoog, trilpatroon, `sfx_buzz`).

**Aflegstapel**: de open kaart heeft een vaste plek (`FAN_SLOTS`), de oudere kaarten waaieren links ervan uit en de
stapeldikte groeit onder de kaarten in plaats van erboven.

**Android Auto** (`app/src/debug`): `TableCarAppService` + `TableCarScreen` (Car App Library 1.4, `ListTemplate`/
`MessageTemplate`, max. 6 rijen, verversen hoogstens 1×/s). Gegevens uit `ViewSummary.cardsLeft` en de lobby.
Alleen in debug-builds en met `ALLOW_ALL_HOSTS_VALIDATOR`, omdat Android Auto geen categorie voor kaartspellen heeft
(de service gebruikt `category.IOT` om te kunnen draaien).

## 14. Tafels vinden: aankondiging met adres (en de weg naar iOS)

**Waarom het traag was**: een klassieke Bluetooth-zoekactie (inquiry + namen) duurt 12–20 s en de app wachtte die
af voordat hij de gevonden apparaten één voor één controleerde (RFCOMM + HELLO QUERY, tot 9 s per apparaat). Het
beginscherm zocht maar elke derde ronde. En een niet-gekoppelde host werd alleen gevonden als hij *zichtbaar* was.

**Nu**:
* Een app kan zijn eigen Bluetooth-adres niet uitlezen (Android geeft 02:00:00:00:00:00). Daarom stuurt elke
  telefoon in `Hello.hostAddress` het adres mee waarmee hij de host bereikte; `HostSession.ownAddress` geeft het
  door, de app bewaart het (`NearbyTableAlerts.ownAddress`).
* De BLE-aankondiging (`TableBeaconFormat` versie 2: vlaggen *spel bezig* / *adres*, 6 bytes adres, naam ±12 bytes)
  loopt zolang de telefoon host is, op `ADVERTISE_MODE_BALANCED`. Meldingen alleen voor een open lobby.
* Zoekers scannen actief (`TableBeaconScanner`, `SCAN_MODE_LOW_LATENCY`) en geven de aankondigingen 3 s voorsprong
  voordat een zoekactie de radio bezet. Een aankondiging met adres wordt meteen gecontroleerd (beginscherm: meteen
  getoond); zonder adres start juist een zoekactie.
* `searchUntilNewPhone`: de zoekactie stopt bij elke nieuwe telefoon, die direct gecontroleerd wordt, en zoekt dan
  verder. Controles gaan één voor één (`probeLock`).
* Zichtbaar maken wordt niet meer automatisch gevraagd zodra het eigen adres bekend is (📡 blijft beschikbaar).

**iOS**: iPhones kunnen geen klassieke Bluetooth (RFCOMM) gebruiken (alleen MFi-accessoires), dus Android ↔ iPhone
kan nooit over de huidige verbinding; iPhone ↔ iPhone loopt via Multipeer. De gemeenschappelijke weg is **BLE**:
beide kennen L2CAP-kanalen (Android 10+, iOS 11+) — een gewone datastroom waar ons regelprotocol ongewijzigd overheen
kan. Een iPhone-host kan in zijn aankondiging alleen een service-UUID en naam zetten (geen manufacturer data), dus
het gezamenlijke profiel wordt: service-UUID in de aankondiging, tafelinfo + L2CAP-PSM in een GATT-kenmerk, daarna
het L2CAP-kanaal. Android-hosts houden daarnaast de snelle manufacturer data. Het zoekpad van nu (BLE zien → direct
verbinden) blijft daarbij hetzelfde; alleen de verbinding (RFCOMM → L2CAP) en een CoreBluetooth-transport voor iOS
komen erbij. Bijkomend voordeel: geen zichtbaar maken en geen klassieke zoekactie meer, ook niet de eerste keer.

