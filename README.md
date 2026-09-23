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

**Drei Reiter, unten.** Weil der Daumen dort ist — oben wären sie näher am
Titel, aber weiter weg von der Hand.

Sie trennen **drei Fragen, nicht drei Datenquellen**:

| Reiter | Die Frage dahinter | Karten |
|---|---|---|
| **Gesundheit** | Was hat der Tag mit mir gemacht? | Bewegung, Schlaf, Herz, »Wie war der Tag?« |
| **Training** | Was habe ich getan? | Die Woche, das letzte Training mit Karte, die davor |
| **Ernährung** | Was geht hinein? | Wasser, Präparate, Koffein |

Die Technik — Zustand, Erlaubnisse, Aufgabenliste, Verlauf — liegt hinter dem
**Zahnrad** oben rechts. Sie war einmal ein Reiter, und das war eine
Fehleinschätzung: ein Reiter ist eine Behauptung darüber, wie oft man etwas
anschaut, und das hier schaut man an, wenn etwas nicht geht. Zweimal im Jahr.

**Der Trend war auch einmal ein Reiter.** Er ist aber eine *Antwort* und keine
eigene Frage: man sieht 7985 Schritte und will wissen, ob das viel ist. Als
Reiter kostete das zwei Bewegungen — unten umschalten, oben die Kategorie
suchen — und zwischen ihnen vergisst man, was man wissen wollte. Jetzt führt
**jede Karte weiter**: ein Tippen, und der Trend öffnet sich gleich bei der
richtigen Grösse.

**Eine Trendseite je Karte, ohne Leiste.** Eine Zeit lang stand oben eine
Auswahl mit allen Gruppen, damit man vom Schlaf zum Herz wechseln konnte. Das
machte aus der Antwort wieder einen Katalog: wer auf den Schlaf tippt, will den
Schlaf sehen — und was mit ihm zusammenhängt —, nicht alles andere daneben.

Damit man eine Fläche auch antippt, steht neben jeder Überschrift ein
**»Trend ›«**. Eine Karte, die still auf eine Berührung wartet, wird nie
gefunden.

| Karte | Werte |
|---|---|
| Bewegung | Schritte, Aktiv, Distanz, Kalorien |
| Schlaf | Schlaf, Tiefschlaf |
| Herz | Ruhepuls, HRV, Puls tief, Puls hoch |
| Ernährung | Wasser, Supplemente |

Einen Balken bekommt nur, was ein Ziel hat. Für einen Ruhepuls gibt es keins,
und ein Balken ohne Ziel wäre eine Behauptung darüber, was gut ist.

**Gesundheit und Ernährung lesen denselben Tagesstand — einmal.** Ihn je
Reiter zu holen hiesse, die teuerste Stelle der App doppelt zu bezahlen, und
die beiden Schirme könnten auseinanderlaufen.

### Farbe sagt, wo man ist

Jeder Reiter hat seinen eigenen Ton — Gesundheit den Wasserton, der von
Anfang an da war, Training einen warmen Erdton, Ernährung ein Moosgrün. Er
steckt bis in die Balken der Diagramme und in den Kreisel beim Ziehen, und
unten trägt ihn der Name des Reiters, auf dem man steht.

Die drei sind **gleich tief und gleich leise**: kein Reiter soll lauter sein
als die anderen, nur unterscheidbar. Und gefärbt ist immer nur der gewählte —
drei farbige Wörter nebeneinander wären ein Farbkasten, eines ist eine
Auskunft.

Der Trend-Schirm **behält den Ton der Karte**, aus der man kam. Wer auf eine
grüne Karte tippt, soll nicht auf einem blauen Schirm landen; der Weg dorthin
wäre sonst nicht mehr zu sehen.

### Das lange Warum steht hinter einem Zeichen

Diese App erklärt viel, und das bleibt so: eine Zahl ohne ihre Herkunft ist
eine Behauptung. Nur standen die Erklärungen bisher alle **offen** unter den
Bildern — und wer sie zum dritten Mal liest, liest sie gar nicht mehr. Sie
wurden zu grauem Rauschen, durch das man zur nächsten Zahl scrollt.

Jetzt steht dort ein **ⓘ**. Der kurze Satz bleibt sichtbar, weil er sagt,
*was* man sieht; das lange *Warum* kommt auf Tippen und geht auf Tippen
wieder weg.

**Ein eigenes Fenster, kein Systemdialog.** Ein `AlertDialog` nimmt das Thema
des Systems an — auf neueren Telefonen also Material You, und damit stünde
mitten in dieser App ein rosa Kasten. Dasselbe Kartenbild wie überall sonst
kostet zehn Zeilen mehr und passt. Es ist derselbe Grund, aus dem die App
ihre Farben selbst mitbringt.

### Der Trainings-Reiter

Oben die **letzten sieben Tage** in drei Zahlen: wie viele Trainings, wie viel
Zeit, wie viele Kilometer. Nicht der Monat und nicht das Jahr — eine
Trainingswoche ist die Einheit, in der man plant; was im August war, sagt
heute nichts mehr.

Unter den Zahlen **vier Wochen als Kalender**: ein Punkt je Tag. Ein
Trainingstag ist ein Kreis in der Farbe seiner Art, und die Grösse sagt, wie
lang es war; kam eine zweite Art dazu, liegt ihr Ring aussen. Punkte und keine
Kacheln: ein Raster voller Felder mit Zahlen darin war laut und sah nach
Tabelle aus.

Darunter **das jüngste Training gross**: der Puls als Fläche über die Zeit, mit
Schnitt und Spitze. Beim Krafttraining stehen die Sätze auf der Zeitachse,
hoch für viele Wiederholungen, breit für lange Sätze, dazwischen die Pause.
Beim Schwimmen steht jede Bahn als Balken da, die schnellste hervorgehoben.
Dazu Strecke, Tempo, Aufstieg und die Karte.

