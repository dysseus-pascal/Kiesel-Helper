# Kiesel-Helper

Android-App mit zwei Gesichtern:

* **vorne** acht Zahlen aus der Gesundheitsakte des Telefons — Schritte,
  Distanz, Kalorien, Bewegung, Wasser, Schlaf, Ruhepuls, HRV — auf einem
  Schirm und in einem Widget;
* **dahinter** die Brücke zwischen Pebble-Uhr und Telefon: sie trägt ein, was
  die Uhr misst, und schickt der Uhr, was OsmAnd navigiert.

*Kiesel, weil Pebble.*

## Warum vorne Zahlen stehen

Health Connect kann alles und zeigt deshalb nichts zuerst. Bis zu der einen
Zahl, die man täglich wissen will, sind es mehrere Griffe durch Listen — für
eine Auskunft, die man im Vorbeigehen haben möchte.

Die Daten liegen ohnehin schon dort, teils von dieser App selbst eingetragen.
Sie anzuzeigen kostet keine neue Quelle, nur eine Leseerlaubnis.

**Kein Wert wird erfunden.** Steht nichts in der Akte, steht ein Strich da,
keine Null. »Du bist heute keinen Schritt gegangen« ist etwas anderes als
»niemand hat Schritte eingetragen«, und eine Null sagt das Erste, wenn das
Zweite gilt.

### Der Schirm

Zwei Reiter. **Gesundheit** steht vorne, weil man deswegen die App öffnet;
**Technik** ist der alte Schirm mit Zustand, Aufgaben und Verlauf.

| Karte | Werte |
|---|---|
| Bewegung | Schritte, Aktiv, Distanz, Kalorien |
| Schlaf | Schlaf, Tiefschlaf |
| Herz | Ruhepuls, HRV |
| Wasser | Wasser |

Einen Balken bekommt nur, was ein Ziel hat. Für einen Ruhepuls gibt es keins,
und ein Balken ohne Ziel wäre eine Behauptung darüber, was gut ist.

### Die Bilder

Zu jeder Karte ein Bild - eine Zahl allein sagt nicht, ob sie hoch ist.

| Karte | Bild |
|---|---|
| Bewegung | Schritte der letzten sieben Tage, mit Ziellinie |
| Schlaf | die Nacht in Phasen (tief, REM, leicht, wach) + sieben Nächte |
| Herz | Pulsverlauf des Tages, gestrichelt der Ruhepuls |
| Wasser | sieben Tage |

Selbst gezeichnet, ohne Diagramm-Bibliothek: die drei Bilder zusammen sind
kürzer als die Einrichtung einer Bibliothek, und jede Bibliothek brächte ihre
eigenen Farben mit — genau das, was diese App bei Material You schon einmal
rosa gemacht hat.

**Ein Bild behauptet schnell mehr, als es weiss.** Ein Tag ohne Eintrag
bekommt darum keinen Balken der Höhe null, sondern gar keinen, und eine Nacht
ohne Phasen keinen geviertelten Balken.

### Der Ruhepuls kommt in drei Anläufen

Die Uhr zeigt ihn, die Akte hatte ihn trotzdem nicht: nicht jede App schreibt
einen `RestingHeartRateRecord`, manche rechnen ihn nur für die eigene
Anzeige aus. Deshalb der Weg von hinten:

1. der eingetragene Ruhepuls von heute oder gestern;
2. sonst der jüngste eingetragene aus einer Woche;
3. sonst der **tiefste gemessene Puls der Nacht** — und der steht dann mit
   einem **≈** da. Das ist nicht dasselbe, und es wird auch nicht so getan.

### Das Widget

Vier Kacheln mit Balken — Schritte, Aktiv, Schlaf, Wasser — und darunter leise
Ruhepuls, HRV und Distanz. Vier und nicht acht: auf dem Startbildschirm liest
man im Vorbeigehen, und nur was ein Ziel hat, lohnt dort den Blick.

Es frischt sich **alle 30 Minuten** auf; kürzer lässt Android nicht zu. Dazu
nach jedem eigenen Eintrag (Wasser, HRV) und immer dann, wenn der
Gesundheits-Schirm gerade gelesen hat — ein Widget, das älter ist als die App,
die eben daneben offen war, sieht nach Fehler aus.

