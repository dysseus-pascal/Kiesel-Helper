# Kiesel-Helper

Android-App mit zwei Gesichtern:

* **vorne** acht Zahlen aus der Gesundheitsakte des Telefons — Schritte,
  Distanz, Kalorien, Bewegung, Wasser, Schlaf, Ruhepuls, HRV — auf einem
  Schirm, in Bildern und in einem Widget;
* **daneben** der Trend: was ein typischer Mittwoch bei einem ist, aus einer
  eigenen Tabelle, weil Health Connect vergisst;
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

**Zwei Reiter, unten.** Weil der Daumen dort ist — oben wären sie näher am
Titel, aber weiter weg von der Hand.

**Gesundheit** zeigt heute, **Trend** rechnet aus dem eigenen Speicher. Die
Technik — Zustand, Erlaubnisse, Aufgabenliste, Verlauf — liegt hinter dem
**Zahnrad** oben rechts. Sie war einmal ein dritter Reiter, und das war eine
Fehleinschätzung: ein Reiter ist eine Behauptung darüber, wie oft man etwas
anschaut, und das hier schaut man an, wenn etwas nicht geht. Zweimal im Jahr.

| Karte | Werte |
|---|---|
| Bewegung | Schritte, Aktiv, Distanz, Kalorien |
| Schlaf | Schlaf, Tiefschlaf |
| Herz | Ruhepuls, HRV, Puls tief, Puls hoch |
| Ernährung | Wasser, Supplemente |

Einen Balken bekommt nur, was ein Ziel hat. Für einen Ruhepuls gibt es keins,
und ein Balken ohne Ziel wäre eine Behauptung darüber, was gut ist.

### Die Bilder

Zu jeder Karte ein Bild - eine Zahl allein sagt nicht, ob sie hoch ist.

| Karte | Bild |
|---|---|
| Bewegung | Schritte der sieben Tage **vor** heute, dazu das Tagesprofil in Halbstundenstufen |
| Schlaf | die Nacht in Phasen (tief, REM, leicht, wach) + die sieben Nächte **davor** |
| Herz | Punktewolke der letzten **24 Stunden**, mit gleitendem Median; im Trend dieselbe Wolke über 14 Tage |
| Ernährung | Wasser über sieben Tage, dazu die Supplemente: hell geplant, dunkel genommen |

Selbst gezeichnet, ohne Diagramm-Bibliothek: die drei Bilder zusammen sind
kürzer als die Einrichtung einer Bibliothek, und jede Bibliothek brächte ihre
eigenen Farben mit — genau das, was diese App bei Material You schon einmal
rosa gemacht hat.

**Ein Bild behauptet schnell mehr, als es weiss.** Ein Tag ohne Eintrag
bekommt darum keinen Balken der Höhe null, sondern gar keinen, und eine Nacht
ohne Phasen keinen geviertelten Balken.

**Der laufende Tag steht nicht im Schritte- und Schlafbild.** Um zehn Uhr morgens stünde er
auf einem Drittel neben ganzen Tagen, und das Bild sagte »heute war schwach«,
wo »heute ist noch nicht vorbei« gilt. Oben steht er ohnehin, und zwar als das,
was er ist. Wasser und Supplemente behalten ihn: dort will man genau wissen,
was heute noch fehlt.

**Auch die horizontalen Balken tragen ihre Marke.** Ein gedeckelter Balken
verschweigt den Überschuss: neun Stunden Schlaf bei acht Stunden Ideal sahen
aus wie genau acht, weil beide Male der Balken voll war. Die Spur steht deshalb
für den grösseren der beiden Werte, das Ziel sitzt als schmaler Strich darin,
und was dahinter kommt, bekommt dieselbe Farbe wie im Wochenbild.

**Das Pulsbild zeigt die letzten 24 Stunden**, nicht den laufenden Tag. Die
Tagesgrenze ist eine Zählgrenze, kein Sichtschutz — wer sie auf sechs Uhr
setzt, will morgens trotzdem den Verlauf der Nacht sehen. Die Stundenstriche
nennen deshalb die echte Uhrzeit, nicht die Stunde seit Bildanfang.

