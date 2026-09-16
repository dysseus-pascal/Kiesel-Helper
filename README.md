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

| `art` | was | Einheit | Angaben |
|---|---|---|---|
| `hrv_rmssd` | Herzratenvariabilität | ms | `wert` + `zeitpunkt` |
| `herzfrequenz` | Herzfrequenz | bpm | `wert` + `zeitpunkt` |
| `ruhepuls` | Ruhepuls | bpm | `wert` + `zeitpunkt` |
| `sauerstoff` | Sauerstoffsättigung | % | `wert` + `zeitpunkt` |
| `atemfrequenz` | Atemfrequenz | 1/min | `wert` + `zeitpunkt` |
| `gewicht` | Gewicht | g | `wert` + `zeitpunkt` |
| `koerperfett` | Körperfettanteil | % | `wert` + `zeitpunkt` |
| `groesse` | Körpergrösse | mm | `wert` + `zeitpunkt` |
| `temperatur` | Körpertemperatur | m°C | `wert` + `zeitpunkt` |
| `blutzucker` | Blutzucker | mg/dl | `wert` + `zeitpunkt` |
| `grundumsatz` | Grundumsatz | kcal/d | `wert` + `zeitpunkt` |
| `vo2max` | VO2max | ml/kg/min | `wert` + `zeitpunkt` |
| `schritte` | Schritte | Schritte | `menge` + `beginn` + `dauer_s` |
| `strecke` | Zurückgelegte Strecke | m | `menge` + `beginn` + `dauer_s` |
| `hoehenmeter` | Höhenmeter | m | `menge` + `beginn` + `dauer_s` |
| `stockwerke` | Stockwerke | Stockwerke | `menge` + `beginn` + `dauer_s` |
| `rollstuhl` | Rollstuhlstösse | Stösse | `menge` + `beginn` + `dauer_s` |
| `aktive_kalorien` | Aktive Kalorien | kcal | `menge` + `beginn` + `dauer_s` |
| `gesamt_kalorien` | Gesamtkalorien | kcal | `menge` + `beginn` + `dauer_s` |
| `hydration` | Getrunkenes Wasser | ml | `menge` + `beginn` + `dauer_s` |
| `koffein` | Koffein | mg | `menge` + `beginn` + `dauer_s` |
| `schlaf` | Schlaf | — | `beginn` + `dauer_s` |

**Die Einheiten sind durchweg ganzzahlig gewählt** — Gramm statt Kilogramm,
Millimeter statt Meter, Milligrad statt Grad. Ein Zettel trägt ganze Zahlen;
wer Gewicht in `kg` verlangte, könnte 72,4 kg nicht ausdrücken.

Nicht aufgenommen sind: alles zum Zyklus (schon der Eintrag im Manifest wäre
eine Aussage); Blutdruck (die Akte will zwei Werte in einem Satz, eine Regel
liefert einen); und Reihenwerte wie Leistung (dort will die Akte eine Folge von
Proben).

**Eine Art zu ergänzen kostet eine neue Fassung der App:** eine Zeile in
`Katalog.kt`, eine `<uses-permission>`-Zeile im Manifest, und das neue APK aufs
Telefon. Alles andere kostet nur einen Zettel. Diese Grenze ist von Android
gesetzt, nicht von mir.

**Gelesen wird nichts.** Kiesel-Helper trägt nur ein. Eine Leseerlaubnis wäre
eine ungleich grössere Offenlegung und wird von nichts gebraucht, was diese App
tut.

## Quellen und Senken

Der Katalog oben sagt, **was** eingetragen werden kann. Daneben steht die
zweite Wahl, die ein Zettel trifft: **woher** die Werte kommen und **wohin**
sie gehen.

**Quellen**

| `quelle.art` | Felder | braucht |
|---|---|---|
| `appmessage` | die Namen aus `schluessel` | nichts — die Pebble-App sendet von selbst |
| `benachrichtigung` | `titel`, `text`, `untertext`, `grosstext`, `zusatz`, `ticker`, `paket`, `wann`, `dauerhaft`, dazu jedes `extra:<name>` | einmalige Freigabe in den Systemeinstellungen |

Zusätzlich liefert **jede** Quelle das Feld `jetzt` — den Augenblick des
Empfangs in Sekunden. Gedacht für Quellen ohne eigenen Zeitstempel. Es ist der
schlechtere Zeitpunkt, aber manchmal der einzige.

**Senken** — eine Regel wählt genau eine:

| Feld | was geschieht |
|---|---|
| `eintrag` | ein Satz in der Gesundheitsakte (Katalog oben) |
| `senden` | eine AppMessage an eine Uhr-App, auf Wunsch mit `"starten": true` |
| `melden` | eine Benachrichtigung auf dem Telefon |

Damit ist der Weg **in beide Richtungen** offen: Uhr → Telefon war schon da,
Telefon → Uhr ist dazugekommen. Eine Navigationsanweisung von OsmAnd auf der
Uhr ist ab hier ein Zettel und keine neue Fassung der App.