Dann **acht Wochen**, gestapelt nach Art, und **woraus es besteht**: die Arten
eines Vierteljahres als ein Band nach Zeit. Ein Band und kein Kuchen, weil man
Anteile an Längen besser liest als an Winkeln.

**Die älteren Trainings haben eine eigene Seite.** Im Reiter steht von ihnen
nur eine Karte — wie viele, wie lange, die Zeichen der jüngsten —, und ein
Tippen öffnet sie: eine Zeile je Training, nach Monaten. Zwanzig Karten unter
den Bildern nahmen mehr Platz als alles andere, und gesucht wird in ihnen
selten. Ein Tippen auf eine Zeile zeigt das Training so gross wie das jüngste,
mit Puls, Sätzen oder Bahnen und Karte.

**Jede Art hat ihre Farbe** und ihr Zeichen — warme Erdtöne für das, was auf
dem Boden stattfindet, kühle für Yoga und Wasser. Man erkennt sie im Kalender
und in der Woche, ohne die Legende zu lesen.

Die Liste kommt **aus der Gesundheitsakte**, nicht aus einer eigenen Tabelle.
Dort stehen die Sitzungen ohnehin; eine zweite Liste daneben wäre eine zweite
Wahrheit. Trainings anderer Apps erscheinen deshalb mit, sobald sie dort
stehen.

### Der Ernährungs-Reiter

Hier stehen die einzigen Zahlen, die man selbst macht — und die soll man
sehen, nicht lesen.

* **Wasser** ist ein Glas, das sich füllt, daneben die Gläser des Tages als
  Reihe: »noch drei« ist eine Auskunft, nach der man handelt, »noch 900 ml«
  muss man erst umrechnen. Darunter der Tag als Leiste mit einem Tropfen je
  Glas — die Summe verschweigt, ob der Nachmittag trocken war. Wasser ist
  blau, auch im grünen Reiter.
* **Präparate** sind ein Ring, der sich schliesst, wenn alles genommen ist,
  und daneben die Liste: ein leerer Kreis für das, was noch ansteht.
* **Koffein** ist eine Kurve: jede Tasse ein Sprung, danach ein Abklingen mit
  einer Halbwertszeit von rund fünf Stunden. Die Kurve sagt, was gerade wirkt
  und wie viel zur Schlafenszeit von letzter Nacht noch da ist. 240 mg am Tag
  sind harmlos, wenn das letzte um zehn kam, und nicht, wenn es um fünf kam.
  Die fünf Stunden sind ein Mittel; die Kurve zeigt die Form, keine Messung.

### Sätze, Pausen, Bahnen

Seit Kieselsport 0.5.0 zählt die Uhr beim Krafttraining Wiederholungen und
misst die Pausen, und beim Schwimmen zählt sie Bahnen über den Kompass. Sie
schickt beides als Liste mit — `beginn:anzahl:dauer` je Abschnitt — und hier
wird daraus, was die Gesundheitsakte dafür hat:

| Von der Uhr | In der Akte |
|---|---|
| ein Satz mit 12 Wiederholungen | ein **Segment** mit `repetitions = 12` |
| die Lücke zum nächsten Satz | ein Segment vom Typ **Pause** |
| eine Bahn | eine **Runde** mit ihrer Länge in Metern |

**Die Pausen stehen nicht in der Liste** — sie ergeben sich aus den Lücken
dazwischen. Das ist der ganze Grund, die Zeiten mitzuschicken: »vier Sätze«
sagt wenig, »vier Sätze mit anderthalb Minuten dazwischen« ist die Aussage.

**Alles oder nichts.** Die Akte weist einen Eintrag zurück, dessen Segmente
ausserhalb der Sitzung liegen oder sich überschneiden — und zwar den *ganzen*
Eintrag. Lieber ohne Abschnitte eintragen als das Training verlieren: deshalb
wird geprüft, und im Zweifel bleibt die Liste leer. Eine halb angekommene
Zeile (der Postausgang der Uhr bricht ab, statt zu kürzen) fällt schon beim
Zerlegen weg. 7 Prüfungen decken das ab.

Welche Übung es war, weiss die Uhr nicht — sie sieht eine Bewegung, keine
Hantelbank. In der Akte steht deshalb »anderes Training« und nicht
»Bankdrücken«.

**Die Spuren werden beim Laden gelesen, nicht beim Zeichnen** — und die Punkte
nur für das oberste Training. Eine Stunde Laufen sind tausend Zeilen JSON;
zwanzig solche Dateien beim Zusammensetzen der Ansicht zu lesen hielte den
Bildschirm an, und der Fehler fiele erst auf, wenn jemand ein halbes Jahr lang
trainiert hat. Die Länge bleibt, die Punkte nicht.

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

## Was in der Gesundheitsakte landet

**Alles, wofür sie einen Platz hat.** Vier Satzarten schreibt die App:

| Was | Satzart | Kennung |
|---|---|---|
| Wasser (Drinktervall) | `HydrationRecord` | `drinktervall-<Zeitpunkt>` |
| HRV (Herzintervall) | `HeartRateVariabilityRmssdRecord` | `herzintervall-<Zeitpunkt>` |
| Koffein | `NutritionRecord` (`caffeine`) | `koffein-<Zeitpunkt>` |
| Präparate (SupCycle) | `NutritionRecord` (nur `name`) | `supcycle-<Tag>-<Platz>` |

**Nur eines bleibt draussen: die Einschätzung von 1 bis 5.** Für »wie ich mich
fühle« hat die Akte keinen Satz, und einen unpassenden zu nehmen hiesse, eine
Zahl als etwas auszugeben, was sie nicht ist.

Die Präparate gehen **ohne Nährstoffmengen** hinein. SupCycle kennt Namen und
Zyklen, keine Milligramm; eine Menge zu erfinden, damit das Feld gefüllt ist,
wäre schlimmer als ein leeres Feld — sie täuschte Genauigkeit vor, die es
nirgends gibt.

