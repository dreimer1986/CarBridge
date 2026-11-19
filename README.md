# CarBridge

An original GPL-3.0 Android app that makes a car copy of an installed app so Android Auto lists
it and runs its full interface on the car display while parked. Patching, signing and installing
all happen on the phone. The car-visibility entries come from AABrowser (GPL-3.0); nothing here
comes from AAAnything.

> **Status (2026-11-06):** verified on a Galaxy S24 Ultra (Android 17, Android Auto 17.9) with
> VLC 3.7.1, Firefox 159.0.1 (3 split APKs), GitHub 1.282.0, Offline Games (Unity, 2 split APKs)
> and Morphe YouTube 21.03.36.

## CarBridge app or CarBridge Patches?

Both put an app's full interface on the Android Auto screen, using the same rules (CarBridge's
`core` module). They differ in what they do to the app:

| | [CarBridge app](https://github.com/jmwerk/CarBridge) | [CarBridge Patches](https://github.com/jmwerk/carbridge-patches) for Morphe |
|---|---|---|
| What you get | A separate copy, "*App* (Car)", next to the original | The patched app; no extra car copy |
| Original app | Untouched; keeps updating from its store | Replaced, unless Morphe gives the app its own package name (as it does for YouTube) |
| App data and sign-in | The copy starts empty; sign in again | Kept when the patched build replaces the original |
| Works with | Almost any installed app, including Play Store split APKs | Apps you patch in Morphe Manager, from an APK you provide |
| Combine with other patches | No | Yes, e.g. Morphe's YouTube patches |
| After the original updates | CarBridge notifies you; tap **Update** to rebuild the copy | Repatch the new version in Morphe Manager |
| Options | Per copy, from its ⋮ menu: fill the car screen, keep the audio player, show in split screen | Patch options: Fill the car screen, Keep media services; a separate split-screen patch |
| What you need | Only the phone | Morphe Manager |

**Use the CarBridge app** for apps you install from a store and don't otherwise modify, like
games, browsers and video apps. It's the quickest route and never touches the original.

**Use CarBridge Patches** for apps you already patch with Morphe (YouTube, YouTube Music and
others), or when you want one patched app instead of an original plus a car copy. CarBridge
points Morphe-patched apps here automatically.

Neither works with apps that check Play Integrity, their own signature or a license; both
re-sign the app.

## Install

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/jmwerk/CarBridge)

