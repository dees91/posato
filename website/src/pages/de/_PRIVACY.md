# Datenschutzerklärung von Posato

Gültig ab 22. Oktober 2026.

Posato sendet deine Pausensets, deine Website-Liste, deine App-Auswahl,
Sitzungen, Zeitpläne oder Daten des Arbeitsbereichs nicht an seinen
Entwickler. Es gibt keinen Posato-Account, keine Analyse, keine Werbung und
keinen Server, der von Posato betrieben wird. Diese Erklärung gilt für Posato
für Mac, iPhone, Linux und Android. Die optionale Synchronisierung über iCloud
oder einen Ordner, das Blockieren auf jedem Gerät und die Update-Anfragen unter
macOS sind unten beschrieben.

## Daten, die Posato auf deinen Geräten speichert

Damit es funktioniert, speichert Posato auf jedem Gerät:

- deine Pausensets: ihre Namen, welches Set der Standard ist, welche
  Website-Domains jedes enthält und welche Apps du für jedes auf diesem Gerät
  ausgewählt hast, auf dem iPhone gespeichert als vom System bereitgestellte
  Auswahl, die Posato nicht als App-Namen lesen kann; auf Mac, Linux und
  Android speichert Posato, um welche Apps es sich handelt;
- deine Sitzungen: wann sie beginnen und enden, welches Pausenset jede
  verwendet hat und ob sie vorzeitig beendet wurden;
- deine Zeitpläne: ihre Namen, ihre Pausensets, die Wochentage und Uhrzeiten,
  an denen sie sich wiederholen, ob jeder eingeschaltet ist und welche
  geplanten Pausen du übersprungen oder vorzeitig beendet hast;
- Einstellungen, etwa ob die Synchronisierung eingeschaltet ist, welchen
  Ordner du für die Ordner-Synchronisierung gewählt hast und ob das
  Hilfsprogramm unter macOS aktiviert ist;
- bei eingeschalteter Synchronisierung, wann dieses Gerät zuletzt eine
  Synchronisierung abgeschlossen hat.

Diese Daten bleiben im privaten Speicher von Posato auf dem Gerät, bis du sie
änderst oder entfernst. Auf Mac, iPhone und Linux können diese Daten, mit
Ausnahme der App-Auswahl auf dem iPhone, je nach deinen Backup-Einstellungen
auch in Backups deiner Geräte enthalten sein, etwa in iCloud-Backup, Time
Machine oder einem Backup deines Linux-Benutzerordners. Unter Android schließt
Posato seine Daten von Android-Backups und Geräteübertragungen aus. Auf dem
Mac löscht das Bewegen von Posato in den Papierkorb diese Daten nicht; sie
bleiben im Ordner für App-Daten von Posato, bis du sie entfernst. Unter Linux
löscht auch das Entfernen des Pakets sie nicht; sie bleiben im Ordner von
Posato innerhalb der App-Daten in deinem Benutzerordner, bis du sie entfernst.

Bei eingeschalteter Synchronisierung, über iCloud oder einen Ordner, speichert
Posato außerdem den Verlauf der synchronisierten Änderungen in seinem privaten
Speicher auf jedem verbundenen Gerät: jedes erstellte, umbenannte oder
gelöschte Pausenset, jede Website, die einem Set hinzugefügt oder daraus
entfernt wurde, welches Set der Standard ist und welches Set jede Sitzung und
jeder Zeitplan verwendet hat, und wann jede Änderung vorgenommen wurde,
einschließlich wann vergangene Sitzungen begonnen haben, wann sie enden sollten
und ob sie vorzeitig beendet wurden. Einträge aus der Zeit vor Posato 1.3
erfassen außerdem Änderungen am Namen der geteilten App-Gruppe, die es damals
verwendet hat. Das Ändern oder Entfernen einer Website oder eines Sets löscht
deren frühere Einträge nicht aus diesem Verlauf. Der Verlauf bleibt erhalten,
bis **Arbeitsbereich entfernen** auf diesem Gerät abgeschlossen ist oder die
Daten von Posato von dem Gerät entfernt werden, und er kann wie die oben
genannten Daten in Backups des Geräts enthalten sein.

Posato erfasst keinen Browserverlauf, keine besuchten Seiten, keine Angaben
dazu, welche Seiten blockiert wurden oder wie oft du Apps öffnest, keine
Nutzungswerte und keine sonstigen Aktivitätsprotokolle.

## Synchronisierung mit iCloud

