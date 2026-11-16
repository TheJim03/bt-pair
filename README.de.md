# bt-pair

Eine Bluetooth-Fernbedienung oder ein anderes Zubehör mit einem Android TV koppeln, wenn die Zubehörsuche des Fernsehers es nie anzeigt.

*English: [README.md](README.md)*

## Das Problem

Du versetzt eine Bluetooth-Fernbedienung in den Kopplungsmodus. Dein Handy sieht sie, aber **Einstellungen → Fernbedienungen & Zubehör → Zubehör hinzufügen** auf der Android-TV-Box bleibt leer.

Oft empfängt der Bluetooth-Stack des Fernsehers das Gerät durchaus. Die Zubehörsuche der TV-Einstellungen listet aber nur Geräte, die einen konkreten Typ melden: Fernbedienung, Tastatur, Gamepad, Maus oder Audio. Ein Gerät, das nur den allgemeinen HID-Dienst meldet, wird ohne Hinweis übergangen. Das ist häufig bei Fernbedienungen, die zur Box oder zum Stick eines anderen Herstellers gehören.

Android TV hat keine normalen Bluetooth-Einstellungen als Ausweichmöglichkeit, also gibt es keinen eingebauten Weg an dieser Suche vorbei.

## Die Lösung

Eine winzige App, die alle Bluetooth-Geräte auflistet, die der Fernseher sieht, und das ausgewählte koppelt. Danach verbindet Android das Gerät von selbst als Eingabegerät, und die App kann wieder deinstalliert werden.

- Keine Internetberechtigung, keine Werbung, kein Hintergrunddienst.
- Eine einzige Quelldatei: [MainActivity.java](app/src/com/github/btpair/MainActivity.java).

## Voraussetzungen