### Der Ruhepuls kommt in drei Anläufen

Die Uhr zeigt ihn, die Akte hatte ihn trotzdem nicht: nicht jede App schreibt
einen `RestingHeartRateRecord`, manche rechnen ihn nur für die eigene
Anzeige aus. Deshalb der Weg von hinten:

1. der eingetragene Ruhepuls von heute oder gestern;
2. sonst der jüngste eingetragene aus einer Woche;
3. sonst der **Durchschnitt der zehn tiefsten Messungen der Nacht** — und der
   steht dann mit einem **≈** da. Das ist nicht dasselbe, und es wird auch
   nicht so getan.

Zehn und nicht eine: ein einzelner Tiefstwert ist kein Ruhepuls. Ein
verrutschter Sensor, eine schlechte Auflage, und es steht 41 da, wo 54 wäre.
Ein einfacher Durchschnitt genügt — die Auswahl der zehn tiefsten *ist* schon
die Filterung.

### Das Widget

Vier Kacheln mit Balken — Schritte, Aktiv, Schlaf, Wasser — und darunter leise
Ruhepuls, HRV und Distanz. Vier und nicht acht: auf dem Startbildschirm liest
man im Vorbeigehen, und nur was ein Ziel hat, lohnt dort den Blick.

Oben rechts steht, **wie alt** der Stand ist — »vor 4 min«, in ganzen Minuten.
Nicht die Uhrzeit: »21:12« beantwortet die Frage nicht, die man am Widget hat.
Daneben ein Zeichen, das sofort neu liest.

Aufgefrischt wird, sobald etwas eingetragen wird (Wasser, HRV, Supplemente),
sobald der Gesundheits-Schirm gelesen hat — und **spätestens alle zehn
Minuten**. Androids eigener Takt kann nicht unter eine halbe Stunde; die
Zehn-Minuten-Grenze zieht deshalb der Minutentakt des laufenden Dienstes.

**Der Takt kostet nichts.** ACTION_TIME_TICK schickt das System jede Minute an
angemeldete Empfänger, aber nur bei eingeschaltetem Bildschirm — also genau
dann, wenn jemand hinschauen könnte. Ein eigener Wecker im Minutentakt wäre
1440 Weckrufe am Tag für eine Textzeile. Zwischendurch wird nur der Text
nachgezogen, nicht das ganze Widget.

### Die Ziele

| | | |
|---|---|---|
| Wasser | 8 × 300 ml | kommt aus Drinktervall |
| Schritte | 10 000 | Hausnummer |
| Aktiv | 30 min | Hausnummer |
| Schlaf | **einstellbar** | dein Idealwert, siehe unten |

Wasser kommt aus Drinktervall, der Schlaf aus den Einstellungen. Schritte und
Aktiv bleiben Konstanten in `Gesundheit.kt`: zehntausend und dreissig Minuten
sind Hausnummern, an denen sich ohnehin niemand misst.

## Der eigene Speicher

**Health Connect vergisst.** Die Akte hält die Rohdaten nicht ewig; was älter
ist, ist weg — und mit ihm jede Aussage darüber, wie ein Mittwoch bei einem
normalerweise aussieht. Deshalb eine eigene SQLite-Tabelle: **eine Zeile je
Tag**, dreizehn Spalten. Ein Tagesstand kostet gut hundert Zeichen, ein Jahr
passt in weniger als ein einzelnes Foto.

Keine Rohdaten — der Pulsverlauf von vorletztem Dienstag interessiert
niemanden. Es sind die Zahlen, aus denen sich ein Muster lesen lässt.

| | |
|---|---|
| **Beim ersten Start** | wird nachgetragen, was die Akte noch hat (30 Tage, drei Abfragen) |
| **Bei jedem Lesen** | fällt der heutige Stand in die Tabelle |
| **Nie** | wird ein bekannter Wert mit einem unbekannten überschrieben |

Der letzte Punkt ist der wichtigste: wer morgens die App öffnet, hat noch
keinen Schlaf von heute Nacht in der Akte, und der gestrige Eintrag darf davon
nicht sterben.

