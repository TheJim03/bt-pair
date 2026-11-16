# shield-bt-pair

Pair a Bluetooth remote with an NVIDIA SHIELD TV when **Settings → Remotes & Accessories → Add accessory** never shows it.

*Deutsche Anleitung: [README.de.md](README.de.md)*

## The problem

Some Bluetooth LE remotes (for example the Xiaomi XMRM-M3, which shows up as "Xiaomi RC") are visible to a phone but never appear in the SHIELD's accessory search, even in pairing mode.

The SHIELD's Bluetooth stack does receive the remote. The accessory search in the Settings app only lists devices that announce a specific input type (remote, keyboard, gamepad, …) or audio. A remote that only advertises the generic HID service is skipped without any message.

There is no built-in way around the search on the SHIELD:

- `android.settings.BLUETOOTH_SETTINGS` is not handled by any app.
- `cmd bluetooth_manager` does not exist on Android 11.
- The Bluetooth device picker is a stub that closes immediately.

## The fix

A tiny app that lists every Bluetooth device the SHIELD can see and pairs the one you select. After pairing, Android connects the remote as an input device on its own, and the app can be uninstalled.

The app needs no internet access and has no code besides [MainActivity.java](app/src/com/github/shieldbtpair/MainActivity.java).

## What you need

- A computer with `adb` ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools)).
- Network debugging enabled on the SHIELD: *Settings → Device Preferences → About → tap "Build" seven times*, then *Developer options → Network debugging*.
- The SHIELD's IP address (*Settings → Network*), called `<SHIELD-IP>` below.
- `btpair.apk`: download it from the Releases page of this repository, or [build it yourself](#build-it-yourself).

## Steps

1. Connect and confirm the debugging prompt on the TV:

   ```bash
   adb connect <SHIELD-IP>:5555
   ```

2. Install the app:

   ```bash
   adb install btpair.apk
   ```

3. Allow the app to see scan results. Android ties Bluetooth scan results to the location permission; the app does not use your location.

   ```bash
   adb shell pm grant com.github.shieldbtpair android.permission.ACCESS_FINE_LOCATION
   ```

4. Start the app:

   ```bash
   adb shell am start -n com.github.shieldbtpair/.MainActivity
   ```

5. Put the remote into pairing mode close to the SHIELD. For the Xiaomi XMRM-M3 hold **Home + Back** for about 5 seconds. It only advertises for about 30 seconds, so repeat if needed.

6. Select the remote in the list on the TV with your SHIELD remote. The status line shows `PAIRED` when it worked. Confirm a pairing prompt if one appears.

7. Remove the app. The pairing stays.

   ```bash
   adb uninstall com.github.shieldbtpair
   ```

### Without the device list

If you already know the remote's Bluetooth address, skip step 3 and pass it directly:

```bash
adb shell am start -n com.github.shieldbtpair/.MainActivity -e addr AA:BB:CC:DD:EE:FF
```

Add `-e transport classic` for a Bluetooth Classic device. The default is LE.

## Is this your problem? How to check

You can confirm the cause before installing anything. Open **Add accessory** on the SHIELD, put the remote into pairing mode, then run:

```bash
adb logcat -d | grep -E "BluetoothDevicePairer|btif_get_device_type"
```

- `btif_get_device_type: Device [aa:bb:cc:dd:ee:ff]` lines are devices the Bluetooth stack received.
- `BluetoothDevicePairer: Added device to visible list` lines are devices the accessory search accepted.

If your remote's address shows up in the first kind but never in the second, the search is filtering it and this app helps. If it never shows up at all, the remote is not in pairing mode or out of range.

iPhones hide Bluetooth addresses. To find the address, use nRF Connect on Android or compare the logcat output with the remote on and off.

## Troubleshooting

- **The list stays empty:** the location permission is missing (step 3), or location is switched off in the SHIELD's settings.
- **"Not bonded … will retry":** the remote stopped advertising. Press the pairing combination again; the app retries every 8 seconds, 12 times.
- **Paired but some buttons do nothing:** the SHIELD uses a generic key layout for unknown remotes. App shortcut buttons and voice buttons may not work.
- **Undo:** remove the remote under *Remotes & Accessories* and uninstall the app.

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

- NVIDIA SHIELD TV (2015/2017, `foster`), Android 11
- Xiaomi XMRM-M3 remote ("Xiaomi RC")

It should work on other Android TV devices and remotes with the same symptom, but that is untested. The app targets an old API level on purpose and is meant for sideloading only.

## License

[MIT](LICENSE)
