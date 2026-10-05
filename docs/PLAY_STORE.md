# BlueCard publiceren in de Google Play Store

Stappen in volgorde. ✅ = al geregeld in het project, 👤 = moet jij doen (in de Play Console of op je Mac).

## 1. Uploadsleutel (👤, eenmalig)

Play accepteert geen build met de debug-sleutel. Maak een eigen uploadsleutel en **bewaar het bestand en de
wachtwoorden goed** (kwijt = Google-support nodig om hem te vervangen):

```bash
keytool -genkeypair -v -keystore ~/keys/bluecard-upload.jks -alias bluecard -keyalg RSA -keysize 2048 -validity 10000
```

Zet daarna in `~/.gradle/gradle.properties` (niet in het project, niet in git):

```properties
bluecard.upload.storeFile=/Users/<jij>/keys/bluecard-upload.jks
bluecard.upload.storePassword=...
bluecard.upload.keyAlias=bluecard
bluecard.upload.keyPassword=...
```

`./package.sh` maakt dan `dist/BlueCard-release.aab`, ondertekend met deze sleutel ✅. Kies in de Play Console
voor **Play App Signing** (standaard): Google beheert de echte app-sleutel, jij uploadt met deze sleutel.

## 2. App aanmaken in de Play Console (👤)

* App-naam: **BlueCard – Pesten & Zweeds Pesten** (max. 30 tekens)
* Standaardtaal: Nederlands · Type: Game · Gratis
* Pakketnaam (vast na de eerste upload): `nl.bluecard.app` (nog vrij in de Play Store)
* Versie: `versionCode 1`, `versionName 1.0.0` ✅ — verhoog `versionCode` bij elke nieuwe upload.

## 3. Testen vóór productie (👤)

Nieuwe **persoonlijke** ontwikkelaarsaccounts (aangemaakt na november 2023) moeten eerst een **gesloten test met
minstens 12 testers die 14 dagen meedoen** draaien, pas daarna mag je productietoegang aanvragen.
Organisatie-accounts hebben die eis niet. Upload de `.aab` daarom eerst naar *Testen → Gesloten testen*.

## 4. Store-vermelding (👤 invullen, teksten hieronder)

**Korte beschrijving** (max. 80):

> Pesten en Zweeds Pesten tegen bots of samen via Bluetooth – zonder internet.

**Volledige beschrijving:**

> Speel de twee bekendste Nederlandse kaartspellen op je telefoon: gewoon **Pesten** en **Zweeds Pesten**.
>
> 🃏 Tegen slimme bots, of met vrienden aan tafel via **Bluetooth** (2 tot 6 spelers), zonder internet, zonder
> account en zonder server.
>
> • Pesten: kleur of waarde volgen, pak 2, joker pak 5, stapelen, boer kiest de kleur, en vergeet niet
>   "Laatste kaart!" te roepen…
> • Zweeds Pesten: hand-, open en blinde kaarten, 2 reset, 7 of lager, 10 verbrandt de stapel.
> • Speelt als echte kaarten: veeg een kaart naar de stapel, trek de stapel naar je toe.
> • Net als aan tafel mag je vals spelen – maar word je betrapt met "Vals!", dan krijg je straf.
> • Alle huisregels instelbaar, met voorinstellingen (o.a. Klassiek, TIS-modus, Extra pesten).
> • Je spel tegen de bots wordt bewaard; stop wanneer je wilt.
>
> Het spel werkt volledig offline. Alleen op het start- en eindscherm staat een advertentie.

**Vertalingen:** de app is er in het Nederlands, Engels, Frans, Duits, Catalaans en Baskisch. Voeg in de Play Console
(*Store-vermelding → Vertalingen beheren*) dezelfde talen toe; de Engelse tekst kan als basis dienen
("Crazy Eights and Palace against bots or together over Bluetooth – no internet needed.").

**Afbeeldingen:** app-icoon 512×512 PNG, feature graphic 1024×500, minstens 2 telefoon-screenshots
(bijv. Pesten-tafel, Zweeds Pesten-tafel, bot-setup, eindscherm).

## 5. App-content (👤, antwoorden)

* **Privacybeleid-URL**: verplicht (advertenties). Staat op de website, zie onderaan ("Website en privacybeleid").
* **Advertenties**: *Ja, mijn app bevat advertenties*.
* **App-toegang**: alle functies zonder inloggen.
* **Doelgroep**: 13+ (kies géén leeftijden onder 13; anders gelden de Families-regels voor advertenties).
* **Contentclassificatie**: vragenlijst; geen geweld/gokken met echt geld → laag (PEGI 3/IARC).
  "Gokken van de trekstapel" is een spelregel, geen gokken met geld.
