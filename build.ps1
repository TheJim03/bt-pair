# Builds btpair.apk without Gradle. Needs: Android SDK (build-tools + one platform) and a JDK 11+.
#   .\build.ps1
# Optional environment variables: ANDROID_HOME (or ANDROID_SDK_ROOT), JAVA_HOME
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$sdk = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk") |
    Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $sdk) { throw "Android SDK not found. Set ANDROID_HOME." }

$bt = Get-ChildItem "$sdk\build-tools" -Directory | Where-Object { $_.Name -notmatch "rc" } |
    Sort-Object { [version]$_.Name } | Select-Object -Last 1
$platform = Get-ChildItem "$sdk\platforms" -Directory | Where-Object { $_.Name -match "^android-\d+$" } |
    Sort-Object { [int]($_.Name -replace "android-", "") } | Select-Object -Last 1
if (-not $bt -or -not $platform) { throw "Install build-tools and a platform with the SDK manager." }
$jar = Join-Path $platform.FullName "android.jar"

function Find-Tool($name) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\$name.exe")) { return "$env:JAVA_HOME\bin\$name.exe" }
    $c = Get-Command $name -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    throw "$name not found. Install a JDK and set JAVA_HOME."
}
$javac = Find-Tool javac; $jarTool = Find-Tool jar; $keytool = Find-Tool keytool
function Check($what) { if ($LASTEXITCODE -ne 0) { throw "$what failed" } }

$out = Join-Path $root "build"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes", "$out\dex" | Out-Null

& "$($bt.FullName)\aapt2.exe" link -o "$out\base.apk" --manifest "$root\app\AndroidManifest.xml" -I $jar; Check "aapt2"
$src = Get-ChildItem "$root\app\src" -Recurse -Filter *.java | ForEach-Object FullName
& $javac -encoding UTF-8 --release 8 -Xlint:-options -nowarn -cp $jar -d "$out\classes" $src; Check "javac"
$cls = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$($bt.FullName)\d8.bat" --min-api 21 --lib $jar --output "$out\dex" $cls; Check "d8"

Copy-Item "$out\base.apk" "$out\unaligned.apk"
Push-Location "$out\dex"
try { & $jarTool uf "$out\unaligned.apk" classes.dex; Check "jar" } finally { Pop-Location }
& "$($bt.FullName)\zipalign.exe" -f 4 "$out\unaligned.apk" "$out\aligned.apk"; Check "zipalign"

# Throwaway signing key, created locally and never committed.
$ks = Join-Path $root "local.keystore"
if (-not (Test-Path $ks)) {
    & $keytool -genkeypair -keystore $ks -storepass android -keypass android -alias btpair `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=btpair" | Out-Null; Check "keytool"
}
& "$($bt.FullName)\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out "$root\btpair.apk" "$out\aligned.apk"; Check "apksigner"

Write-Host "Built $root\btpair.apk"
