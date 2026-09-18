# Misst, was in den Benachrichtigungen einer App wirklich steht.
#
# WOZU: ein Zettel fuer eine Benachrichtigungs-Quelle muss wissen, ob die
# Angabe in `titel`, `text` oder einem `extra:` steckt. Raten kostet Stunden;
# messen kostet eine Minute. Bei Google Maps hat genau diese Messung den
# Entwurf widerlegt - dort gibt es gar keine Entfernung zur Abzweigung.
#
# Aufruf:
#   tools\felder_messen.ps1 com.google.android.apps.maps
#   tools\felder_messen.ps1 net.osmand.plus -Wiederholen 6 -Abstand 10
#
# Das Telefon muss per adb erreichbar sein, und die App muss gerade eine
# Benachrichtigung zeigen - bei einer Karten-App heisst das: Navigation laeuft.
param(
  [Parameter(Mandatory = $true)][string]$Paket,
  # Mehrfach messen zeigt, WELCHE Felder sich aendern - und das ist die
  # eigentliche Frage. Ein Feld, das stehen bleibt, taugt als Anweisung; eines,
  # das mitlaeuft, ist die Entfernung.
  [int]$Wiederholen = 1,
  [int]$Abstand = 10
)

function Hole-Block([string]$p) {
  $roh = adb shell dumpsys notification --noredact
  if (-not $roh) { return $null }
  $z = $roh -split "`n"
  for ($i = 0; $i -lt $z.Length; $i++) {
    if ($z[$i] -notmatch "NotificationRecord\(.*pkg=$([regex]::Escape($p))") { continue }
    $aus = @()
    $inExtras = $false
    for ($j = $i + 1; $j -lt $z.Length; $j++) {
      if ($z[$j] -match "NotificationRecord\(") { break }
      $t = $z[$j].Trim()
      if ($t -match "^extras=\{") { $inExtras = $true; continue }
      if ($inExtras) {
        if ($t -eq "}") { $inExtras = $false; continue }
        # Bilder und Absender sind fuer einen Zettel unerreichbar - sie
        # wuerden die Liste nur zumuellen.
        if ($t -match "^android\.(largeIcon|smallIcon|picture|people|messages|progressTrackerIcon|appInfo)") { continue }
        $aus += $t
      }
      if ($t -match "^tickerText=" -and $t -notmatch "null") { $aus += $t }
    }
    return $aus
  }
  return @()
}

for ($n = 1; $n -le $Wiederholen; $n++) {
  $b = Hole-Block $Paket
  if ($null -eq $b) { Write-Output "Kein Telefon an adb."; exit 1 }
  Write-Output ("=== {0}  ({1}. Messung, {2}) ===" -f $Paket, $n, (Get-Date -Format "HH:mm:ss"))
  if ($b.Count -eq 0) {
    Write-Output "  Keine aktive Benachrichtigung."
    Write-Output "  Bei einer Karten-App: Navigation starten, dann noch einmal messen."
  } else {
    $b | ForEach-Object { Write-Output "  $_" }
  }
  Write-Output ""
  if ($n -lt $Wiederholen) { Start-Sleep -Seconds $Abstand }
}