**Gemessener und geschätzter Ruhepuls stehen in getrennten Spalten.** Einem aus
dem Nachttief hergeleiteten Wert sieht man in einem Jahresmittel nicht mehr an,
woher er kam.

## Der Trend

Die Frage, die ein Tageswert nicht beantwortet: *7985 Schritte — ist das viel?*

| Bild | Antwort |
|---|---|
| **Typische Woche** | sieben Balken, einer je Wochentag, im Mittel über alles Gespeicherte |
| **Verlauf** | acht Kalenderwochen, dazu die Veränderung der letzten vier gegenüber den vier davor |

**Gruppen statt Einzelwerte.** Schlaf ohne Tiefschlaf daneben sagt wenig, ein
Ruhepuls ohne die Spanne des Tages noch weniger:

| Gruppe | Form |
|---|---|
| Schritte, Wasser, Aktiv | ein Balken je Tag |
| Schlaf | ein Balken, der Tiefschlaf dunkel **darin** — er steckt im Schlaf, zwei Balken nebeneinander behaupteten zwei Dinge |
| Herz | eine **Spanne** vom Tagestief zum Tageshoch, der Ruhepuls als heller Strich darin; die HRV daneben, weil Millisekunden nicht auf eine bpm-Achse gehören |

Die gestrichelte Linie ist in beiden Bildern **dasselbe**: der Schnitt über
alle Tage. So heisst »über der Linie« überall dasselbe.

Die Auswahlleiste wird **einmal** gebaut und danach nur umgefärbt — sonst
stünde sie nach jedem Umschalten wieder ganz links, während man rechts aussen
getippt hat.

### Bewegungsprofil

Schritte in Halbstundenstufen über den Tag, blass dahinter der Schnitt der
letzten zwei Wochen. **Die Tagessumme sagt nicht, ob ein Tag schwach war oder
nur spät**: 4000 Schritte um achtzehn Uhr sind ein anderer Tag als 4000 um
zehn.

Balken und keine Linie: zwischen zwei Pulsmessungen liegt ein Verlauf, zwischen
zwei Schrittzählungen eine Summe. Sie zu verbinden hiesse, zwischen zehn und
halb elf etwas zu behaupten.

### Schlafmitte

Die Schlafsätze tragen Anfang und Ende — bisher wurde nur die Dauer genutzt.
Jetzt stehen Einschlafzeit, Aufwachzeit und **die Mitte der Nacht** da, im
Trend mit ihrer Streuung: *»02:47 ± 38 min — regelmässig«*.

**Die Mitte ist der stabilere Wert.** Wer eine Nacht kurz schläft, merkt das am
nächsten Tag; wer jede Nacht zu einer anderen Zeit schläft, merkt es dauerhaft.
Die Dauer sagt das nicht.

Gerechnet wird **ab 18 Uhr**, nicht in Uhrzeiten: 23:10 und 00:30 liegen
achtzig Minuten auseinander, als Tagesminuten aber 1360 — jeder Mittelwert über
Mitternacht hinweg wäre sonst Unsinn, und gerade die Mitte ist hier die
interessante Zahl.

### Zusammenhänge

Eine eigene Gruppe im Trend: zwei Grössen gegeneinander als Punktewolke mit
Ausgleichsgerade, dazu der Korrelationskoeffizient nach Pearson und die Zahl
der gemeinsamen Tage.

| | |
|---|---|
| Schlaf ↔ Ruhepuls | Schlaf ↔ HRV |
| Tiefschlaf ↔ HRV | Schritte ↔ Schlaf |
| Aktiv ↔ Ruhepuls | |

**Fünf Paare, nicht alle.** Aus sieben Spalten liessen sich einundzwanzig
bilden, und wer lange genug sucht, findet in jedem Datensatz eine Korrelation.

**Erst ab 14 gemeinsamen Tagen** wird etwas gezeigt — durch eine Handvoll
Punkte lässt sich jede Gerade legen, und sie sähe überzeugend aus. Gepaart wird
nur, wo **beide** Grössen an demselben Tag etwas wissen; eine Lücke mit dem
Mittelwert aufzufüllen hiesse, eine Messung zu erfinden, und gerade hier fällt
das nicht auf: die Wolke sähe danach sogar ordentlicher aus.