**Was `melden` gut kann:** herausfinden, was in den Benachrichtigungen einer
App überhaupt steht. Siehe [beispiele/felder-anzeigen.json](beispiele/felder-anzeigen.json)
— der erste Zettel, den man für eine neue App schreibt. Ohne ihn rät man, wo
die Angabe steckt.

**Die Freigabe für Benachrichtigungen** erteilt man einmal in *Einstellungen →
Benachrichtigungen → Benachrichtigungszugriff*. Es gibt dafür keinen Dialog und
keine Abfrage zur Laufzeit; eine App kann nur hinführen, und genau das tut der
Knopf auf der Karte. Ohne Zettel für ein Paket wird jede Benachrichtigung
sofort wieder verworfen, ohne gelesen zu werden.

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

Es gibt zwei Formatnummern, und **beide bleiben gültig**. Fassung 1 ist der
kurze Fall: Quelle ist immer eine Uhr-App, Senke immer die Gesundheitsakte.
Das hier ist die vollständige, echte Beschreibung für Drinktervall — mehr
braucht es nicht:

```json
{
  "format": 1,
  "name": "Drinktervall",
  "uuid": "5b0f7a3e-2c8d-4b61-9e4f-7d2a1c9b8e50",
  "quelle": "https://github.com/dysseus-pascal/Drinktervall",
  "beschreibung": "Trägt jedes getrunkene Glas als Wassermenge in die Gesundheitsakte ein.",

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

### Fassung 2

Fassung 2 ändert an alldem nichts, sie ergänzt. Neu sind `quelle` als Objekt
und die beiden anderen Senken:

```json
{
  "format": 2,
  "name": "OsmAnd-Navigation",
  "quelle": { "art": "benachrichtigung", "paket": "net.osmand.plus" },
  "schluessel": { "ANWEISUNG": 10000, "ENTFERNUNG": 10001 },
  "regeln": [
    {
      "wenn": ["titel", "text"],
      "nur_wenn": { "titel": "[0-9]" },
      "nicht_zweimal_fuer": "text",
      "senden": {
        "an": "00000000-0000-0000-0000-000000000000",
        "starten": true,
        "felder": {
          "ANWEISUNG":  { "aus": "text",  "art": "text" },
          "ENTFERNUNG": { "aus": "titel", "art": "zahl", "muster": "([0-9.,]+)" }
        }
      },
      "meldung": "{text} in {titel}"
    }
  ]
}
```

| Feld | Bedeutung |
|---|---|
| `quelle` | `{ "art": "appmessage", "uuid": … }` oder `{ "art": "benachrichtigung", "paket": … }`. Fehlt es, gilt das `uuid` ganz oben und damit Fassung 1. |
| `nur_wenn` | Feld → regulärer Ausdruck, der passen muss. Das ist der Unterschied zwischen »Maps hat eine Benachrichtigung« und »Maps navigiert«. |
| `senden.an` | UUID der Uhr-App. |
| `senden.starten` | Eine AppMessage erreicht nur die **laufende** Uhr-App. Mit `true` geht ein Start voraus. |
| `senden.felder` | Schlüsselname → `{ aus, art, muster?, faktor? }`. Der Name muss in `schluessel` stehen — dort steht seine Nummer auf dem Draht. |
| `… .art` | `"text"` oder `"zahl"`. |
| `… .muster` | Regulärer Ausdruck mit einer Fanggruppe, angewandt bevor eine Zahl gelesen wird. Braucht man ständig: eine Benachrichtigung trägt »in 250 m«, nicht `250`. |
| `… .faktor` | Multiplikator nach dem Ausschneiden. Für km → m: `1000`. |
| `melden` | `{ "titel": …, "text": … }` — eine Benachrichtigung auf dem Telefon. `{FELD}` wird auch hier ersetzt. |

**Mehr Rechnen als Ausschneiden und Malnehmen gibt es nicht.** Das ist Absicht:
je mehr ein Zettel kann, desto mehr wird er eine Programmiersprache — und am
Ende dieses Wegs stünde wieder nachgeladener Code, nur mit mehr Umwegen.

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
  wieder hoch. Er ist vom Typ `specialUse` und **nicht** `dataSync`, und das
  ist kein Geschmack: Android 15 verbietet, einen `dataSync`-Dienst aus einem
  `BOOT_COMPLETED`-Empfänger zu starten, und lässt ihn ohnehin nur sechs
  Stunden je vierundzwanzig laufen. Mit `dataSync` wäre der Empfang nach einem
  Neustart nie wieder hochgekommen und im Betrieb täglich verstummt.
* **Benachrichtigungen mithören**, wenn ein Zettel es verlangt. Der
  `NotificationListenerService` ist dabei nebenbei der stabilste
  Hintergrundläufer, den Android hergibt: das System bindet ihn selbst, bindet
  nach Neustart und nach Abstürzen neu, ohne Vordergrunddienst und ohne
  Zeitgrenze.

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

**Der Zettelleser lässt sich ohne Telefon prüfen** — er ist reine Logik, kein
Android. Das ist wichtiger, als es klingt: ein Zettel kommt aus dem Netz, und
was er anordnet, landet in einer Gesundheitsakte. Der Leser ist die einzige
Stelle, die zwischen beidem steht.

```bash
gradle test
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