### Keine doppelten Einträge

Zwei Quellen, zwei Mechanismen:

**Gegen eigene Doppelte hilft die `clientRecordId`.** Health Connect führt
Einträge mit derselben Kennung derselben App zusammen: wird einer zweimal
geschrieben, **ersetzt** der zweite den ersten, statt danebenzustehen. Die
Kennung kommt deshalb aus dem *Ereignis* und nicht aus der Uhrzeit des
Schreibens — derselbe Schluck Wasser ergibt dieselbe Kennung, auch wenn die Uhr
ihn eine Stunde später noch einmal meldet. Der Riegel in den Einstellungen
spart nur die Abfrage; er ist weg, sobald jemand die App-Daten löscht, die
Kennung überlebt das.

**Gegen fremde Doppelte hilft nur Nachsehen.** Die Pebble-App trägt selbst ein
— Schritte, Schlaf, Puls, womöglich auch die Herzratenvariabilität. Zwei Apps,
die dieselbe Messung eintragen, ergeben **zwei** Sätze; die Akte führt nur
zusammen, was aus derselben App mit derselben Kennung kommt. Vor jedem Schreiben
schaut die App deshalb nach, ob dort schon etwas steht:

| | Fenster | Warum |
|---|---|---|
| HRV | ± 5 min | einmal pro Nacht gemessen; zwei Apps melden sie nicht sekundengenau |
| Wasser | ± 1 min | zwei Gläser in fünf Minuten sind möglich, zwei Einträge in derselben Minute nicht |

Findet sich ein fremder Satz, wird **nicht** geschrieben, und im Verlauf steht,
wer zuvorgekommen ist. Wer wirklich was einträgt, zeigt die Bestandsliste in
den Einstellungen — sie nennt zu jeder Satzart die schreibende App.

**Was schon doppelt drinsteht, räumt das nicht auf.** Die Prüfung greift ab
jetzt; ältere Dubletten müsstest du in Health Connect selbst löschen.

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

## Die Seite zum Training

Die Karte im Trainings-Reiter zeigt die Grundzahlen — Dauer, Puls, Strecke —
und führt beim Antippen auf eine eigene Seite mit allem, was mehr ist als der
erste Blick:

- **Pulszonen** — Zeit je Zone als Balken und Liste, in den Farben der Uhr.
  Die Zonen rechnen sich aus dem **Maximalpuls** in den Einstellungen; er ist
  von Hand einzutragen, derselbe wie in Kieselsport, die Uhr schickt ihn nicht
  mit. Ein Satz darunter sagt, was für ein Training es war.
- **Puls über die Strecke** — die Kurve über den Kilometern, die Zonen als
  Bänder dahinter. Die Strecke ist die Achse, nicht die Zeit: »am Anstieg bei
  Kilometer vier« ist, wie man sich an eine Fahrt erinnert.
- **Tempo** — Schnitt, Spitze, bewegte Zeit; darunter das Tempo über die
  Strecke, gefärbt wie auf der Karte. Auf dem Rad in km/h, sonst in min/km.
- **Tempo auf der Karte** — die Linie trägt das Tempo als Farbe: blau, wo es
  zäh war, über Grün und Gelb bis Rot, wo es lief. Die Spanne sind das 5.- und
  das 95.-Perzentil, damit ein Ausreisser nicht alles andere blau färbt.
  Aufeinander folgende Punkte derselben Farbstufe werden zu einer Linie — ein
  paar Dutzend statt Tausend, sonst wird die Karte zäh.
- **Höhenprofil** — wenn das GPS Höhen lieferte und mehr als zehn Meter
  dazwischen liegen; geglättet über fünf Punkte, weil GPS-Höhe zittert.
- **Kilometer für Kilometer** — Zeit, Tempo, Puls und Aufstieg je Kilometer,
  der schnellste hervorgehoben.

**Jede Sportart hat ihre eigene Frage**, und die Seite zeigt die Antwort
darauf statt derselben Bilder für alle:

| Art | Was die Seite zeigt |
|---|---|
| Laufen, Bike, MTB | Zonen, Puls und Tempo über die Strecke, Tempo als Farbe auf der Karte, Höhenprofil, Kilometer |
| Wandern | Zonen, **Höhenprofil zuerst**, Aufstieg in m/h, Puls über die Strecke, **Steigung als Farbe auf der Karte** (blau bergab, rot bergauf), Kilometer |
| Kraft | Zonen, Puls über die Zeit mit den Sätzen als Bändern, Satztabelle (Wdh., Dauer, Puls, Pause), und **wie schnell der Puls in den Pausen fällt** — Schläge je Minute |
| Yoga | Zonen, Puls über die Zeit, **Anfang gegen Ende** (wurde man ruhiger?), tiefster Puls, die **HRV** aus dieser Stunde |
| Schwimmen | Zonen, Puls über die Zeit mit jeder zweiten Bahn als Band, Bahnen und Strecke, Zeit je 100 m, **Schwankung der Bahnzeiten**, Bahn für Bahn gefärbt |

Keine Karte beim Yoga, im Becken oder in der Halle — und dort läuft auch kein
GPS mehr mit: das Telefon zeichnet nur bei Laufen, Bike, MTB und Wandern auf.

Das Tempo ist über rund zehn Sekunden geglättet: aus zwei GPS-Punkten im
Sekundenabstand wird sonst ein Tempo, das zwischen 10 und 40 km/h flattert.
Gerechnet wird in `Trainingsanalyse`, ohne Schirm — und deshalb mit Tests.

## Was die Uhr direkt liefert

Seit 0.38.0 kommen drei Dinge nicht mehr nur über Health Connect, sondern
direkt von der Uhr — und landen von hier aus in der Akte:

- **Die Pulskurve zum Training**, von Kieselsport (ab 0.10.0) in Stücken per
  AppMessage: ein Byte je zehn Sekunden, bis zu 300 je Nachricht, nach der
  Zusammenfassung (`KURVE_AB`, `KURVE_ANZAHL`, `KURVE` als Rohdaten). Die
  Werte sammeln sich in einer Datei je Training; ist das letzte Stück da, geht
  die Kurve als Herzfrequenz-Eintrag in die Akte. Der Trainings-Reiter liest
  sie ohnehin von dort — sie steht damit von selbst unter dem Training. Der
  Weg über Data Logging (0.38.0) blieb leer: die neue Pebble-App reicht es
  nicht an klassische Companion-Apps weiter. Der Empfänger dafür bleibt drin,
  falls sich das ändert.
- **Tempo auf dem Rad in km/h**, beim Laufen und Wandern in min/km.
- **Die Nacht und der Ruhepuls**, von Herzintervall (ab 0.7.0): die letzte
  abgeschlossene Nacht — Schlafbeginn, Schlafende — und der mittlere Puls
  darin als Ruhepuls fahren mit dem Ergebnis der Nachtmessung mit, meist einen
  Tag versetzt, aber mit den echten Zeiten. Schlaf wird nur eingetragen, wenn
  nicht schon eine andere App dieselbe Nacht geschrieben hat; der Ruhepuls
  einmal je Nacht, zu ihrem Ende.
- **HRV aus dem Yoga**, von Kieselsport: der RMSSD des Trainings, als eigener
  Satz zum Trainingsende — wie die nächtliche Messung von Herzintervall.

## Sicherung in einen Ordner auf dem Telefon

**Die Gesundheitsakte hält rund dreissig Tage.** Alles, was diese App an
Wochenprofilen, typischen Tagen und Zusammenhängen rechnet, steht danach nur
noch in ihrer eigenen Tabelle — und die liegt in den App-Daten eines einzigen
Telefons. Ein Wechsel, ein Zurücksetzen, ein kaputtes Gerät, und ein Jahr
Aufzeichnung ist weg.

**Ein Ordner, den eine Sync-App abgleicht.** Man wählt ihn im Ordnerdialog des
Systems — am besten den Ordner, den DAVx5, Nextcloud, mailbox.org Drive oder
Syncthing synchronisiert. Kiesel-Helper schreibt dorthin, die Sync-App trägt es
hinauf; Anmeldung und Eigenheiten des Servers sind deren Sache. Das geht mit
jedem Anbieter, und es geht ohne Netz: die tägliche Sicherung läuft auch im
Flugmodus, die Sync-App holt nach. Ohne Sync-App bleibt es eine Kopie auf dem
Telefon, die man abholen kann.

**Kein WebDAV mehr.** Es gab eine eigene WebDAV-Anbindung (0.33 bis 0.36); sie
scheiterte an mailbox.org und an Icedrive — jeder Server hat seine Eigenheiten
(`HEAD` auf einen Ordner gibt 404, `MKCOL` gibt 412, die Webseite antwortet auf
`PROPFIND` mit 200), und eine Sicherung, die an ihnen scheitert, ist keine.
Eine Sync-App wie DAVx5 kennt diese Eigenheiten; diese App muss sie nicht
ein zweites Mal lernen. Mit WebDAV gingen auch der Passworttresor und OkHttp.

```
Kiesel/
├── kiesel-helper.json     ← die ganze Tagestabelle, lesbar
└── spuren/
    ├── spur-1758400000.jsonl
    └── spur-1758486400.jsonl
```

**Als Klartext, nicht als Datenbankabzug.** Ein JSON, das man öffnen und lesen
kann, ist auch dann noch etwas wert, wenn es diese App nicht mehr gibt. Ein
SQLite-Abzug wäre kleiner und in fünf Jahren ein Rätsel. In jeder Datei steht
eine **Fassungsnummer**: eine Sicherung überlebt die App, die sie geschrieben
hat.

**Die Strecken gehen nur einmal hinüber.** Eine Stunde Laufen sind tausend
Punkte; sie täglich erneut zu schreiben wäre jeden Tag dasselbe Megabyte. Eine
Spur ändert sich nach dem Training nicht mehr — was schon im Ordner liegt,
steht in einer Liste daneben. Wird ein anderer Ordner gewählt, beginnt die
Liste von vorn.

### Zurückholen ergänzt, es überschreibt nicht

Eine Sicherung ist **älter** als das, was gerade auf dem Telefon steht — sonst
bräuchte man sie nicht. Sie darüberzulegen hiesse, die letzten Tage gegen alte
Zahlen zu tauschen. Geschrieben wird deshalb nur, wo lokal **nichts** steht;
eine vorhandene Strecke wird nicht angefasst.

Das ist zugleich die Antwort auf zwei Telefone: beide ergänzen einander,
keines löscht das andere.

### Die Erlaubnis ist dauerhaft

Der Ordnerdialog gibt eine Erlaubnis für genau diesen Ordner, und die App
behält sie (`takePersistableUriPermission`) über den Neustart hinaus. Sie fällt
weg, wenn die Sync-App deinstalliert oder der Ordner gelöscht wird — dann sagt
die Prüfung das, und der Ordner wird neu gewählt.

**WorkManager** für die tägliche Sicherung. Ein Wecker, den Android im
Stromsparen verschluckt, wäre eine Sicherung, die es nur gibt, wenn man daran
denkt — und dann hätte man sie auch von Hand angestossen.

Das Umwandeln selbst — Tabelle zu JSON und zurück, und die Frage, was eine
Lücke ist — steht **ohne Netz und ohne Android** in einer eigenen Datei und
ist mit 8 Prüfungen abgedeckt. Wer eine Sicherung schreibt, die sich nicht
zurücklesen lässt, merkt es genau einmal: dann, wenn er sie braucht.

## Der Trend