### Die Ziele

| | | |
|---|---|---|
| Wasser | 8 × 300 ml | kommt aus Drinktervall |
| Schritte | 10 000 | Hausnummer |
| Aktiv | 30 min | Hausnummer |
| Schlaf | 8 h | Hausnummer |

Nur das Wasserziel ist echt. Die anderen drei stehen als Konstanten in
`Gesundheit.kt`; wer andere will, ändert sie dort. Eine Einstellung dafür wäre
ein Bildschirm mehr für eine Zahl, die man einmal im Leben setzt.

## Was sie einträgt

Drei feste Aufgaben, **im Code**, nicht in einer Datei aus dem Netz:

| Von | Nach | Was |
|---|---|---|
| Drinktervall | Gesundheitsakte | jedes getrunkene Glas als Wassermenge, mit dem Zeitpunkt von der Uhr |
| Herzintervall | Gesundheitsakte | die nächtliche RMSSD-Messung als Herzratenvariabilität |
| OsmAnd | Kieselstrasse | Abbiegeart, Entfernung, Strasse, Ankunftszeit |

Auf jedem Eintrag liegt ein **Riegel** gegen Doppelte: die Uhr schickt ihren
Stand bei jeder Gelegenheit mit, nicht nur beim Trinken. Ohne ihn stünde
dasselbe Glas mehrfach in der Akte.

### Es gab einmal ein Zettelsystem

Bis Fassung 0.3.0 stand *nicht* im Code, was eingetragen wird: die App lud
JSON-Beschreibungen aus GitHub-Repos, eine je Uhr-App, und führte aus, was dort
stand. Das war richtig gedacht für eine App, die viele benutzen und die neue
Uhr-Apps kennenlernen soll, ohne neu gebaut zu werden.

Diese benutzt einer. Für ihn sind es drei Aufgaben, und der ganze Apparat —
Katalog, Regelwerk, Zettelleser, Einbinde-Schirm, zwei Beschreibungs-Repos —
kostete mehr, als er trug. Er ist weg; die App wurde dabei von 3660 auf gut
1500 Zeilen kleiner.

Die Mauer, an der das Zettelsystem ohnehin stand: **Berechtigungen für die
Gesundheitsakte stehen im Manifest** und werden beim Installieren
festgeschrieben. Ein Zettel konnte die Liste so wenig erweitern wie
nachgeladener Code. Was im Manifest fehlt, kann nichts nachreichen.

Mit dem Zettelsystem ging auch der `NotificationListenerService` — und mit ihm
die unangenehmste Freigabe, die diese App je verlangt hat: Zugriff auf **jede**
Benachrichtigung des Telefons, für eine Handvoll Zahlen. Seit OsmAnd über seine
eigene Schnittstelle antwortet, braucht es ihn nicht mehr.

## OsmAnd

Für die Navigation fragt Kiesel-Helper **OsmAnd selbst**, nicht seine
Benachrichtigung. OsmAnd bietet dafür einen Dienst an (`OsmandAidlServiceV2`,
im Manifest `exported="true"`, ohne Berechtigung und ohne Aufruferliste —
nachgesehen, nicht angenommen). Was von dort kommt, ist ungleich besser als
Text aus einer Meldung:

| | |
|---|---|
| `updateNavigationInfo` | Entfernung zur Abzweigung **in Metern als Zahl**, Abbiegeart als **Kennzahl 1–14** (Kreisverkehr inbegriffen), Linksverkehr |
| `getAppInfo` | Strassenname, Restweg, Restzeit, **Ankunftszeit als Unix-Sekunden** |