Die Synchronisierung mit iCloud ist optional und ausgeschaltet, bis du
**Mit iCloud synchronisieren** wählst.

- Deine Pausensets (ihre Namen, ihre Websites und welches der Standard ist),
  in Änderungen aus der Zeit vor Posato 1.3 der Name der geteilten App-Gruppe,
  Beginn und Ende von Sitzungen sowie deine Zeitpläne, einschließlich
  übersprungener und beendeter geplanter Pausen, werden auf deinem Gerät
  verschlüsselt, bevor sie in der privaten CloudKit-Datenbank deines eigenen
  iCloud-Accounts gespeichert werden. Der Entwickler von Posato kann sie weder
  lesen noch darauf zugreifen.
- Jede Änderung und die Registrierung jedes Geräts werden als separater
  verschlüsselter Datensatz gespeichert, und diese Datensätze sammeln sich in
  deiner iCloud-Datenbank, bis du **Arbeitsbereich entfernen** wählst; Posato
  räumt sie nicht automatisch auf.
- Der Schlüssel für die Verschlüsselung wird zwischen deinen Geräten über den
  iCloud-Schlüsselbund geteilt.
- Die App-Auswahl wird nie synchronisiert; jedes Gerät behält seine eigene.
- Apple betreibt iCloud und kann technische Informationen sehen, die für den
  Betrieb nötig sind, etwa die Zuordnung zu deinem Account, Zeitpunkte, Größe
  und Anzahl der Datensätze, gemäß Apples eigener Datenschutzrichtlinie.

**Arbeitsbereich entfernen** löscht die synchronisierten Datensätze von
Posato aus deiner iCloud-Datenbank, seinen Verlauf der synchronisierten
Änderungen auf dem Gerät, auf dem du ihn entfernst, und den Schlüssel des
Arbeitsbereichs aus dem iCloud-Schlüsselbund, sodass auch deine anderen Geräte
den Zugriff auf den Arbeitsbereich verlieren; ihre eigenen Kopien deiner
Pausensets, Sitzungen und des Änderungsverlaufs bleiben auf diesen Geräten, bis
du den Arbeitsbereich dort entfernst. Pausensets und Websites, die auf dem
Gerät gesichert sind, auf dem du den Arbeitsbereich entfernst, bleiben auf
diesem Gerät. Apple kann Backups gemäß seinen eigenen Richtlinien für einen
gewissen Zeitraum aufbewahren. Gehen alle Kopien des Schlüssels verloren,
lassen sich synchronisierte Daten nicht wiederherstellen.

## Ordner-Synchronisierung

Die Ordner-Synchronisierung ist optional und ausgeschaltet, bis du in Posato
einen Ordner wählst. Unter Linux und Android ist sie die einzige
Synchronisierung, und auf Mac und iPhone ersetzt sie für diesen Arbeitsbereich
die Synchronisierung mit iCloud.

- Posato legt in dem Ordner, den du wählst, einen Ordner `Posato` an. Er
  enthält eine Datei mit zufälligen Kennungen des Arbeitsbereichs und für jede
  Änderung eine separate Datei, die auf deinem Gerät mit derselben
  Verschlüsselung wie bei der Synchronisierung mit iCloud verschlüsselt wird:
  deine Pausensets (ihre Namen, ihre Websites und welches der Standard ist),
  Beginn und Ende von Sitzungen sowie deine Zeitpläne, einschließlich
  übersprungener und beendeter geplanter Pausen. Die App-Auswahl wird nie
  synchronisiert; jedes Gerät behält seine eigene.
- Diese Dateien sammeln sich im Ordner, bis du **Arbeitsbereich entfernen**
  wählst, wodurch der Ordner von Posato darin gelöscht wird. Posato räumt sie
  nicht automatisch auf.
- Posato lädt selbst nichts hoch. Der Dienst, der den Ordner synchron hält,
  etwa Dropbox, OneDrive, iCloud Drive oder Syncthing, kopiert die Dateien
  gemäß seiner eigenen Datenschutzrichtlinie. Er kann ihren Inhalt nicht lesen,
  aber technische Informationen sehen, etwa Dateinamen, die zufällig sind,
  Dateigrößen, Anzahl und wann sich Dateien ändern, und er kann frühere
  Versionen oder gelöschte Dateien eine Zeit lang aufbewahren.
