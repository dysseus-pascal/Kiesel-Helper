# Kiesel-Helper

Android-App, die entgegennimmt, was Pebble-Uhren melden, und es in die
Gesundheitsakte des Telefons (Health Connect) einträgt.

Was eingetragen wird, steht **nicht in dieser App**. Es steht in
Beschreibungen, die man aus GitHub-Repos lädt — eine je Uhr-App. Der Helper
kann das Eintragen; er weiss nur nicht, wofür, bis ihm jemand einen Zettel
gibt.

*Kiesel, weil Pebble.*

## Warum

Bisher brauchte jede Uhr-App ihre eigene Companion-App: derselbe
Broadcast-Empfänger, derselbe Vordergrunddienst, dieselben vier Fallstricke —
jedes Mal neu, jedes Mal von Hand aufs Telefon. Der Teil, der sich tatsächlich
unterscheidet, ist winzig: *diese UUID, dieses Feld, diese Satzart.*

Genau dieser Teil ist hier herausgezogen.

## Was eine Beschreibung ist — und was nicht

Eine Beschreibung ist eine JSON-Datei. **Kein Programmcode.** Sie sagt, welche
Uhr-App gemeint ist, welche Felder deren Nachricht enthält und was daraus in
der Akte werden soll. Mehr kann sie nicht sagen.

Nachladbarer Code wäre der naheliegende Gedanke und wurde verworfen — nicht aus
Vorsicht, sondern weil er den Engpass gar nicht löst:

* **Berechtigungen lassen sich nicht nachreichen.** Was eine App in die
  Gesundheitsakte schreiben darf, steht in ihrem Manifest und wird beim
  Installieren festgeschrieben. Der Erlaubnis-Dialog zeigt nur, was angefragt
  **und** angemeldet ist. Geladener Code könnte diese Liste nicht erweitern —
  er stünde vor derselben Mauer wie ein Zettel.
* **Android 14 verlangt, dass nachgeladener Code schreibgeschützt liegt**
  (»Safer Dynamic Code Loading«), Android 17 dehnt das auf native
  Bibliotheken aus. Der Weg wird enger, nicht breiter.
* **Play Protect meldet nachgeladenen Code**, auch bei einer App, die nie im
  Play Store war.

Bleibt also der Zettel. Und ein Zettel hat einen Vorzug, den Code nie hätte:
man kann ihn lesen. Er ist zwanzig Zeilen lang, steht in einem öffentlichen
Repo, und was er anordnen kann, ist durch den Katalog unten begrenzt.

## Der Katalog

Die Satzarten, die diese Fassung eintragen kann. Eine Beschreibung darf hieraus
**wählen** — hinzufügen kann sie nichts.

| `art` | was | Einheit | Angaben | Berechtigung |
|---|---|---|---|---|
| `hrv_rmssd` | Herzratenvariabilität (RMSSD) | ms | `wert` + `zeitpunkt` | WRITE_HEART_RATE_VARIABILITY |
| `herzfrequenz` | Herzfrequenz | bpm | `wert` + `zeitpunkt` | WRITE_HEART_RATE |
| `hydration` | getrunkenes Wasser | ml | `menge` + `beginn` + `dauer_s` | WRITE_HYDRATION |
| `koffein` | Koffein | mg | `menge` + `beginn` + `dauer_s` | WRITE_NUTRITION |
| `schritte` | Schritte | Schritte | `menge` + `beginn` + `dauer_s` | WRITE_STEPS |
| `schlaf` | Schlaf | — | `beginn` + `dauer_s` | WRITE_SLEEP |

Aufgenommen ist, was eine Pebble-Uhr wirklich hergibt. Nicht aufgenommen ist,
wofür eine Uhr ein schlechtes Eingabegerät wäre — Gewicht, Blutdruck,
Körpertemperatur tippt man am Telefon, nicht mit drei Tasten.