Unter den Bildern steht einmal, was für alle gilt: **Zusammenhang ist keine
Ursache**, und fünf Paare sind fünf Versuche.

### Der typische Tag

Alle Pulsmessungen der letzten **14 Tage** nach Tageszeit übereinandergelegt
(blass), die Trendlinie als gleitender Median hindurch. So verläuft ein Tag
normalerweise — wann der Puls hochgeht, wie breit die Streuung mittags ist, wie
tief es nachts wird.

Vierzehn und nicht dreissig Tage: bei Zehnminutentakt sind das gut 2000
Messungen am Tag, und dreissig Tage wären 60 000 Sätze über eine Prozessgrenze.
Geblättert wird dabei über alle Seiten — wer nur die erste holt, zeichnet eine
Wolke aus dem ersten Drittel des Zeitraums und nennt sie den typischen Tag.

### Fühler und Wochenende

Die Wochentagsbalken tragen einen **Fühler** von der kleinsten bis zur
grössten Messung. Der Mittelwert allein ist eine halbe Aussage: drei Mittwoche
mit 4000, 8000 und 12 000 Schritten ergeben denselben Schnitt wie drei mit je
8000, und nur einer der beiden Fälle heisst »typisch«.

Dazu je Gruppe eine Zeile **Wochenende gegen Werktag**. Kein eigenes Bild — der
Unterschied ist eine Zahl, und ein Balkenpaar dafür wäre Verpackung. Beide
Seiten brauchen mindestens drei Tage.

**Zwei Regeln, die das Bild ehrlich halten:**

* **Heute zählt nicht mit.** Ein angefangener Tag hat immer zu wenig Schritte;
  wer ihn einrechnet, bekommt ein Wochenprofil, in dem der heutige Wochentag
  für immer der schwächste ist — das Muster wäre ein Abbild der Uhrzeit, zu der
  man hinschaut.
* **Ein Wochentag bleibt leer, bis er zweimal aufgezeichnet ist.** Aus einem
  einzigen Mittwoch ein Muster zu lesen ist keine Auswertung, sondern eine
  Erinnerung.

Die Veränderung vergleicht **vier Wochen gegen vier Wochen**, nicht eine gegen
eine: ein Feiertag, eine Erkältung, ein Wochenende weg, und eine Einzelwoche
springt um dreissig Prozent.

## Bedienung

**Nur die Mitte scrollt.** Oben der Name mit dem Zahnrad, unten die Reiter,
dazwischen der Inhalt. Vorher lag beides im Roller und war nach der ersten
Karte weg — man wusste dann nicht mehr, in welchem Reiter man steht.

Das **Zahnrad ist gezeichnet, nicht geladen**: dreissig Zeilen statt eines
Satzes Bilddateien in fünf Auflösungen, und es nimmt die Schriftfarbe an, stimmt
also bei Tag wie bei Nacht. Die Zähne entstehen mit `Path.op` als Vereinigung,
das Loch als Differenz — mit einer Even-Odd-Füllung wäre es kürzer und falsch:
überlappende Flächen löschten sich dort gegenseitig aus, und jeder Zahn risse
ein Loch in den Körper.

**Von oben ziehen holt alles neu.** Der Kreisel verschwindet erst, wenn
wirklich nichts mehr nachkommt: die drei Reiter laden hintereinander, nicht
nebeneinander. Nebenläufig wäre es schneller, aber dann müsste jemand zählen,
wann der Letzte fertig ist.

## Einstellungen

Hinter dem Zahnrad, und bewusst **zwei** Werte — nicht sieben.

### Mein Idealwert für den Schlaf

Acht Stunden sind ein Mittelwert über Menschen, keine Vorgabe für einen. Wer
mit sieben auskommt, bekäme jede Nacht einen Balken vorgehalten, der nichts
bedeutet; wer neun braucht, sähe eine erfüllte Vorgabe, wo eine kurze Nacht
war.