- Bei der Ordner-Synchronisierung bewahrt jedes Gerät den Schlüssel des
  Arbeitsbereichs in einer Datei im privaten Speicher von Posato auf, die nur
  dein Benutzeraccount lesen kann, nicht im iCloud-Schlüsselbund. Unter Android
  ist die Datei zusätzlich mit einem Schlüssel versiegelt, der den Android
  Keystore dieses Smartphones nie verlässt. Auf Mac, iPhone und Linux kann die
  Schlüsseldatei wie die oben genannten Daten in Backups des Geräts enthalten
  sein.
- Um ein Gerät hinzuzufügen, zeigt Posato auf einem Gerät im Arbeitsbereich
  einen einmaligen Kopplungscode an, auch als QR-Code und als `posato://`-Link,
  der nur diesen Code enthält. Es legt den Schlüssel des Arbeitsbereichs in
  den Ordner, verschlüsselt mit einem Schlüssel, der aus dem Code abgeleitet
  ist. Es löscht diese Datei, wenn das neue Gerät beigetreten ist, wenn du den
  Code schließt oder, nachdem der Code nach 10 Minuten abgelaufen ist, beim
  nächsten Start von Posato oder wenn es einen neuen Code anzeigt. Posato zeigt
  den QR-Code an, verwendet aber nicht deine Kamera. Wenn du den Link per
  Nachricht, E-Mail oder über die Zwischenablage teilst, läuft er über die App,
  die du wählst. Jeder, der den Code oder den Link hat und den Ordner oder eine
  Kopie dieser Datei in seinem Dateiverlauf öffnen kann, kann den Schlüssel des
  Arbeitsbereichs wiederherstellen, auch nachdem der Code abgelaufen ist, teile
  sie also nur mit deinen eigenen Geräten.

**Arbeitsbereich entfernen** löscht den Ordner von Posato aus dem Ordner, den
du gewählt hast, falls dieses Gerät ihn noch erreichen kann, sowie die
Schlüsseldatei des Arbeitsbereichs und den Verlauf der synchronisierten
Änderungen auf dem Gerät, auf dem du ihn entfernst. Andere Geräte behalten ihre
eigenen Kopien deiner Pausensets, Sitzungen, des Änderungsverlaufs und der
Schlüsseldatei, bis du den Arbeitsbereich dort entfernst. Der
Synchronisierungsdienst kann frühere Versionen der Dateien gemäß seiner eigenen
Richtlinie aufbewahren. Gehen alle Kopien des Schlüssels verloren, lassen sich
synchronisierte Daten nicht wiederherstellen.

## Bildschirmzeit auf dem iPhone

Auf dem iPhone verwendet Posato das Bildschirmzeit-Framework von Apple, um
die Websites und Apps zu blockieren, die du auswählst. Deine App-Auswahl
besteht aus undurchsichtigen Systemauswahlen, die auf dem Gerät bleiben;
Posato erhält von Bildschirmzeit keine App-Namen und keine Nutzungsdaten.
Damit ein Zeitplan starten kann, während Posato geschlossen ist, bewahrt
Posato eine Kopie deiner Zeitpläne sowie der Websites und App-Auswahl jedes
Pausensets in einem Speicher auf, den es nur mit seiner eigenen
Bildschirmzeit-Erweiterung auf demselben iPhone teilt. Diese Kopie verlässt
das Gerät nie, ist von Backups des Geräts ausgeschlossen und wird gelöscht,
wenn die Daten von Posato oder der Arbeitsbereich entfernt werden.

## Blockieren auf dem Mac

Auf dem Mac blockiert das Hilfsprogramm von Posato im Hintergrund ausgewählte
Websites, indem es den Browserverkehr, während eine Sitzung aktiv ist, über
einen lokalen Proxy auf deinem Mac leitet. Das Hilfsprogramm prüft jede Anfrage
nur so lange, bis es sie erlaubt oder blockiert, und speichert oder sendet sie
nicht. Um seine Pausenseite im aktuellen Tab von Safari oder Chrome
anzuzeigen, kann Posato die Berechtigung „Automation“ verwenden; dabei liest es
nur die Adresse des aktuellen Tabs in diesem Moment und speichert nichts
davon. Posato entschlüsselt keinen HTTPS-Verkehr.