[Obtainium](https://github.com/ImranR98/Obtainium) installs and updates CarBridge from this repo's
GitHub releases. Or download the APK from [Releases](https://github.com/jmwerk/CarBridge/releases).

Verify the installed app with [AppVerifier](https://github.com/soupslurpr/AppVerifier) by copying
this block into it:

```
org.carbridge
A4:68:93:60:A9:1A:49:B1:6F:A7:B1:50:DC:FC:6E:2B:89:21:1A:CD:B9:63:B5:5F:1B:15:70:4A:DA:43:CC:7F
```

### Dev builds

Every push to the `dev` branch publishes a pre-release, **CarBridge Dev** (`org.carbridge.dev`),
which installs next to CarBridge. In Obtainium, add this repo again with **Include prereleases**
on and **Filter release titles by regular expression** set to `Dev`. Dev builds may be unstable.
Both apps make the same `<package>.car` copies with different keys, so use one of them per app.
Dev builds are signed with the same key; verify them with:

```
org.carbridge.dev
A4:68:93:60:A9:1A:49:B1:6F:A7:B1:50:DC:FC:6E:2B:89:21:1A:CD:B9:63:B5:5F:1B:15:70:4A:DA:43:CC:7F
```

## Requirements

- Android 15+ phone with USB debugging on
- Android Auto developer mode: Android Auto settings → tap *Version* 10× → ⋮ → Developer settings
  → enable **Unknown sources** (CarBridge's setup card walks through this)
- A head unit, or the Desktop Head Unit (DHU) at
  `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`

## Build and install

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :patcher:installDebug
```

The release build (`:patcher:assembleRelease`) runs R8 and is about 5 MB. apksig's ASN.1 models
need the keep rules in `patcher/proguard-rules.pro`, or v1 signing fails.

## Use

Open CarBridge, tap **Add** next to an app, and confirm Android's install prompt. The copy installs
as `<package>.car` next to the original, named "<App> (Car)" with a bridge badge on its icon. Then
reconnect Android Auto. Tap a copy in CarBridge to open it on the phone, e.g. to sign in, since
copies start with no data. When the original updates, CarBridge sends a notification, flags the copy,
and offers **Update all** when several are out of date. With **Update copies automatically** on
(Settings, or the notification's button), a job rebuilds outdated copies one at a time while the
phone charges and installs them without a prompt where Android allows; a copy's ⋮ menu opts it
out. Each original version is tried once, and a copy still waiting after two days, or one that
failed, gets the notification instead. **Add from an APK file** makes a copy of an app
that isn't installed, from an APK, a base plus split APKs, or an `.apks`/`.xapk` bundle (not
encrypted `.apkm`); such copies update from newer files through **Update from a file**. APKs can also be
opened with or shared to CarBridge from a file manager or browser. With [Shizuku](https://shizuku.rikka.app/)
running, **Settings → Install with Shizuku** installs copies without any prompt, even the first
time, and gives them the runtime permissions the original app has. Copies drop portrait locks so they fill the
car screen; the ⋮ menu keeps the portrait layout per app. CarBridge warns before copying apps with
Google Play's copy protection (`com.pairip.licensecheck`), and points Morphe-patched apps to the
[CarBridge Patches](https://github.com/jmwerk/carbridge-patches) source instead.

| Step | Code | Notes |
|------|------|-------|
| Android Auto rules, shared with [CarBridge Patches](https://github.com/jmwerk/carbridge-patches) | `core/…/CarPatch.kt` | Plain Kotlin |
| Edit the binary manifest and resources | `patch/ManifestPatcher.kt` | ARSCLib 1.4.0 (Apache-2.0) |
| Label and icon | `patch/IconBadger.kt` | Draws the original icon plus the badge on the phone |
| Sign | `patch/KeystoreSigner.kt` | apksig 9.5.1 (Apache-2.0); RSA-3072 key in the Android Keystore, 16 KB native-lib alignment |
| Key backup | `patch/KeyBackup.kt` | Backup key plus v3 rotation lineage, sealed with a recovery code; see [Key backup](#key-backup) |
| Install | `Installer.kt` | `PackageInstaller` session; the user confirms new copies, and updates when Android requires it |

Test the background updater without waiting for an app update (debug builds): `adb shell am
broadcast -n org.carbridge.debug/org.carbridge.AutoUpdateDebugReceiver`, then `adb shell cmd
jobscheduler run -f org.carbridge.debug 2`.

A JVM check of the patch against a real APK, or a directory of `base.apk` + `split_*.apk`,
verified afterwards with `aapt2`. `-PpatchHeap=256m` mirrors a phone's heap cap:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :patcher:testDebugUnitTest -PpatchFixture=/path/app.apk
```

## Split screen (experimental)

A copy's ⋮ menu → **Car screen layout…** → **Next to the media cards** rebuilds it to show in
Android Auto's main pane, next to the media and notification cards, instead of full screen.
**Full screen** switches back.

What you need:

- [Shizuku](https://shizuku.rikka.app/) installed and running on the phone. The copy asks for
  Shizuku permission the first time it opens on the car; allow it on the phone. If Shizuku isn't
  running yet, the car says so, and the app opens by itself once you start it.
- Android Auto's **Unknown sources** setting, as for any copy.

How it works:

- Opening the copy on the car first shows **For passengers only**. Tap **I'm a passenger** to
  open the app. Android Auto treats the copy as a navigation app, so it doesn't block it while
  driving the way it blocks full-screen apps; don't use it while you drive.
- To leave, open another app from Android Auto's app bar. There's no Exit button: Android Auto
  keeps a navigation app in the main pane and reopens this one when it closes.
- Taps, scrolls and flings on the car screen go to the app, including its dialogs, menus and
  Android permission prompts. Pinch zoom doesn't yet, and two quick taps on the same spot can
  arrive as a pinch, so tap a little slower.
- The keyboard opens on the phone, not on the car.
- **Back** at the top right goes back inside the app. On the app's main screen it shows the
  app's name instead and does nothing, unless the app handles Back in its own code.
- The app runs on its own display, so it can be open on the phone and the car at the same time.
- **Let the app go under the Back button**, in the same dialog, uses the space under **Back** too.
  The app gets more room, but the button covers its top-right corner while it's visible.

Known issues:

- If the copy is reinstalled or force-stopped while it's on the car screen, Android Auto may say
  the app isn't responding. Tap **Exit** and open it again.
- Unity games, e.g. 2 Player Games, stay white in the pane. Use the full-screen copy for them.

The car code lives in `splitscreen/host` (added to the copy's classes) and `splitscreen/payload`
(loaded from the copy's assets), shared with CarBridge Patches; the manifest rules are in
`core/…/SplitScreenPatch.kt`.

## Run with the DHU

On the phone: Android Auto developer settings → ⋮ → **Start head unit server**. Then:

```bash
~/Library/Android/sdk/platform-tools/adb forward tcp:5277 tcp:5277
```

```bash
~/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

The DHU session drops when a car app is installed. Restart the DHU afterwards.

## How it works

The original spike (removed; its code is in commit 329fa49) tested two approaches.

**Launching unmodified apps onto the car display doesn't work.** Each car app gets its own
virtual-device display (`NativeDisplay:ComponentInfo{…}`). The system's
`GenericWindowPolicyController` rejects any other app's activity on it: `Activity launch
disallowed by policy` (result code 102), whatever the caller's permissions or target display id.

**Patching a copy works.** Android Auto shows an ordinary activity when the app has:

- `com.google.android.gms.car.application` meta-data pointing to an automotive app descriptor
- `CAR_LAUNCHER`, `androidx.car.app.category.NAVIGATION` and `android.intent.category.APP_MAPS`
  on the launcher activity
- the `androidx.car.app.ACCESS_SURFACE` and `MAP_TEMPLATES` permissions

Any activity of the patched package may then run on its car display, not just the launcher one.

### Lessons

- Apps with a `MediaBrowserService` (or `CarAppService`) open in Android Auto's template UI
  instead. The patcher strips those intent filters from the copy.
- Renaming the package means rewriting provider authorities (including `@string/` ones), declared
  permissions and `android:sharedUserId`, or the install fails with a conflict.
- The resource table's package name must be renamed too: Unity apps call
  `getIdentifier(name, type, getPackageName())` and crash on launch otherwise.
- Authorities inside `<queries>` are lookups, not declarations, and must not be renamed.
- Split APKs must carry the renamed package too; all parts are signed with the same key and
  installed in one `PackageInstaller` session.
- Apps with `minSdk < 18` get a v1 signature using SHA1withRSA, so the Keystore key must allow
  SHA-1. Keystore keys can't be changed after creation.
- ARSCLib keeps an APK's tables in memory; YouTube needs over 100 MB, so CarBridge uses
  `largeHeap`.
- The newest AndroidX libraries need AGP 9.1 and compileSdk 37; versions are pinned for AGP 8.13.

### Known gaps

- Apps protected by Play Integrity, signature checks or license verification will refuse to run
  after re-signing. Nothing can be done about that from the outside.
- Authorities hardcoded in an app's code still point at the original package. Measured on
  2026-11-06: Firefox 159 has none (it builds them from the runtime package name); VLC 3.7.1 has
  one (`org.videolan.vlc.thumbprovider`), so thumbnails in the copy may come from, or miss, the
  original. Fixing this means rewriting dex strings, and ARSCLib's dex module calls itself
  experimental, so it's deferred until a real app needs it.
- Activity aliases keep the original package prefix, so apps that build alias names from
  `getPackageName()` can't switch them. Morphe YouTube shows a harmless
  `CustomBrandingPatch: setBranding failure` toast because of this.
- Copies made before a user's first key backup, on a phone that has since lost its key, can't be
  recovered: the lost key is needed to vouch for the backup key.

## Key backup

Copies are signed with a non-exportable Keystore key, which dies with CarBridge's data. To survive
that, **Back up** (a card once a copy exists, or Settings) makes a second RSA-3072 key in software and
has the Keystore key sign an APK Signature Scheme v3 rotation lineage to it. The backup key,
its certificate (`patch/SelfSignedCertificate.kt`) and the lineage go into a `.cbkey` file
encrypted with AES-256-GCM under a PBKDF2 key from a 24-character recovery code shown once. The
backup key isn't kept on the phone; the Keystore key keeps signing copies as before.

After a reinstall or data clear, CarBridge flags copies it can't update and offers **Restore
backup**. The backup key is imported into the Keystore, and from then on copies are signed with it
plus the lineage, which Android accepts as an update of copies signed by the lost key. Rotated
copies are v3 only, since v1 and v2 would need the lost key; copies only install on Android 15+.

- Android checks updates against a copy's current signer, and won't roll back: once a copy is
  signed with the backup key, only that key updates it. `KeystoreSigner.choose` picks per copy.
- The same file keeps working after a second loss, since it holds the key now in use.
- Anyone with the file and code can sign updates to the user's copies, so the UI says to keep
  both off the phone.
- Copies installed by another CarBridge build still on the phone (CarBridge Dev) aren't flagged
  as lost.

Verified on 2026-11-17 on the S24 Ultra (Android 17) with a minimal test package: the Keystore key
signs the lineage, the backup key imports into Keystore, and the rotated update installs in place
without a prompt.

## Release signing

The release key is `~/.android/carbridge-release.jks` (RSA-4096, alias `carbridge`, valid to 2056,
certificate SHA-256 `a4:68:93:60:a9:1a:49:b1:6f:a7:b1:50:dc:fc:6e:2b:89:21:1a:cd:b9:63:b5:5f:1b:15:70:4a:da:43:cc:7f`).
Its password is in `~/.gradle/gradle.properties` as `CARBRIDGE_*`; neither is in the repo.
Without them, `assembleRelease` falls back to the debug key.

Note user-facing changes under `## [Unreleased]` in `CHANGELOG.md` as they land on `dev`; Dev
pre-releases show that section. To publish a release, bump `versionCode` and `versionName`, rename
`[Unreleased]` to `[<versionName>] - <date>`, add
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (short form, for F-Droid), then run
`scripts/release.sh`. It refuses a version without a changelog section, and an APK that isn't signed
with the release key.

If the repo has the `CARBRIDGE_KEYSTORE` secret, the script only tags and pushes, and
`.github/workflows/release.yml` builds, verifies and publishes the release. That stores the key in
GitHub, so anyone with admin access to the repo could use it. To turn it on:

```bash
base64 -i ~/.android/carbridge-release.jks | gh secret set CARBRIDGE_KEYSTORE
```

```bash
gh secret set CARBRIDGE_STORE_PASSWORD && gh secret set CARBRIDGE_KEY_ALIAS && gh secret set CARBRIDGE_KEY_PASSWORD
```

Store listing text, icon and screenshots for IzzyOnDroid and F-Droid clients are in
`fastlane/metadata/android/en-US`. The screenshot uses made-up sample apps from a debug-only
screen (`patcher/src/debug`); crop the status and navigation bars off the capture:

```bash
./gradlew :patcher:installDebug && adb shell am start -n org.carbridge.debug/org.carbridge.ScreenshotActivity
```

**Back up both files somewhere safe.** If the key is lost, CarBridge can't be updated in place.
Users would have to uninstall it, which also deletes the on-device key it uses for car copies.

Never redistribute patched APKs, and only patch apps whose license allows modification.

## Inspiration

[AAAnything](https://github.com/kododake/AAAnything) showed that everyday apps can run on Android
Auto without root, and CarBridge owes the idea to it. CarBridge is a separate, from-scratch take
that shares no code with it, with one goal of its own: every app is free, with no tiers or sign-in,
and the source is open under GPL-3.0.

## Support

CarBridge is free for every app and will stay that way. If it's useful to you, you can
[buy me a coffee](https://buymeacoffee.com/jmwerk). It's entirely optional and doesn't unlock anything.

## License

GPL-3.0. See [LICENSE](LICENSE).
