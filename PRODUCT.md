# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Everyday Android Auto owners who install FOSS apps from F-Droid or GitHub. Many are not technical:
they know how to sideload an app but not what a package name, signature or split APK is. They use
CarBridge on their phone, usually at home or while parked, to get an app they already own onto
their car's screen.

## Product Purpose

CarBridge makes a patched copy of an installed app so Android Auto lists it and runs its full
interface on the car display while parked. Success: a user picks an app, approves one install
prompt, and finds it in the car launcher, without a computer.

## Positioning

Free and open source (GPL-3.0), no accounts, no license server, no telemetry. All patching and
signing happens on the phone with a Keystore key that never leaves the device. An optional key
backup exists only in a file the user saves, sealed with a recovery code shown once.

## Operating Context

- Three jobs, all first-class: first-time setup (Android Auto developer mode and Unknown sources),
  patching a new app, and managing existing car copies (re-patch after the original updates, by
  hand or automatically while charging; remove a copy).
- A first install needs the user to confirm Android's system install prompt; updates of copies
  CarBridge installed usually don't. With Shizuku (optional, for advanced users) none do.
- App-wide options live in Settings (gear icon); per-copy options in each copy's ⋮ menu.
- Patching large apps takes noticeable time (Firefox is about 257 MB across 3 APKs).
- After installing a copy, Android Auto must reconnect before the copy appears in the car.

## Capabilities and Constraints

- Car copies install next to the original as `<package>.car`; the original and its data are untouched.
- Supports single and split APKs, from installed apps or from picked/shared APK files and
  .apks/.xapk bundles (not encrypted .apkm). Copies made from files update only from newer files.
- Apps already on the car launcher (Android Auto apps, copies made by AAAnything or Morphe) aren't
  offered for copying.
- With Shizuku, a copy gets the runtime permissions the original was granted; it never gets the
  original's data or sign-ins.
- Apps that check Play Integrity, their own signature or a license may refuse to run after patching.
- The on-device signing key is lost if CarBridge is uninstalled or its data cleared. With a key
  backup, existing copies update again after a restore; without one, they need uninstalling and
  re-patching.
- Only apps whose license allows modification should be patched; patched APKs must not be shared.
- Driving safety: car use is for passengers or while parked.
- minSdk 35 (Android 15+).

## Brand Commitments

Name: CarBridge. App ID `org.carbridge`.

## Evidence on Hand

Verified on a Galaxy S24 Ultra (Android 17, Android Auto 17.9): VLC 3.7.1 and Firefox 159.0.1 car
copies run in Android Auto. Shizuku installs (Google Maps, The Weather Channel) and copies from
APK files (Fossify Calculator, Fossify Notes) verified on the phone only, not yet in the car. No user testimonials, download counts or compatibility list exist yet;
don't invent them.

## Product Principles

- Plain language first: say what happens to the user's apps, not how APKs work.
- Never surprise: say up front what's touched (nothing of the original) and what isn't possible.
- One clear next step on every screen.
- Honest about limits: legal use, safety, and apps that won't work.