**Nach einer Neuinstallation** ist die eigene Tabelle leer. Die App holt beim
Start von selbst den letzten Monat aus Health Connect — mehr gibt die Akte ohne
weitere Erlaubnis nicht heraus. Unter *Einstellungen → Frühere Daten →
Nachladen* fragt sie nach der Erlaubnis für ältere Daten
(`READ_HEALTH_DATA_HISTORY`) und holt dann ein Jahr, in Fenstern zu dreissig
Tagen: eine Abfrage liefert nur eine Seite Sätze, und ein Jahr HRV-Messungen
passte nicht hinein.

Die Frage, die ein Tageswert nicht beantwortet: *7985 Schritte — ist das viel?*

| Bild | Antwort |
|---|---|
| **Typische Woche** | sieben Balken, einer je Wochentag, im Mittel über alles Gespeicherte |
| **Verlauf** | acht Kalenderwochen, dazu die Veränderung der letzten vier gegenüber den vier davor |

**Gruppen statt Einzelwerte.** Schlaf ohne Tiefschlaf daneben sagt wenig, ein
Ruhepuls ohne die Spanne des Tages noch weniger:

| Seite | Form |
|---|---|
| Bewegung (Schritte, Aktiv), Ernährung (Wasser) | ein Balken je Tag |
| Schlaf | ein Balken, der Tiefschlaf dunkel **darin** — er steckt im Schlaf, zwei Balken nebeneinander behaupteten zwei Dinge |
| Herz | eine **Spanne** vom Tagestief zum Tageshoch, der Ruhepuls als heller Strich darin; die HRV daneben, weil Millisekunden nicht auf eine bpm-Achse gehören |

Die gestrichelte Linie ist in beiden Bildern **dasselbe**: der Schnitt über
alle Tage. So heisst »über der Linie« überall dasselbe.

**Die Farbe wird beim Bauen festgehalten, nicht beim Zeichnen.** Der Ton des
Reiters ist eine einzige Stelle für die ganze App. Fragte ein Bild ihn erst ab,
wenn es gezeichnet wird, malte es in der Farbe des Reiters, der zuletzt geladen
hat — und das war die Ernährung.

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

Zwei Grössen gegeneinander als Punktewolke mit Ausgleichsgerade, dazu der
Korrelationskoeffizient nach Pearson und die Zahl der gemeinsamen Tage. **Ein
Paar steht auf jeder Trendseite, deren Grösse es enthält** — Schlaf ↔ Ruhepuls
also beim Schlaf und beim Herz, Koffein ↔ Tiefschlaf beim Schlaf und bei der
Ernährung. Solange zu wenige Tage da sind, steht dafür ein Satz und nicht eine
leere Karte je Paar.

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
wirklich nichts mehr nachkommt: Tagesstand und Trainings laden
hintereinander, nicht nebeneinander. Nebenläufig wäre es schneller, aber dann müsste jemand zählen,
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

### Selbst eintragen

Zwei Dinge, die kein Sensor weiss — und sie stehen in verschiedenen Reitern,
weil sie verschiedene Dinge sind:

**»Wie war der Tag?«** steht unter **Gesundheit** — eine Zahl von 1 bis 5.
Eine Uhr misst, wie lange man geschlafen hat; ob man sich ausgeruht *fühlt*,
weiss nur der Mensch. Es ist eine Beobachtung des Tages.

**Koffein** steht unter **Ernährung** — ein Tipp je Getränk (Kaffee 80 mg,
Espresso 60, Tee 40, Energydrink 80). Es ist keine Beobachtung, sondern
etwas, das man tut, und zwar mehrmals am Tag: der Knopf muss nah liegen. Die
Milligramm sind Hausnummern; die interessante Hälfte ist der **Zeitpunkt des
letzten**, denn der erklärt die Nacht, über die man sich wundert.

Ein Tipp je Sache, nicht mehr. Was mehr kostet, trägt niemand drei Wochen lang
ein — und drei Wochen sind die Untergrenze, ab der sich etwas ablesen lässt.
Dafür stehen zwei neue Paare unter *Zusammenhänge*: **Schlaf ↔ Energie** und
**Koffein ↔ Tiefschlaf**.

### Nachtpuls: das ± ist nicht die HRV

Neben dem geschätzten Ruhepuls steht die **Streuung der Pulswerte über die
Nacht**: *»In der Nacht 54 ± 6 bpm, über 42 Messungen.«* Beides kommt aus
demselben Lesevorgang.

**Sie darf nicht HRV heissen.** RMSSD misst die Schwankung zwischen
*aufeinanderfolgenden Schlägen*, in Millisekunden — dafür braucht es die
Zeitpunkte einzelner Schläge, und die stehen in keinem `HeartRateRecord`.
Selbst bei dichter Messung reichte die Auflösung nicht: bei 60 bpm ist **ein
einziger Schritt in der bpm-Zahl schon rund 16 ms**, und RMSSD liegt
typischerweise bei 20 bis 50 ms — die Rundung wäre so gross wie das Signal.

Was hier steht, sagt etwas anderes, aber nichts Falsches: wie ruhig eine Nacht
verlief. Sie wird mitgespeichert, damit sich später fragen lässt, woran sie
hängt.

### Kartenlink ausprobieren

In den Einstellungen: Link einfügen, *Nur prüfen* oder *An OsmAnd geben*.

An der Umleitung hängen **drei Dinge hintereinander** — Android muss den Link
überhaupt weitergeben, die Zerlegung muss ihn verstehen, OsmAnd muss ihn
annehmen. Geht es nicht, weiss man ohne diesen Weg nicht, welches der drei
schuld ist. Der Prüfstand überspringt das erste: was hier klappt und draussen
nicht, ist eine Sache der Link-Freigabe in den Android-Einstellungen.

### Kartenlinks nach OsmAnd