**Eine Art zu ergänzen kostet eine neue Fassung der App:** eine Zeile in
`Katalog.kt`, eine `<uses-permission>`-Zeile im Manifest, und das neue APK aufs
Telefon. Alles andere kostet nur einen Zettel. Diese Grenze ist von Android
gesetzt, nicht von mir.

**Gelesen wird nichts.** Kiesel-Helper trägt nur ein. Eine Leseerlaubnis wäre
eine ungleich grössere Offenlegung und wird von nichts gebraucht, was diese App
tut.

## Bekannte Beschreibungen

* [Kiesel-Helper-Drinktervall](https://github.com/dysseus-pascal/Kiesel-Helper-Drinktervall)
  — getrunkenes Wasser
* [Kiesel-Helper-Herzintervall](https://github.com/dysseus-pascal/Kiesel-Helper-Herzintervall)
  — Herzratenvariabilität

## Beschreibung laden

1. App öffnen.
2. Die Adresse des Repos einsetzen — die, die im Browser oben steht, etwa
   `https://github.com/dysseus-pascal/Kiesel-Helper-Drinktervall`. Die App
   macht daraus selbst die Adresse der `kiesel.json`. Eine fertige Adresse
   direkt auf eine `.json` geht auch.
3. **Laden.** Die Datei wird sofort eingelesen; bei Fehlern wird nichts
   abgelegt und die Meldung sagt, was fehlt. Danach fragt die App die
   Erlaubnisse ab, die genau diese Beschreibung braucht.

**Nichts wird von selbst geholt.** Aktualisiert wird nur, wenn jemand
*Erneuern* drückt. Eine stille Änderung an dem, was in eine Gesundheitsakte
schreibt, wäre genau das, was man nicht will — und ein Netzzugriff im
Hintergrund wäre ausserdem ein Fernsteuerungskanal, den niemand bestellt hat.

## Das Format

Das hier ist die vollständige, echte Beschreibung für Drinktervall — mehr
braucht es nicht:

```json
{
  "format": 1,
  "name": "Drinktervall",
  "uuid": "5b0f7a3e-2c8d-4b61-9e4f-7d2a1c9b8e50",
  "quelle": "https://github.com/dysseus-pascal/Drinktervall",
  "beschreibung": "Traegt jedes getrunkene Glas als Wassermenge in die Gesundheitsakte ein.",

  "schluessel": {
    "GLASS_ML": 10008,
    "DRANK_AT": 10009
  },

  "regeln": [
    {
      "wenn": ["DRANK_AT", "GLASS_ML"],
      "nicht_zweimal_fuer": "DRANK_AT",
      "eintrag": {
        "art": "hydration",
        "menge": { "aus": "GLASS_ML", "einheit": "ml" },
        "beginn": { "aus": "DRANK_AT", "einheit": "s" },
        "dauer_s": 60
      },
      "meldung": "{GLASS_ML} ml eingetragen"
    }
  ]
}
```

| Feld | Bedeutung |
|---|---|
| `format` | Muss `1` sein. Eine Fassung, die die Zahl nicht kennt, lehnt die Datei ab, statt zu raten. |
| `uuid` | Die UUID der Uhr-App. Daran wird die Nachricht erkannt. |
| `schluessel` | Feldname → Nummer, wie das Feld in der Nachricht ankommt. Die Nummern ergeben sich aus der Reihenfolge der `messageKeys` in der `package.json` der Uhr-App, beginnend bei 10000. |
| `regeln[].wenn` | Diese Felder müssen in der Nachricht stehen, damit die Regel greift. Ohne diese Bedingung trüge eine blosse Standmeldung der Uhr jedes Mal einen weiteren Eintrag ein. |
| `regeln[].nicht_zweimal_fuer` | Zweimal derselbe Wert in diesem Feld heisst: dieselbe Messung. Meist ein Zeitstempel. Schützt vor erneut zugestellten Nachrichten. |
| `regeln[].meldung` | Text für die Statusanzeige. `{FELD}` wird durch den Wert ersetzt. |
| `regeln[].eintrag.art` | Eine `art` aus dem Katalog oben. |
| `… .wert` / `.menge` | `{ "aus": FELD, "einheit": … }`. Die Einheit muss die des Katalogs sein — ein `g` statt `mg` verschöbe jeden Wert um das Tausendfache, und niemand sähe es dem Eintrag an. |
| `… .zeitpunkt` / `.beginn` | `{ "aus": FELD, "einheit": "s" }` oder `"ms"`. |
| `… .dauer_s` | Länge der Spanne in Sekunden. Muss grösser als null sein — die Akte lehnt eine Spanne der Länge null ab. |

Eingelesen wird nach dem Alles-oder-nichts-Grundsatz: **entweder ein Modul oder
eine Liste von Fehlern**, nie ein halbes. Eine Beschreibung, die nur teilweise
verstanden wurde, schriebe teilweise falsche Werte in eine Gesundheitsakte —
und das fällt niemandem auf.

Geprüft wird beim Laden: Format, UUID, Schlüsselnummern, die Pflichtangaben der
gewählten Satzart, die Einheit, und dass jedes genannte Feld auch deklariert
ist.

## Was die App sonst noch tut

* **Empfangen.** Die Pebble-App verschickt eingehende AppMessages als
  *impliziten* Broadcast — und den bekommt ab Android 8 nur ein zur Laufzeit
  angemeldeter Empfänger. Deshalb läuft ein Vordergrunddienst mit einer stillen
  Meldung in der Leiste; er ist der Preis dafür, dass Messungen auch dann
  ankommen, wenn die App nicht offen ist.
* **Bestätigen.** Quittiert wird jede Nachricht einer bekannten UUID, und
  *zuerst*, dann wird geschrieben — sonst läuft die Uhr in ihren Zeitablauf,
  sobald die Akte einmal langsam ist. Für eine **unbekannte** UUID wird
  ausdrücklich **nicht** bestätigt: die Nachricht gilt einer fremden Watchapp,
  und ein ACK von hier behauptete, wir hätten sie verarbeitet.
* **Neustart überstehen.** Nach einem Neustart des Telefons fährt der Dienst
  wieder hoch, und wenn Android 15 ihn nach sechs Stunden auf die Zeitgrenze
  schickt, startet er sich selbst neu.

## Bauen

Auf dem Entwicklungsrechner steht kein Android-SDK. Gebaut wird deshalb bei
GitHub Actions; jeder Push erzeugt ein Debug-APK als Artefakt des Laufs
([Bauen](../../actions/workflows/bauen.yml) → letzter Lauf → *kiesel-helper-debug*).

Der Lauf belegt, dass die App übersetzt und ein Paket ergibt. Dass sie am
Telefon das Richtige **tut**, belegt er nicht — das zeigt sich erst dort.

```bash
adb install -r kiesel-helper-debug.apk
```

Wer lokal baut, braucht JDK 17, ein Android-SDK mit API 36 und Gradle 9.5.1
(9.6 hat eine interne Schnittstelle entfernt, auf die das Android-Plugin 8.x
noch baut):

```bash
gradle assembleDebug
```

## Grenzen

* Die App prüft eine Beschreibung auf **Form**, nicht auf **Sinn**. Wer
  `hydration` mit einer Zahl aus dem falschen Feld füttert, bekommt saubere
  Einträge mit falschen Werten. Deshalb prüfen die Beschreibungs-Repos ihre
  Schlüsselnummern wöchentlich gegen die Uhr-App gegen.
* Die Schlüsselnummern hängen an der Reihenfolge der `messageKeys` in einem
  **anderen** Repo. Wer dort eine Zeile dazwischen einfügt, verschiebt alle
  folgenden Nummern.
* Health Connect gibt es ab Android 14 im System; davor braucht es die App aus
  dem Play Store. Ohne sie zeigt Kiesel-Helper das an und tut sonst nichts.

## Lizenz

[CC0 1.0](LICENSE) — gemeinfrei.
