# bt-pair

Pair a Bluetooth remote or accessory with an Android TV when the TV's own accessory search never shows it.

*Deutsche Anleitung: [README.de.md](README.de.md)*

## The problem

You put a Bluetooth remote into pairing mode. Your phone sees it, but **Settings → Remotes & Accessories → Add accessory** on the Android TV box stays empty.

In many cases the TV's Bluetooth stack does receive the device. The accessory search in the TV settings app only lists devices that announce a specific type: remote, keyboard, gamepad, mouse or audio. A device that only advertises the generic HID service, which is common for remotes bundled with another brand's box or stick, is skipped without any message.

Android TV has no regular Bluetooth settings screen to fall back on, so there is no built-in way around that search.

## The fix

A tiny app that lists every Bluetooth device the TV can see and pairs the one you select. After pairing, Android connects the device as an input device on its own, and the app can be uninstalled again.

- No internet permission, no ads, no background service.
- One source file: [MainActivity.java](app/src/com/github/btpair/MainActivity.java).

## What you need

- A computer with `adb` ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools)).
- Debugging enabled on the TV: open *Settings → Device Preferences → About* (on Google TV: *System → About*), select *Build* (or *Android TV OS build*) seven times, then switch on *USB debugging* or *Network debugging* in *Developer options*.
- The TV's IP address (*Settings → Network*), called `<TV-IP>` below.
- `btpair.apk`: download it from the [Releases](../../releases) page, or [build it yourself](#build-it-yourself).

## Steps

1. Connect and confirm the debugging prompt on the TV:

   ```bash
   adb connect <TV-IP>:5555
   ```

2. Install the app:

   ```bash
   adb install btpair.apk
   ```

3. Allow the app to see scan results. Android ties Bluetooth scan results to the location permission; the app does not use your location.

   ```bash
   adb shell pm grant com.github.btpair android.permission.ACCESS_FINE_LOCATION
   ```

4. Start the app:

   ```bash
   adb shell am start -n com.github.btpair/.MainActivity
   ```

5. Put your remote into pairing mode close to the TV. Many remotes only advertise for about 30 seconds, so repeat if needed. See [pairing mode](#pairing-mode-of-common-remotes) below.

6. Select the remote in the list on the TV with your working remote. The status line shows `PAIRED` when it worked. Confirm a pairing prompt if one appears.

7. Remove the app. The pairing stays.

   ```bash
   adb uninstall com.github.btpair
   ```

### Without the device list

If you already know the device's Bluetooth address, skip step 3 and pass it directly:

```bash
adb shell am start -n com.github.btpair/.MainActivity -e addr AA:BB:CC:DD:EE:FF
```

Add `-e transport classic` for a Bluetooth Classic device. The default is LE.

## Is this your problem? How to check

You can confirm the cause before installing anything. Open **Add accessory** on the TV, put the remote into pairing mode, then run:

```bash
adb logcat -d | grep -E "BluetoothDevicePairer|btif_get_device_type"
```

- `btif_get_device_type: Device [aa:bb:cc:dd:ee:ff]` lines are devices the Bluetooth stack received.
- `BluetoothDevicePairer: Added device to visible list` lines are devices the accessory search accepted.

If your remote's address shows up in the first kind but never in the second, the search is filtering it and this app helps. If it never shows up at all, the remote is not in pairing mode or out of range, and no app can fix that.

The exact log tags can differ between manufacturers. iPhones hide Bluetooth addresses; to find the address, use nRF Connect on Android or compare the logcat output with the remote on and off.

## Pairing mode of common remotes

Remotes without an LED give no feedback, so it is easy to miss. Combinations reported to work:

| Remote | Hold for about 5 seconds |
|---|---|
| Xiaomi XMRM-M3 ("Xiaomi RC") | Home + Back |
| Other Xiaomi / Mi Box remotes | Home + Apps, or Home + Menu |

Only the first row was verified for this project. Additions are welcome.

## Troubleshooting

- **The list stays empty:** the location permission is missing (step 3), or location is switched off in the TV's settings.
- **"Not bonded … will retry":** the remote stopped advertising. Press the pairing combination again; the app retries every 8 seconds, 12 times.
- **Paired but some buttons do nothing:** Android uses a generic key layout for unknown remotes. App shortcut buttons and voice buttons may not work.
- **Paired but nothing works at all:** the remote may use a vendor-specific protocol instead of standard HID. This app cannot change that.
- **Undo:** remove the device under *Remotes & Accessories* and uninstall the app.

Everything the app does is logged: `adb logcat -s BtPair`.

## Build it yourself

You need the Android SDK (build-tools and one platform) and a JDK 11 or newer. No Gradle.

```bash
./build.sh
```

On Windows:

```powershell
.\build.ps1
```

Both write `btpair.apk` next to the script and sign it with a throwaway key created on your machine.

## Tested with

| TV device | Android | Remote | Result |
|---|---|---|---|
| NVIDIA SHIELD TV (2015/2017) | 11 | Xiaomi XMRM-M3 | Paired, all main buttons work |

That test used pairing by address (`-e addr`). The device list was added afterwards and has not been tested on a TV yet; if it stays empty, use the address method and please open an issue.

Other Android TV and Google TV devices with the same symptom should work, but are untested. Reports for other devices are welcome. The app targets an old API level on purpose and is meant for sideloading only.

## License

[MIT](LICENSE)