Ein Google-Maps-Link öffnet OsmAnd mit gesetztem Ziel. Die App zerlegt dabei
ein halbes Dutzend Linkformen — `dir/?destination=`, `search/?query=`,
`place/Name/@lat,lon`, das alte `daddr=`, und `geo:`.

**Die Koordinate geht vor — und das war einmal andersherum.** Die Überlegung
war: der Name trifft den Eingang, das `@lat,lon` in einem Google-Link ist nur
die Bildmitte. Das stimmt, aber **OsmAnds Suche ist offline** und findet nur,
was in der geladenen Karte steht und dort auch so heisst. Im Versuch fand sie
eine gewöhnliche Adresse nicht — und ein Ziel, das nicht ankommt, ist
schlechter als eines, das zwanzig Meter danebenliegt.

**Bleibt nur ein Name, wird er erst in eine Koordinate umgesetzt**, bevor
OsmAnd überhaupt gefragt wird. Zwei Wege, in dieser Reihenfolge:

1. **Androids eigener `Geocoder`** — kostet nichts, verlässt das Telefon
   womöglich gar nicht. Er braucht aber einen Dienst im Hintergrund, und den
   hat nicht jedes Gerät; ohne Google-Dienste fehlt er.
2. **Nominatim**, der Suchdienst von OpenStreetMap. Dieselbe Datengrundlage,
   aus der OsmAnds Karten stammen — was er findet, liegt also auch dort, wo
   OsmAnd hinfährt.

OsmAnds eigene Suche ist damit der **letzte** Ausweg, nicht der erste.

> **Was dabei das Telefon verlässt:** im zweiten Fall die Adresse, an einen
> fremden Rechner. Nur für Links **ohne** Koordinate, und nur wenn gerade
> jemand einen angetippt hat. Steht eine Koordinate im Link, wird niemand
> gefragt.

Nach erfolgreicher Übergabe wird OsmAnd **nach vorne geholt**. Die Führung
läuft sonst im Hintergrund, und man sucht die Karte selbst.

**Kurzlinks** (`maps.app.goo.gl`) werden erst aufgelöst — nur der Kopf der
Antwort, höchstens fünf Sprünge. Eine Google-Maps-Seite ist ein Megabyte
JavaScript, und gesucht ist nur das Ziel der Umleitung.

**Übergeben wird per Intent, nicht über die AIDL-Schnittstelle.** Deren
`navigate()` lieferte `true` und OsmAnd tat nichts: der Aufruf kommt an,
solange der Dienst gebunden ist — ob die Karte dahinter ihn *ausführen* kann,
sagt die Rückgabe nicht. Bei einer App, die gerade erst startet, verfällt er
still.

Ein Intent hat diese Lücke nicht: er startet OsmAnd **mit** dem Ziel, und wenn
niemand ihn annimmt, fliegt eine Ausnahme, die man sieht. Er braucht ausserdem
keine Freischaltung unter *Plugins* — für Kartenlinks fällt diese Hürde damit
ganz weg. Zwei Formen, in dieser Reihenfolge:

1. `geo:lat,lon?q=…` — **die Vorgabe**: setzt den Punkt auf die Karte und
   überlässt den Start dem Menschen. Ohne Koordinate wird daraus
   `geo:0,0?q=<Name>`, und OsmAnd sucht selbst.
2. `osmand.api://navigate?dest_lat=…&force=true` — startet die Führung sofort.
   Nur, wenn es in den Einstellungen so gewählt ist.

**Zeigen ist die Vorgabe, und das ist eine Haltung.** Eine Führung, die von
selbst anspringt, nimmt eine Entscheidung vorweg: welche Route, welches
Profil, und überhaupt — ob jetzt gefahren wird. Wer auf einen Link tippt, will
meistens erst sehen, wo das ist; der Weg dahin ist danach ein Tipp entfernt.
Umstellen lässt es sich in den Einstellungen unter *Kartenlinks*.

**Zwei Intent-Filter, nicht einer** — und das ist kein Schönheitsfehler. Alle
`<data>`-Zeilen *innerhalb* eines Filters verschmilzt Android zu **einer**
Bedingung: jedes Schema mal jeden Host mal jeden Pfad. Stand irgendwo ein
`pathPrefix`, galt er damit für alle Hosts des Filters.

Genau das war hier der Fall: `/maps` stand wegen `www.google.com` im selben
Filter wie `maps.app.goo.gl` — und ein Kurzlink hat als Pfad `/abc123`. Er
passte auf keine Bedingung und kam **nie** an. Getrennt nach »mit Pfad« und
»ohne Pfad« stimmt beides.

**Android gibt diese Links nicht von selbst her.** Seit Android 12 muss eine
App den Besitz einer Adresse nachweisen, um sie zu beanspruchen, und für
google.com kann das niemand ausser Google. Einmal von Hand erlauben:

> Einstellungen → Apps → Kiesel-Helper → Standardmässig öffnen → Links
> hinzufügen → Haken bei den Google-Maps-Adressen

Das ist richtig so: eine App, die sich unbemerkt vor fremde Links setzen
könnte, wäre ein Angriffswerkzeug. `geo:` gehört dagegen niemandem und
funktioniert sofort.

Die Zerlegung ist **reine Zeichenkettenarbeit ohne Android** und damit ohne
Telefon prüfbar — 11 Prüfungen. Der erste Entwurf nahm `android.net.Uri`; die
ist im Test eine Attrappe, die null zurückgibt. Eine der Prüfungen hat prompt
einen Fehler gefunden: ein `q=` gibt es auch in einer gewöhnlichen
Google-Suche, und daraus ein Navigationsziel zu machen wäre eine Anmassung.

**Der Empfang läuft auch nach einem Update weiter.** Android beendet beim
Ersetzen des Pakets den Prozess und startet nichts von selbst neu; bis jemand
die App öffnet, hörte niemand der Uhr zu. So ging ein Training verloren:
abends aktualisiert, morgens gefahren. Seit 0.35.0 fährt `MY_PACKAGE_REPLACED`
den Dienst wieder hoch, wie `BOOT_COMPLETED` nach einem Neustart.