- Ein Computer mit `adb` ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools)).
- Debugging am Fernseher: *Einstellungen → Geräteeinstellungen → Info* öffnen (bei Google TV: *System → Info*), siebenmal *Build* (bzw. *Android TV OS-Build*) auswählen, danach in den *Entwickleroptionen* *USB-Debugging* oder *Netzwerk-Debugging* einschalten.
- Die IP-Adresse des Fernsehers (*Einstellungen → Netzwerk*), unten `<TV-IP>`.
- `btpair.apk`: von der [Releases](../../releases)-Seite laden oder [selbst bauen](#selbst-bauen).

## Schritte

1. Verbinden und die Debugging-Abfrage am Fernseher bestätigen:

   ```bash
   adb connect <TV-IP>:5555
   ```

2. App installieren:

   ```bash
   adb install btpair.apk
   ```

3. Der App erlauben, Suchergebnisse zu sehen. Android koppelt Bluetooth-Suchergebnisse an die Standortberechtigung; die App nutzt den Standort nicht.

   ```bash
   adb shell pm grant com.github.btpair android.permission.ACCESS_FINE_LOCATION
   ```

4. App starten:

   ```bash
   adb shell am start -n com.github.btpair/.MainActivity
   ```

5. Die Fernbedienung nah am Fernseher in den Kopplungsmodus versetzen. Viele Fernbedienungen senden nur rund 30 Sekunden, also bei Bedarf wiederholen. Siehe [Kopplungsmodus](#kopplungsmodus-gängiger-fernbedienungen) unten.

6. Die Fernbedienung in der Liste am Fernseher mit der funktionierenden Fernbedienung auswählen. Bei Erfolg steht in der Statuszeile `PAIRED`. Eine Kopplungsabfrage bestätigen, falls eine erscheint.

7. App entfernen. Die Kopplung bleibt erhalten.

   ```bash
   adb uninstall com.github.btpair
   ```

### Ohne Geräteliste

Wer die Bluetooth-Adresse des Geräts schon kennt, lässt Schritt 3 weg und übergibt sie direkt:

```bash
adb shell am start -n com.github.btpair/.MainActivity -e addr AA:BB:CC:DD:EE:FF
```

Für ein Bluetooth-Classic-Gerät `-e transport classic` anhängen. Standard ist LE.

## Ist das dein Problem? So prüfst du es

Die Ursache lässt sich bestätigen, bevor du etwas installierst. **Zubehör hinzufügen** am Fernseher öffnen, die Fernbedienung in den Kopplungsmodus versetzen, dann:

```bash
adb logcat -d | grep -E "BluetoothDevicePairer|btif_get_device_type"
```

- Zeilen mit `btif_get_device_type: Device [aa:bb:cc:dd:ee:ff]` sind Geräte, die der Bluetooth-Stack empfangen hat.
- Zeilen mit `BluetoothDevicePairer: Added device to visible list` sind Geräte, die die Zubehörsuche angenommen hat.

Taucht die Adresse deiner Fernbedienung in der ersten Sorte auf, aber nie in der zweiten, filtert die Suche sie heraus, und diese App hilft. Taucht sie gar nicht auf, ist die Fernbedienung nicht im Kopplungsmodus oder außer Reichweite, und daran kann keine App etwas ändern.

Die genauen Log-Tags können je nach Hersteller abweichen. iPhones zeigen keine Bluetooth-Adressen an; die Adresse findest du mit nRF Connect unter Android oder durch Vergleich der Logcat-Ausgabe mit ein- und ausgeschalteter Fernbedienung.

## Kopplungsmodus gängiger Fernbedienungen

Fernbedienungen ohne LED geben keine Rückmeldung, man verpasst den Modus also leicht. Kombinationen, die funktionieren sollen:

| Fernbedienung | Etwa 5 Sekunden halten |
|---|---|
| Xiaomi XMRM-M3 („Xiaomi RC“) | Home + Zurück |
| Andere Xiaomi- / Mi-Box-Fernbedienungen | Home + Apps oder Home + Menü |

Nur die erste Zeile wurde für dieses Projekt überprüft. Ergänzungen sind willkommen.

## Fehlersuche

- **Die Liste bleibt leer:** Die Standortberechtigung fehlt (Schritt 3), oder der Standort ist in den Einstellungen des Fernsehers ausgeschaltet.
- **„Not bonded … will retry“:** Die Fernbedienung sendet nicht mehr. Kopplungskombination erneut drücken; die App versucht es 12-mal im Abstand von 8 Sekunden.
- **Gekoppelt, aber manche Tasten tun nichts:** Android nutzt für unbekannte Fernbedienungen eine allgemeine Tastenbelegung. App-Schnelltasten und Sprachtasten funktionieren eventuell nicht.
- **Gekoppelt, aber gar nichts funktioniert:** Die Fernbedienung nutzt möglicherweise ein herstellereigenes Protokoll statt Standard-HID. Das kann diese App nicht ändern.
- **Rückgängig:** Gerät unter *Fernbedienungen & Zubehör* entfernen und die App deinstallieren.

Alles, was die App tut, steht im Log: `adb logcat -s BtPair`.

## Selbst bauen

Nötig sind das Android-SDK (Build-Tools und eine Plattform) und ein JDK ab Version 11. Kein Gradle.

```bash
./build.sh
```

Unter Windows:

```powershell
.\build.ps1
```

Beide schreiben `btpair.apk` neben das Skript und signieren sie mit einem Wegwerf-Schlüssel, der auf deinem Rechner erzeugt wird.

## Getestet mit

| TV-Gerät | Android | Fernbedienung | Ergebnis |
|---|---|---|---|
| NVIDIA SHIELD TV (2015/2017) | 11 | Xiaomi XMRM-M3 | Gekoppelt, alle Haupttasten funktionieren |

Dieser Test lief über das Koppeln per Adresse (`-e addr`). Die Geräteliste kam danach hinzu und ist auf einem Fernseher noch nicht getestet; bleibt sie leer, nimm den Weg über die Adresse und eröffne bitte ein Issue.

Andere Android-TV- und Google-TV-Geräte mit demselben Symptom sollten ebenfalls funktionieren, sind aber ungetestet. Rückmeldungen zu anderen Geräten sind willkommen. Die App zielt absichtlich auf ein altes API-Level und ist nur zum Sideloaden gedacht.

## Lizenz

[MIT](LICENSE)