Weitergereicht wird es an [Kieselstrasse](https://github.com/dysseus-pascal/Kieselstrasse).
Höchstens alle vier Sekunden — OsmAnd meldet im Sekundentakt, und jede Meldung
weiterzugeben hiesse, die Funkstrecke zur Uhr zu fluten. **Ausgenommen ist der
Wechsel der Abbiegeart**: das ist der nächste Schritt, und der darf nicht auf
den Takt warten.

**Zwei stille Hürden**, beide erst am Telefon sichtbar:

* Ohne `<queries>` im Manifest ist OsmAnd seit Android 11 schlicht unsichtbar —
  `bindService` findet nichts, nichts stürzt ab, nichts warnt, und auf der Uhr
  kommt nichts an.
* OsmAnd lässt fremde Apps erst nach einem **Schalter** zu: beim ersten
  Verbindungsversuch trägt es die App unter *Menü → Plugins* ein, aber
  ausgeschaltet. Bis der Schalter umgelegt ist, antwortet jede Methode mit −1.
  Die App sagt das inzwischen auf dem Technik-Schirm, statt still zu schweigen.

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
* **Mitschreiben.** Die letzten hundert Meldungen mit Zeitstempel stehen unter
  *Verlauf*. Gebaut, weil der Logcat-Puffer nur Stunden hält und im Auto kein
  Kabel steckt — eine Fahrt muss ihre Spur selbst mitbringen.

## Bauen

```bash
gradle assembleDebug
```

Gebraucht werden JDK 17, ein Android-SDK mit API 36 und Gradle 9.5.1 (9.6 hat
eine interne Schnittstelle entfernt, auf die das Android-Plugin 8.x noch baut).

Dieselbe Kette läuft bei GitHub Actions; jeder Push erzeugt ein Debug-APK als
Artefakt des Laufs ([Bauen](../../actions/workflows/bauen.yml) → letzter Lauf →
*kiesel-helper-debug*). Fertige Pakete hängen an den
[Veröffentlichungen](../../releases).

```bash
adb install -r kiesel-helper-debug.apk
```

Der Lauf belegt, dass die App übersetzt und ein Paket ergibt. Dass sie am
Telefon das Richtige **tut**, belegt er nicht — das zeigt sich erst dort.

## Grenzen

* **Gelesen wird, was andere eintragen.** Schritte, Puls und Schlaf kommen von
  der Uhr oder einer anderen App; fehlt die Quelle, bleibt das Feld leer, und
  daran kann diese App nichts ändern.
* **Der Schlaf zählt ab 18 Uhr des Vortags.** Wer um 23 Uhr ins Bett geht, hat
  seinen Schlaf am Vortag begonnen — ein Fenster ab Mitternacht schnitte ihn in
  zwei Hälften. Wer tagsüber schläft, sieht das nicht.
* **Die HRV ist die jüngste aus sieben Tagen**, nicht die von heute. Sie kommt
  nachts von der Uhr und gilt tagsüber weiter; ein Tagesfenster liesse sie am
  Nachmittag verschwinden, obwohl sie steht.
* **Ohne Leseerlaubnis schweigt die Akte**, sie sagt nicht Nein. Die Felder
  blieben leer und sähen aus wie ein Fehler der App — deshalb steht auf dem
  Gesundheits-Schirm eine Karte, die es benennt.
* Health Connect gibt es ab Android 14 im System; davor braucht es die App aus
  dem Play Store.

## Lizenz

**[GPLv3](LICENSE).** Bis zum 18.09.2026 war diese App gemeinfrei nach CC0 1.0.
Mit OsmAnds Schnittstelle kam fremder Code ins Projekt — `osmand-api/` ist das
Modul `OsmAnd-api` aus [osmandapp/OsmAnd](https://github.com/osmandapp/OsmAnd),
unverändert übernommen, und OsmAnd steht unter GPLv3. Die ist Copyleft: wer ein
solches Modul mit seiner App zu einem Werk verbindet und das verbreitet, stellt
das Ganze unter dieselbe Lizenz.

Übernommen wurde das **ganze** Modul und nicht nur das Gebrauchte, weil AIDL
die Transaktionsnummern nach der Reihenfolge der Deklarationen vergibt — wer
ungenutzte Methoden streicht, verschiebt alle folgenden, und die App riefe
danach still die falsche Funktion auf.

**Die Uhr-Apps sind davon nicht berührt.** Sie enthalten keinen fremden Code
und bleiben gemeinfrei nach CC0 1.0.