**Ein Fehlschlag beim Eintragen gibt den Riegel wieder frei.** Der Riegel gegen
Doppelte fiel bisher vor dem Schreiben und blieb auch dann zu, wenn die Akte
den Satz nicht annahm — der nächste Versuch der Uhr lief ins Leere, die Messung
war weg.

## Was sie einträgt

Drei feste Aufgaben, **im Code**, nicht in einer Datei aus dem Netz:

| Von | Nach | Was |
|---|---|---|
| Drinktervall | Gesundheitsakte | jedes getrunkene Glas als Wassermenge, mit dem Zeitpunkt von der Uhr |
| Herzintervall | Gesundheitsakte | die nächtliche RMSSD-Messung als Herzratenvariabilität |
| Kieselsport | Gesundheitsakte | ein beendetes Training als Trainingssitzung — **nur die Sitzung**, nicht die Zahlen darin; die Strecke kommt vom Telefon dazu |
| Kieselsport | Trainings-Reiter | dieselben Sitzungen, aus der Akte zurückgelesen, mit Strecke auf der Karte |
| Kieselsport | Gesundheitsakte | jeder **Satz** mit seinen Wiederholungen, jede **Pause** dazwischen, jede **Bahn** mit ihrer Länge |
| SupCycle | Gesundheitsakte + eigener Speicher | jedes neu abgehakte Präparat als Ernährungssatz mit Namen (seit 0.35.0 wirklich — davor stand es nur hier); die Quote bleibt für den Trend lokal |
| OsmAnd | Kieselstrasse | Abbiegeart, Entfernung, Strasse, Ankunftszeit |

**SupCycle geht als Ernährungssatz in die Akte** — mit Namen, ohne Mengen. Der
Plan dort besteht aus Namen und Zyklen, nicht aus Milligramm; eine Menge zu
erfinden, damit das Feld gefüllt ist, wäre der schlechteste aller Wege. Die
Quote »3 von 5« bleibt daneben im eigenen Speicher, weil der Trend sie braucht.

Geschickt wird nichts Neues: SupCycle meldet Tag, Fälligkeits- und
Abhak-Bitmaske ohnehin nach jeder Einnahme, für seine eigenen Timeline-Pins.
Gezählt wird, was **fällig war** und davon genommen wurde — ein Präparat, das
heute pausiert, gehört in keine Quote.

**Sieben Sportarten, zwei davon Bike.** Kieselsport unterscheidet seit 0.4.0
Strasse/Gravel und MTB; dazu kamen Yoga und Schwimmen. Die Gesundheitsakte kennt diese
Unterscheidung nicht — sie hat ein einziges Radfahren und kein Mountainbike.
Beide gehen deshalb als Radfahren hinein, und der **Titel** trägt, was es
war: »Bike Strasse/Gravel«, »Bike MTB«. Ihn wegzulassen hiesse, eine Ausfahrt
im Wald und eine auf der Landstrasse in einen Topf zu werfen.

Strasse und Gravel stehen zusammen: sie unterscheiden sich im Reifen, nicht in
dem, was die Uhr davon sieht.

Die Zahl einer Art darf sich dabei **nie verschieben**: sie ist alles, was von
der Uhr kommt. MTB steht deshalb hinter Kraft und nicht neben Strasse/Gravel,
wo es hingehörte — eine eingeschobene Zeile hätte jede gespeicherte
Aufzeichnung um eine Art verschoben.

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

## Die Strecke kommt vom Telefon

Die Uhr hat **kein GPS**. Jede Pebble hat keins, auch die Time 2 nicht — was
sie kann, ist zählen: Schritte, Puls, Kalorien. Wo jemand gelaufen ist, weiss
sie nicht und kann es nicht wissen.

Das Telefon weiss es. Es liegt ohnehin in der Tasche, und dieser Weg ist der
Weg, den auch grosse Hersteller gehen, wenn eine Uhr keinen eigenen Empfänger
hat: die Uhr misst den Körper, das Telefon misst den Ort, am Ende liegt beides
übereinander.

**Der Dienst läuft nur, solange ein Training läuft.** Das ist der ganze
Unterschied zu einer App, die dauernd den Standort kennt. Kieselsport meldet
den Start — der Dienst geht an; es meldet das Ende — der Dienst geht aus.
Dazwischen liegt genau die Zeit, für die jemand eine Strecke sehen will.

| Auf der Uhr | Auf dem Telefon |
|---|---|
| Start gedrückt | Aufzeichnung an, Meldung in der Leiste |
| Pause | Aufzeichnung aus |
| Weiter | Aufzeichnung an, dieselbe Datei |
| Stop | Aufzeichnung aus, Strecke an die Trainingssitzung |

**Mit sichtbarer Meldung**, und das ist keine Formalie: Android verlangt sie
für Ortung im Hintergrund, und wer den Standort eines Menschen aufzeichnet,
soll das nicht lautlos tun können.

**Die Erlaubnis wird nicht beim ersten Start erfragt**, sondern in den
Einstellungen unter »Training«. Wer die App öffnet, um seinen Schlaf zu sehen,
soll nicht nach seinem Standort gefragt werden; gefragt wird, wer eine Strecke
will. Ohne sie läuft alles andere weiter — das Training wird eingetragen, nur
ohne Karte.

**Nötig ist »Immer erlauben«**, und das war eine unangenehme Einsicht. Der
erste Entwurf verzichtete bewusst auf `ACCESS_BACKGROUND_LOCATION` — ein
Vordergrunddienst mit sichtbarer Meldung schien zu genügen. Er genügt nicht:
das Training beginnt **auf der Uhr**, während das Telefon in der Tasche liegt
und diese App zu ist. In diesem Zustand lässt Android einen Ortungsdienst ohne
das Hintergrundrecht gar nicht erst zu. Die Strecke bliebe leer, und niemand
wüsste warum.