Der gesetzte Wert steht als **farbige Linie** im Nachtbild und im
Wochenprofil, und **was darüber hinausgeht, steht in eigener Farbe** — leise,
denn es ist eine Auskunft und kein Lob. Ohne sie müsste man jede Balkenspitze
mit der Linie vergleichen; mit ihr sieht man es im Vorbeigehen — abgesetzt von der gestrichelten grauen Linie, die den *Schnitt*
zeigt: die eine ist gerechnet, die andere gesetzt. Und der Trend zählt, **in
wie vielen Nächten** er erreicht wurde und wie weit der Schnitt darüber oder
darunter liegt.

Gestellt wird er in Viertelstunden mit zwei Knöpfen. Wer sein Schlafbedürfnis
auf fünf Minuten genau kennt, misst es nicht mit einer Uhr am Handgelenk.

### Wann ein Tag beginnt

Wer um zwei Uhr noch wach ist, hat seine Schritte am Vortag gemacht — der
Kalender sieht das anders. Mit einer Grenze um sechs zählt die Nacht zu dem
Tag, an dem sie begann, und das Widget zeigt um fünf Uhr morgens nicht einen
frisch begonnenen, leeren Tag.

Die Grenze gilt für **alles**, was »heute« heisst: Tageswerte, Wochenbilder,
Trend, Supplementliste, Widget. Entschieden wird sie an **einer** Stelle
(`Einstellungen.heute`) — hätte jede Rechnung ihr eigenes `LocalDate.now()`,
stünde um halb sechs morgens in einem Bild der eine und im nächsten der andere
Tag.

**Der Schlaf hat sein eigenes Fenster.** Eine Nacht beginnt um 18 Uhr, egal wo
der Tag beginnt: beides zu vermischen hiesse, bei einer Grenze um sechs die
halbe Nacht auf zwei Tage zu verteilen.

## Was sie einträgt

Drei feste Aufgaben, **im Code**, nicht in einer Datei aus dem Netz:

| Von | Nach | Was |
|---|---|---|
| Drinktervall | Gesundheitsakte | jedes getrunkene Glas als Wassermenge, mit dem Zeitpunkt von der Uhr |
| Herzintervall | Gesundheitsakte | die nächtliche RMSSD-Messung als Herzratenvariabilität |
| SupCycle | **eigener Speicher** | wie viele Präparate heute anstanden, wie viele davon genommen sind — und seit SupCycle 0.10.0 ihre Namen |
| OsmAnd | Kieselstrasse | Abbiegeart, Entfernung, Strasse, Ankunftszeit |

**SupCycle geht nicht in die Akte, und das ist kein Versehen.** Health Connect
kennt keine Satzart für »genommen«; am nächsten käme ein Ernährungssatz mit
Nährstoffmassen — und die weiss SupCycle nicht, ein Plan dort besteht aus Namen
und Zyklen, nicht aus Milligramm. Eine Zahl zu erfinden, damit sie in eine
fremde Tabelle passt, wäre der schlechteste aller Wege.

Geschickt wird nichts Neues: SupCycle meldet Tag, Fälligkeits- und
Abhak-Bitmaske ohnehin nach jeder Einnahme, für seine eigenen Timeline-Pins.
Gezählt wird, was **fällig war** und davon genommen wurde — ein Präparat, das
heute pausiert, gehört in keine Quote.

**Die Namen kommen seit SupCycle 0.10.0 mit**, alle sechs Plätze durch
Zeilenumbruch getrennt, auch die leeren: die Bitmasken zählen Plätze, nicht
Einträge, und wer die leeren wegliesse, verschöbe jeden Namen dahinter. Damit
steht unter der Quote, *was* heute noch offen ist — danach greift man, wenn man
vor dem Schrank steht. Mit einer älteren Fassung von SupCycle bleibt es bei den
Zahlen; die zuletzt bekannten Namen werden dann nicht gelöscht.

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

**Die Auswertung lässt sich ohne Telefon prüfen** — sie ist reine Rechnung.
Das ist wichtiger, als es klingt: sie ist die einzige Stelle der App, die
etwas *behauptet* (»dein Mittwoch ist schwach«), und eine falsch gezogene
Grenze fällt am Gerät nicht auf, weil das Bild immer plausibel aussieht.

```bash
gradle test
```


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