Wenn du festlegst, dass Sitzungen auf einem Mac ohne Administratorpasswort
starten dürfen, speichert das Hilfsprogramm einen Eintrag über diese Erlaubnis
in einer Systemdatei, die nur das System lesen kann. Der Eintrag enthält die
Benutzernummer und die Systemkennung deines Mac-Benutzeraccounts sowie die
Hardwarekennung deines Mac. Er verlässt deinen Mac nie und wird gelöscht, wenn
du die Option ausschaltest oder Posato von diesem Mac entfernst.

## Blockieren unter Linux

Unter Linux installiert Posato einmalig, mit deinem Passwort, einen
Pausendienst, der mit Systemrechten läuft. Nur dein Benutzeraccount und das
System können ihm Anfragen senden. Während eine Pause läuft, macht der Dienst
Folgendes:

- Er trägt jede pausierte Website und ihren `www`-Namen in die Systemdatei
  `/etc/hosts` ein, die andere Benutzeraccounts auf dem Computer lesen können,
  und entfernt sie wieder, wenn die Pause endet.
- Er beendet die pausierten Programme, die du ausgewählt hast und die unter
  deinem Benutzeraccount laufen; dabei prüft er, welches Programm jeder
  laufende Prozess ist, ohne zu lesen oder zu speichern, was du darin tust.

Der Dienst speichert deinen Benutzernamen und deine Benutzernummer in einer
Konfigurationsdatei des Systems und die Pause, die er durchsetzt, mit ihren
Websites und Programmpfaden, in einer Systemdatei, die nur das System lesen
kann, damit die Pause einen Neustart übersteht. Nach dem Ende der Pause
enthält diese Datei nur noch die Kennung der Sitzung, bis Posato sie liest. Das
Entfernen des Pakets entfernt den Dienst, stellt `/etc/hosts` wieder her und
löscht beide Dateien. Nichts, was der Dienst verarbeitet, verlässt deinen
Computer.

## Blockieren unter Android

Unter Android braucht Posato mehrere Berechtigungen, die jeweils in den
Einstellungen von Android erteilt werden:

- **VPN.** Während eine Pause mit Websites läuft, betreibt Posato ein lokales
  VPN, das nur Namensabfragen überträgt, also die Fragen, mit denen dein
  Smartphone die Adresse einer Website ermittelt. Pausierte Namen beantwortet
  es selbst, und jede andere Abfrage leitet es an den DNS-Server des Netzwerks
  weiter, in dem du dich befindest, oder an Google Public DNS, wenn dieses
  Netzwerk keinen anbietet, den Posato verwenden kann. Kein anderer Verkehr
  gelangt in das VPN, und Posato zeichnet Abfragen weder auf noch speichert es
  sie.
- **Zugriff auf Nutzungsdaten.** Während eine Pause mit Apps läuft, prüft
  Posato etwa zweimal pro Sekunde, welche App im Vordergrund ist, damit es eine
  pausierte App mit seinem Sperrbildschirm verdecken kann. Es behält nur die
  App, die in diesem Moment im Vordergrund ist, im Arbeitsspeicher und
  zeichnet die App-Nutzung nicht auf.
- **Über anderen Apps einblenden**, um den Sperrbildschirm anzuzeigen.
- **Benachrichtigungen, genaue Wecker und Ausführung ohne
  Akkubeschränkungen**, damit Pausen und Zeitpläne pünktlich beginnen und
  enden, während Posato geschlossen ist. Eine lautlose Benachrichtigung wird
  angezeigt, solange Posato sie am Laufen hält.
- **Zugriff auf alle Dateien**, damit Posato den Ordner lesen und beschreiben
  kann, den du für die Ordner-Synchronisierung wählst. Posato liest und
  beschreibt nur diesen Ordner.

Posato liest die Liste der Apps, die du über den Launcher öffnen kannst, damit
du Apps zum Pausieren auswählen kannst; die Liste bleibt auf dem Smartphone.
Die Pause, die es durchsetzt, mit ihren Websites und den Paketnamen der Apps,
wird bis zum Ende der Pause im privaten Speicher von Posato aufbewahrt, damit
sie einen Neustart übersteht.

## Export und Import

Du kannst deine Pausensets in eine Datei exportieren und sie auf einem anderen
Gerät importieren. Die Datei enthält die Namen und Websites deiner Pausensets,
welches Set der Standard ist, und deine Zeitpläne. Sie enthält nie die
App-Auswahl, Sitzungen, Verläufe, Gerätenamen oder Synchronisierungsschlüssel.
Posato schreibt die Datei nur dorthin, wo du sie sicherst oder teilst, und
sendet selbst nichts irgendwohin.