Gemessen wird trotzdem nur zwischen Start und Stop. Das Recht erlaubt die
Ortung bei geschlossener App — es schaltet sie nicht ein.

Das Hintergrundrecht gibt es seit Android 11 **nicht als Dialog**. »Immer
erlauben« steht nur in den Systemeinstellungen; die App kann dorthin nur den
Weg zeigen, und der Knopf in der Karte tut genau das.

**Der Dienst darf nie abstürzen.** Er wird aus dem Nichts gestartet, oft bei
dunklem Schirm. Die erste Fassung prüfte die Berechtigung erst *nach*
`startForeground` — zu spät: Android prüft sie seit 14 **innerhalb** dieses
Aufrufs und wirft, statt Nein zu sagen. Der erste Start ohne erteilte Erlaubnis
riss deshalb die ganze App mit. Jetzt wird vorher gefragt, an beiden Enden
(bevor der Dienst überhaupt startet und noch einmal im Dienst selbst), und
alles Übrige wird gefangen und in den Verlauf geschrieben — wer eine leere
Karte sieht, soll nachlesen können, warum.

**Kein Google-Standortdienst.** Der `LocationManager` des Systems genügt,
kostet keine Abhängigkeit und läuft auch auf einem Telefon ohne Play-Dienste.
Gemessen wird alle drei Sekunden oder alle fünf Meter; dichter macht die Linie
nicht genauer, nur die Datei grösser und den Akku leerer.

### Was aussortiert wird

Drei Regeln, und jede steht für einen Fehler, den man auf der Karte sieht:

* **Über 50 Meter gemeldete Unsicherheit** — dieser Punkt kommt aus dem
  Mobilfunknetz, nicht von Satelliten. Ihn mitzunehmen hiesse, Sprünge quer
  durch die Stadt zu zeichnen.
* **Über 200 Meter zwischen zwei Punkten** — verlorener und wiedergefundener
  Empfang. Kein Mensch legt das in drei Sekunden zurück; der Abschnitt zählt
  nicht in die Länge.
* **Unter 3 Metern Höhenunterschied** — GPS-Höhen schwanken im Stehen um
  mehrere Meter. Ohne diese Schwelle sammelte ein Spaziergang in der Ebene
  hundert Höhenmeter.

Die Rechnung dahinter ist **reine Mathematik ohne Android** — Haversine statt
`Location.distanceTo` — und damit ohne Telefon prüfbar: 10 Prüfungen, darunter
der bekannte Meridianbogen von 111,19 km je Breitengrad. Die Zahlen unter einer
Karte rechnet nämlich niemand nach; steht da »8,2 km«, glaubt man es.

### Als Datei, nicht in der Tabelle

Eine Stunde Laufen sind bei einem Punkt alle drei Sekunden gut tausend Zeilen.
Die Tagestabelle trägt je Tag **eine**. Jede Strecke liegt deshalb als eigene
Datei unter `spuren/spur-<beginn>.jsonl`, eine Zeile je Punkt — und wird
**sofort** geschrieben, nicht am Ende: ein Dienst, den das System während eines
Laufs beendet, nähme sonst die ganze Strecke mit.

Aus derselben Datei entsteht auf Wunsch **GPX**, das Format, das jede
Karten-App liest.

### Die Karte gehört aufs Telefon

Sie war zwischendurch für die Uhr gedacht. Kartenkacheln, Zoom und
Speicherverwaltung auf 128 KB RAM wären ein eigenes Projekt — und auf 200×228
Punkten sähe man nichts, was man nicht hier besser sieht. Die Uhr misst, das
Telefon zeigt.

»Trainings ansehen« öffnet die Liste: das jüngste gross mit Strecke, Tempo,
Aufstieg und Karte, die davor als Zeilen. Was man sucht, wenn man diesen Schirm
öffnet, ist fast immer das letzte Training.

Die Liste kommt **aus der Gesundheitsakte**, nicht aus einer eigenen Tabelle.
Dort stehen die Sitzungen ohnehin; eine zweite Liste daneben wäre eine zweite
Wahrheit. Gezeichnet wird mit **OpenStreetMap** (osmdroid): kein Schlüssel,
keine Play-Dienste — und dieselbe Datengrundlage, aus der auch OsmAnd seine
Karten baut.

Die Strecke geht ausserdem als `ExerciseRoute` **mit in die Gesundheitsakte**.
Damit sieht sie auch, wer dort nachschaut, und sie hängt an derselben Sitzung
wie Puls und Dauer.

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

**Eine neue Fassung auf `main` legt das Release an**, samt Tag und APK. Steht
in `app/build.gradle.kts` eine `versionName`, zu der es noch kein Release gibt,
entsteht es im selben Lauf. Den Text nimmt es aus `.github/release/<fassung>.md`
(erste Zeile Titel), sonst aus dem Commit.

**Jede APK trägt dieselbe Unterschrift.** Der Debug-Schlüssel liegt im Repo
(`app/debug.keystore`, Passwort `android`). Ohne ihn legte jeder Lauf einen
eigenen an, und Android installiert eine anders signierte Fassung nicht über
die alte — nur nach dem Deinstallieren, und das löscht den eigenen Speicher.

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
* **Die Strecke ist so gut wie der Empfang.** Im Wald, zwischen Häusern und
  mit dem Telefon in der Gesässtasche wird die Linie eckig; ein Tunnel
  hinterlässt eine Lücke. Aussortiert wird nur, was offensichtlich falsch ist,
  nicht, was ungenau ist.
* **Ohne die Startmeldung der Uhr keine Strecke.** Kieselsport meldet den Start
  erst ab 0.2.0. Mit einer älteren Fassung kommt das Training an, die Strecke
  nicht — das Telefon erführe erst am Ende davon und hätte nichts
  aufgezeichnet.
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
