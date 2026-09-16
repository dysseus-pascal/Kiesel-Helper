# Misst, was in den Benachrichtigungen einer App wirklich steht.
#
# WOZU: ein Zettel fuer eine Benachrichtigungs-Quelle muss wissen, ob die
# Angabe in `titel`, `text` oder einem `extra:` steckt. Raten kostet Stunden;
# messen kostet eine Minute.
#
# Aufruf:  tools\felder_messen.ps1 com.google.android.apps.maps
#
# Das Telefon muss per adb erreichbar sein, und die App muss gerade eine
# Benachrichtigung zeigen - bei einer Karten-App heisst das: Navigation
# laeuft.
param(
  [Parameter(Mandatory = $true)][string]$Paket
)

$roh = adb shell dumpsys notification --noredact
if (-not $roh) { Write-Output "Kein Telefon an adb."; exit 1 }

$zeilen = $roh -split "`n"
$gefunden = $false

for ($i = 0; $i -lt $zeilen.Length; $i++) {
  if ($zeilen[$i] -notmatch "NotificationRecord\(.*pkg=$([regex]::Escape($Paket))") { continue }
  $gefunden = $true
  Write-Output "=== Benachrichtigung von $Paket ==="

  # Die Extras stehen als "android.title=..." im Block nach dem Kopf. Der
  # Block endet beim naechsten NotificationRecord.
  for ($j = $i + 1; $j -lt $zeilen.Length; $j++) {
    if ($zeilen[$j] -match "NotificationRecord\(") { break }
    $z = $zeilen[$j].Trim()
    if ($z -match "^android\.(title|text|subText|bigText|infoText|summaryText|progress|when)" -or
        $z -match "^extras=" -or $z -match "^tickerText") {
      Write-Output "  $z"
    }
  }
  Write-Output ""
}

if (-not $gefunden) {
  Write-Output "Keine aktive Benachrichtigung von $Paket."
  Write-Output "Bei einer Karten-App: Navigation starten, dann noch einmal messen."
}