* **Advertentie-ID**: *Ja*, gebruikt door de advertentie-SDK (doel: advertenties, analyses door AdMob).
* **Datatypes (Data safety)**:
  * Verzameld door de AdMob-SDK: *Apparaat- of andere ID's* (advertentie-ID), *App-activiteit/diagnostiek*
    zoals AdMob die opgeeft — gedeeld met Google voor advertenties; versleuteld onderweg; niet verplicht te
    verwijderen via de app (toestemming intrekken kan via *Privacy-instellingen advertenties*).
  * De app zelf verzamelt niets: naam en spellen blijven op het toestel; Bluetooth-verkeer gaat alleen naar
    telefoons in de buurt.
  * Zie ook Google's handleiding "Google Mobile Ads SDK – Data disclosure".
* **Foreground service (connectedDevice)**: verplicht te verklaren. Uitleg:
  > Tijdens een Bluetooth-spel houdt een foreground service de verbinding met de andere telefoons open,
  > ook als de speler de app even naar de achtergrond stuurt. De service stopt zodra het spel eindigt.
  Google vraagt soms een korte video: start een Bluetooth-spel, druk op de thuisknop, laat de melding
  "Bluetooth-spel actief" zien en ga terug naar het spel.
* **Bluetooth-rechten**: `BLUETOOTH_SCAN` met `neverForLocation`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` ✅
  (geen locatie op Android 12+).

## 6. AdMob koppelen (👤, na publicatie)

* In AdMob: app koppelen aan de Play Store-vermelding (*Apps → App-instellingen → App-store-details*).
* `app-ads.txt` op de website van je ontwikkelaarsaccount zetten (AdMob geeft de regel).
* GDPR-bericht in *Privacy en berichten* publiceren (anders verschijnt er geen toestemmingsvraag in de EU).
* Testtoestellen staan in `AdsManager.TEST_DEVICES` ✅ — die krijgen altijd testadvertenties.

## 7. In-app aankopen (👤, na de eerste upload)

De app gebruikt Google Play Billing 9.1 ✅. Producten kun je pas aanmaken als er een build met de
billing-bibliotheek in de Play Console staat; **interne test** is genoeg, publiceren is niet nodig.

1. *Instellingen → Betalingsprofiel*: koppel een betalingsprofiel (verkopersaccount) aan je ontwikkelaarsaccount.
2. Upload de `.aab` naar *Testen → Interne test*.
3. *Inkomsten genereren met Play → Producten → In-app-producten → Product maken*, met **precies** deze ids
   (eenmalige producten). De prijzen zijn voorstellen; de app toont wat je hier invult.

   | Product-ID | Naam | Voorstel | Soort in de app |
   |---|---|---|---|
   | `no_ads` | Geen advertenties | € 2,99 | blijvend |
   | `bundle_all` | Alles-in-één (geen advertenties + alle premium skins) | zie hieronder | blijvend |
   | `skin_diamond_holo` | Kaartrug Diamant Holo | € 99,00 | blijvend |
   | `tip_small` | Kleine fooi | € 0,99 | fooi (opnieuw te kopen) |
   | `tip_medium` | Fooi | € 2,99 | fooi |
   | `tip_large` | Grote fooi | € 4,99 | fooi |

   Let op: `bundle_all` bevat ook Diamant Holo. Is die € 99,00, dan is een goedkope bundel de voordeligste route
   naar Holo; prijs de bundel daarom hoger dan Holo, of neem Holo er niet in op (zeg het, dan pas ik dat aan).
4. Zet elk product op *Actief*.
5. Testen zonder echt geld: *Instellingen → Licentietests* → voeg je Google-account toe. Installeer de app via
   de link van de interne test (niet via adb: dan kent Play de app niet en blijft de winkel leeg).

Fooien worden na aankoop "verbruikt" (opnieuw te geven) en zetten een 💚 achter je naam; de rest blijft van je
en komt na herinstallatie terug (*Aankopen herstellen* in de winkel). Controle gebeurt op het toestel (geen server).

## 8. Uploaden (👤)

`./package.sh` → upload `dist/BlueCard-release.aab` en `dist/BlueCard-mapping.txt` (voor leesbare crashrapporten).

---

## Website en privacybeleid

Het privacybeleid (Engels) staat op de website in de aparte repository `bluecard-site`
(`/Users/ivo/Documents/Projects/bluecard-site`): `privacy/index.html`, met landingspagina's in het Engels en
Nederlands en `app-ads.txt` voor AdMob. Publiceren via GitHub Pages; zie de README daar. Vul vóór publicatie
`[DEVELOPER NAME]` en `[CONTACT EMAIL]` in.
