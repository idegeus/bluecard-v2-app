# BlueCard op iPhone (iOS)

De iOS-app gebruikt **dezelfde code** als de Android-app: alle schermen, de vier spellen, bots, animaties, skins,
talen en de opslag staan in de gedeelde module `shared/` (Kotlin Multiplatform + Compose Multiplatform). Alleen een
dunne iOS-laag is apart (`shared/src/iosMain`): bestanden, geluid, de fotokiezer, de taalkeuze en samen spelen via
Multipeer Connectivity. Het Xcode-project staat in `iosApp/`.

## Eerste keer bouwen

1. **Xcode installeren** (gratis, App Store). Op deze Mac stond geen Xcode, daarom is de iOS-app hier wel
   gecompileerd maar nog niet gelinkt of in een simulator gestart (zie "Wat is getest" hieronder).
   Open Xcode één keer en laat het de extra componenten installeren, en zet daarna de command line tools goed:

   ```bash
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
   ```

2. **JDK 17** moet aanwezig zijn (staat er al: Temurin 17). De build-stap in Xcode zoekt hem zelf op.

3. Open **`iosApp/iosApp.xcodeproj`** in Xcode.

4. Kies bij het target *iosApp* → **Signing & Capabilities** je eigen team (een gratis Apple-ID werkt om op je
   eigen iPhone te testen). Je kunt het team ook vast invullen in `iosApp/Configuration/Config.xcconfig`
   (`TEAM_ID=`). Wijzig zo nodig de bundle-id (`BUNDLE_ID`, nu `nl.bluecard.app`).

5. Kies een simulator of je aangesloten iPhone en druk op **Run** (⌘R).
   De eerste build duurt een paar minuten: de build-fase *Compile Kotlin Framework* draait
   `./gradlew :shared:embedAndSignAppleFrameworkForXcode` en compileert alle Kotlin-code naar een iOS-framework.
   Daarna gaat het veel sneller.

   Op een echte iPhone moet je de eerste keer de ontwikkelaar vertrouwen: *Instellingen → Algemeen → VPN- en
   apparaatbeheer*.

## Samen spelen op iPhone

iOS staat apps geen klassiek Bluetooth (RFCOMM) toe zoals Android. Daarom gebruikt de iPhone-versie
**Multipeer Connectivity** (Bluetooth en wifi, geen internet nodig). Het menu heet daar *Samen spelen*.

* Eén iPhone kiest *Spel aanmaken*; de tafel wordt automatisch aangekondigd zolang de lobby open is.
* De anderen kiezen *Meedoen*; tafels in de buurt verschijnen vanzelf (naam van de host en het spel).
* iOS vraagt de eerste keer toestemming voor **lokaal netwerk**: kies *Sta toe*, anders vinden de telefoons
  elkaar niet.
* Het spelprotocol is precies hetzelfde als op Android (`multiplayer/`), dus alles werkt mee: lobby, volgorde,
  spel wisselen, herverbinden, ranglijst-synchronisatie, bot-overname.
* **iPhone en Android samen aan één tafel kan niet**: Android gebruikt Bluetooth RFCOMM, iOS Multipeer.

## Verschillen met Android

| | Android | iOS |
|---|---|---|
| Samen spelen | Bluetooth RFCOMM (met koppelen/zichtbaar maken) | Multipeer Connectivity (automatisch) |
| Advertenties | AdMob op start- en eindscherm | geen |
| Spel op de achtergrond | foreground service + beurtmelding | iOS pauzeert de app; het spel gaat verder als je terugkomt |
| Taal | *Instellingen → Taal* of systeem | idem (ook per app in de iOS-instellingen) |
| Terugknop | systeem-terugknop | de pijl/✕ in het scherm |
| Geluid | uit bij stil/trillen | uit bij de stil-schakelaar |
| Buzzer/schudden | trilmotor + versnellingsmeter | haptiek (`UIImpactFeedbackGenerator`) + CoreMotion |
| Delen ("ik heb gewonnen") | deelmenu via FileProvider | `UIActivityViewController` (opslaan in Foto's vraagt toestemming) |
| Spellen in de buurt op het beginscherm | Bluetooth-zoeken + navragen | Multipeer-browser (automatisch) |

## Wat is getest

* Alle gedeelde code (UI, sessies, opslag, spellen) is **dezelfde** als op Android en daar op twee telefoons getest,
  inclusief kleine schermen (320×568 pt, het formaat van de kleinste iPhone SE).
* De iOS-specifieke code is **gecompileerd tegen de echte iOS-API's** (`./gradlew :shared:compileKotlinIosArm64
  :shared:compileKotlinIosSimulatorArm64`, zonder fouten).
* **Niet getest** (geen Xcode/iPhone beschikbaar op deze Mac): het linken van het framework, de app in de
  simulator, Multipeer tussen twee iPhones, de fotokiezer en geluid op iOS, en uit ronde 5/6: haptiek, schudden met
  CoreMotion, het deelmenu, het overnemen van de host-rol en de Catalaanse/Baskische lokalisatie in iOS zelf. Mocht de eerste build in Xcode iets
  melden, dan staat de oorzaak in het build-log van de fase *Compile Kotlin Framework*.

## Bestanden

```
iosApp/iosApp.xcodeproj         Xcode-project (target iosApp)
iosApp/Configuration/Config.xcconfig   team, bundle-id, naam
iosApp/iosApp/iOSApp.swift      SwiftUI-app die de Compose-UI toont (MainViewController)
iosApp/iosApp/Info.plist        talen (en, nl, fr, de, ca, eu), lokaal netwerk/Bonjour (_bluecard._tcp/_udp), foto's bewaren, status bar
iosApp/iosApp/Assets.xcassets   app-icoon (1024×1024), kleuren
iosApp/iosApp/Sounds/           geluidseffecten (dezelfde als Android)
shared/src/iosMain/             IosPlatform, IosDevice (haptiek, beweging, delen), MultipeerTransport, IosPlatformUi
```