- Ohne Code kann jeder, der die Datei bekommt, deine Pausensets und Websites
  lesen.
- Mit **Mit Code schützen** wird die Datei mit einem Schlüssel verschlüsselt,
  der aus einem neunstelligen Code abgeleitet ist, den Posato einmal anzeigt
  und nicht speichert. Der Code hält zufällige Leser einer Datei ab, die an die
  falsche Person gelangt, aber wer die Datei hat und viel Rechenleistung zum
  Erraten einsetzt, könnte sie öffnen; sende die Datei und den Code also nur
  dorthin, wo du ihnen vertraust.
- Auf dem iPhone ist eine geteilte Datei eine temporäre Kopie, die Posato
  löscht, wenn das Teilen-Menü geschlossen wird, und eine importierte Datei
  wird aus einer temporären Kopie gelesen, die Posato nach dem Lesen löscht.

Ein Import ändert deine Pausensets und Zeitpläne wie Änderungen von Hand; ist
die Synchronisierung eingeschaltet, erreichen die Änderungen deine anderen
Geräte.

## Mitteilungen

Auf Mac und iPhone kann Posato lokale Hinweise anzeigen, wenn eine Pause endet
oder auf einem anderen Gerät beginnt. Sie werden auf deinem Gerät vom
Mitteilungssystem deines Geräts erstellt; Posato sendet dafür nichts an einen
Server. Du kannst sie unter „Über Posato“ oder in den Einstellungen deines
Geräts ausschalten.

## Updates auf dem Mac

Posato für iPhone, Linux und Android sucht nicht selbst nach Updates. Auf dem
Mac fragt Posato, bevor es automatisch nach Updates sucht. Wenn du zustimmst,
sucht es einmal am Tag, während die App läuft. Du kannst die automatische
Suche ausschalten oder selbst „Nach Updates suchen“ wählen. Jedes Update
erfordert deine Aktion zum Laden und Installieren, und die Installation wartet,
bis keine Sitzung aktiv ist.

Update-Informationen und Downloads kommen von GitHub Releases über Sparkle,
eine Update-Bibliothek, die in Posato enthalten ist. Anfragen enthalten weder
deine Website-Liste, deine App-Auswahl, Sitzungen, iCloud-Daten, eine Kennung
der Installation noch ein Systemprofil. Posato vergleicht Versionen und
Systemanforderungen auf deinem Mac.

GitHub und seine Auslieferungsdienstleister können deine IP-Adresse, den
Zeitpunkt der Anfrage, die angefragte Ressource und technische
Verbindungsinformationen erhalten. Eine Download-Adresse lässt erkennen, welche
Version du anfragst. Sie verarbeiten diese Informationen gemäß der
[Datenschutzerklärung von GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
GitHub stellt zusammengefasste Download-Zahlen bereit; Posato fügt keine
Nutzungsanalyse und keine Tracking-Kennung hinzu. Wenn du einen Link zu einer
Version oder zum Support in deinem Browser öffnest, gelten die Richtlinien
dieser Website und deine Browsereinstellungen.

Die Einstellungen für Updates werden auf diesem Mac gespeichert und nicht
über iCloud synchronisiert. Sparkle speichert den Stand der Update-Suche und
temporäre Update-Dateien lokal.

## Diagnose

Posato erfasst oder überträgt keine Diagnosedaten, Absturzberichte oder
Telemetrie. Abgesehen von der Synchronisierung, die du wählst, den Abfragen,
die das Android-VPN weiterleitet, und den unter „Updates auf dem Mac“
beschriebenen Update-Anfragen stellt Posato keine eigenen Netzwerkanfragen.
Dein Betriebssystem oder dein App Store kann gemäß deinen Geräteeinstellungen
und eigenen Richtlinien Diagnosedaten erfassen; Posato greift darauf nicht zu
und fügt nichts hinzu.

Wenn du um Hilfe bittest, teile nur, was du möchtest. Bitte gib in
öffentlichen Meldungen keine Website-Namen, App-Namen, Gerätedetails,
Bildschirmfotos oder Protokolle an.

## Kinder

Posato ist nicht dafür gedacht, das Gerät einer anderen Person zu verwalten,
und erfasst wissentlich keine Daten von irgendjemandem.

## Änderungen

Änderungen an dieser Erklärung werden unter derselben Adresse mit einem neuen
Gültigkeitsdatum veröffentlicht.

## Kontakt

Fragen zum Datenschutz: privacy@posato.app
