#!/usr/bin/env bash
# Builds btpair.apk without Gradle. Needs: Android SDK (build-tools + one platform) and a JDK 11+.
#   ./build.sh
# Optional environment variables: ANDROID_HOME (or ANDROID_SDK_ROOT)
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
for guess in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
  [ -z "$sdk" ] && [ -d "$guess" ] && sdk="$guess"
done
[ -d "$sdk" ] || { echo "Android SDK not found. Set ANDROID_HOME." >&2; exit 1; }

bt="$(ls -d "$sdk"/build-tools/* | grep -v rc | sort -V | tail -n 1)"
platform="$(ls -d "$sdk"/platforms/android-* | grep -E 'android-[0-9]+$' | sort -V | tail -n 1)"
jar="$platform/android.jar"
[ -f "$jar" ] || { echo "Install build-tools and a platform with the SDK manager." >&2; exit 1; }

out="$root/build"
rm -rf "$out"
mkdir -p "$out/classes" "$out/dex"

"$bt/aapt2" link -o "$out/base.apk" --manifest "$root/app/AndroidManifest.xml" -I "$jar"
find "$root/app/src" -name '*.java' > "$out/sources.txt"
javac -encoding UTF-8 --release 8 -Xlint:-options -nowarn -cp "$jar" -d "$out/classes" @"$out/sources.txt"
find "$out/classes" -name '*.class' -print0 | xargs -0 "$bt/d8" --min-api 21 --lib "$jar" --output "$out/dex"

cp "$out/base.apk" "$out/unaligned.apk"
(cd "$out/dex" && jar uf "$out/unaligned.apk" classes.dex)
"$bt/zipalign" -f 4 "$out/unaligned.apk" "$out/aligned.apk"

# Throwaway signing key, created locally and never committed.
ks="$root/local.keystore"
if [ ! -f "$ks" ]; then
  keytool -genkeypair -keystore "$ks" -storepass android -keypass android -alias btpair \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=btpair" >/dev/null 2>&1
fi
"$bt/apksigner" sign --ks "$ks" --ks-pass pass:android --key-pass pass:android \
  --out "$root/btpair.apk" "$out/aligned.apk"

echo "Built $root/btpair.apk"
