# shield-bt-pair

Eine Bluetooth-Fernbedienung mit der NVIDIA SHIELD TV koppeln, wenn sie unter **Einstellungen → Fernbedienungen & Zubehör → Zubehör hinzufügen** nie auftaucht.

*English: [README.md](README.md)*

## Das Problem

Manche Bluetooth-LE-Fernbedienungen (zum Beispiel die Xiaomi XMRM-M3, die sich als „Xiaomi RC“ meldet) erscheinen am Handy, aber nie in der Zubehörsuche der SHIELD, auch nicht im Kopplungsmodus.

Der Bluetooth-Stack der SHIELD empfängt die Fernbedienung durchaus. Die Zubehörsuche der Einstellungs-App listet aber nur Geräte, die einen konkreten Eingabetyp (Fernbedienung, Tastatur, Gamepad …) oder Audio melden. Eine Fernbedienung, die nur den allgemeinen HID-Dienst meldet, wird ohne Hinweis übergangen.

Einen eingebauten Weg an der Suche vorbei gibt es auf der SHIELD nicht:

- `android.settings.BLUETOOTH_SETTINGS` wird von keiner App bedient.
- `cmd bluetooth_manager` gibt es unter Android 11 nicht.
- Der Bluetooth-Gerätewähler ist eine Attrappe und schließt sich sofort.

## Die Lösung

Eine winzige App, die alle Bluetooth-Geräte auflistet, die die SHIELD sieht, und das ausgewählte koppelt. Danach verbindet Android die Fernbedienung von selbst als Eingabegerät, und die App kann wieder deinstalliert werden.

Die App braucht keinen Internetzugriff und besteht nur aus [MainActivity.java](app/src/com/github/shieldbtpair/MainActivity.java).

## Voraussetzungen

- Ein Computer mit `adb` ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools)).
- Netzwerk-Debugging auf der SHIELD: *Einstellungen → Geräteeinstellungen → Info → siebenmal auf „Build“ tippen*, danach *Entwickleroptionen → Netzwerk-Debugging*.
- Die IP-Adresse der SHIELD (*Einstellungen → Netzwerk*), unten `<SHIELD-IP>`.
- `btpair.apk`: von der Releases-Seite dieses Repos laden oder [selbst bauen](#selbst-bauen).

## Schritte

1. Verbinden und die Debugging-Abfrage am Fernseher bestätigen:

   ```bash
   adb connect <SHIELD-IP>:5555
   ```

2. App installieren:

   ```bash
   adb install btpair.apk
   ```

3. Der App erlauben, Suchergebnisse zu sehen. Android koppelt Bluetooth-Suchergebnisse an die Standortberechtigung; die App nutzt den Standort nicht.

   ```bash
   adb shell pm grant com.github.shieldbtpair android.permission.ACCESS_FINE_LOCATION
   ```

4. App starten:

   ```bash
   adb shell am start -n com.github.shieldbtpair/.MainActivity
   ```

5. Die Fernbedienung nah an der SHIELD in den Kopplungsmodus versetzen. Bei der Xiaomi XMRM-M3 **Home + Zurück** etwa 5 Sekunden halten. Sie sendet nur rund 30 Sekunden, also bei Bedarf wiederholen.

6. Die Fernbedienung in der Liste am Fernseher mit der SHIELD-Fernbedienung auswählen. Bei Erfolg steht in der Statuszeile `PAIRED`. Eine Kopplungsabfrage bestätigen, falls eine erscheint.

7. App entfernen. Die Kopplung bleibt erhalten.

   ```bash
   adb uninstall com.github.shieldbtpair
   ```

### Ohne Geräteliste

Wer die Bluetooth-Adresse der Fernbedienung schon kennt, lässt Schritt 3 weg und übergibt sie direkt:

```bash
adb shell am start -n com.github.shieldbtpair/.MainActivity -e addr AA:BB:CC:DD:EE:FF
```

Für ein Bluetooth-Classic-Gerät `-e transport classic` anhängen. Standard ist LE.

## Ist das dein Problem? So prüfst du es

Die Ursache lässt sich bestätigen, bevor du etwas installierst. **Zubehör hinzufügen** auf der SHIELD öffnen, die Fernbedienung in den Kopplungsmodus versetzen, dann:

```bash
adb logcat -d | grep -E "BluetoothDevicePairer|btif_get_device_type"
```

- Zeilen mit `btif_get_device_type: Device [aa:bb:cc:dd:ee:ff]` sind Geräte, die der Bluetooth-Stack empfangen hat.
- Zeilen mit `BluetoothDevicePairer: Added device to visible list` sind Geräte, die die Zubehörsuche angenommen hat.

Taucht die Adresse deiner Fernbedienung in der ersten Sorte auf, aber nie in der zweiten, filtert die Suche sie heraus, und diese App hilft. Taucht sie gar nicht auf, ist die Fernbedienung nicht im Kopplungsmodus oder außer Reichweite.

iPhones zeigen keine Bluetooth-Adressen an. Die Adresse findest du mit nRF Connect unter Android oder durch Vergleich der Logcat-Ausgabe mit ein- und ausgeschalteter Fernbedienung.

## Fehlersuche

- **Die Liste bleibt leer:** Die Standortberechtigung fehlt (Schritt 3), oder der Standort ist in den Einstellungen der SHIELD ausgeschaltet.
- **„Not bonded … will retry“:** Die Fernbedienung sendet nicht mehr. Kopplungskombination erneut drücken; die App versucht es 12-mal im Abstand von 8 Sekunden.
- **Gekoppelt, aber manche Tasten tun nichts:** Die SHIELD nutzt für unbekannte Fernbedienungen eine allgemeine Tastenbelegung. App-Schnelltasten und Sprachtasten funktionieren eventuell nicht.
- **Rückgängig:** Fernbedienung unter *Fernbedienungen & Zubehör* entfernen und die App deinstallieren.

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

- NVIDIA SHIELD TV (2015/2017, `foster`), Android 11
- Xiaomi-Fernbedienung XMRM-M3 („Xiaomi RC“)

Auf anderen Android-TV-Geräten und mit anderen Fernbedienungen mit demselben Symptom sollte es ebenfalls funktionieren, getestet ist das aber nicht. Die App zielt absichtlich auf ein altes API-Level und ist nur zum Sideloaden gedacht.

## Lizenz

[MIT](LICENSE)
