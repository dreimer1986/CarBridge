# Changelog

What changed in each CarBridge release. Dev pre-releases show the Unreleased section.

## [Unreleased]

- The app's font is built in, so it no longer needs Google Play Services or a download
- A Back to top button appears once you've scrolled down the app list
- Tapping the app search scrolls it to the top of the screen, so the results aren't hidden behind the keyboard
- Landscape: the app list stays clear of the navigation bar and camera, and the A–Z strip fits

## [0.7.0] - 2026-11-20

- Back up your signing key with a recovery code, and restore it after reinstalling CarBridge so your copies can update again
- Car copies update automatically while your phone charges, with a per-copy opt-out
- New Settings screen (gear icon) for key backup and automatic updates
- Make car copies from APK files and .apks/.xapk bundles, for apps that aren't installed; update them from newer files
- Open or share APK files to CarBridge from a file manager or browser
- Optional Shizuku support: installs without prompts, even the first time, and copies get the original app's permissions
- Messages no longer hide behind the navigation bar
- Apps already on the car screen, e.g. copies made by AAAnything or Morphe, count as working in Android Auto
- Hide a car copy from the phone's app list from its ⋮ menu; it still shows in Android Auto
- The app's text can now be translated
- Settings → For new copies: choose whether new copies fill the screen, use Android Auto's audio player or hide from the phone's app list
- Settings → Check a backup: test a key backup and its code without restoring it
- Moving to a new phone: key backups now list your copies, and restoring one offers to put them all back on your car with the same choices
- After installing, CarBridge checks that Android Auto can list the copy, and a copy that can't says why
- The app list loads much faster after the first time
- Background update notices for different apps no longer replace each other
- A notice says when a background update took too long, so you can update that copy yourself
- Copy cards list a copy's own choices, e.g. "Hidden on phone", and show short version numbers
- The ⋮ menu marks each copy's choices with a checkmark and has its own icon for each
- Tap anywhere on an app in the list to add it; long names wrap instead of being cut off
- Settings has clearer sections, and a yellow notice when your key backup is missing copies
- The app name fits on one line, and no green strip stays at the top once you scroll past it
- Text is easier to read, with tighter letter spacing
- Car copies show their app's own icon in CarBridge, without the badge covering it
- Settings puts Shizuku under Advanced
- While a copy is made, a little car carries the app over a bridge, showing how far along it is, waits at a gate while Android asks you to confirm the install, and drives off once the copy is installed. Tap the car to honk
- Add several apps at once: tap Select several, or long-press an app, then tick the ones you want; they're added one after another with your choices from Settings
- Copy cards show each copy's size, and "On your car" shows how much space your copies take up in total
- CarBridge knows when Android Auto is connected: it asks you to reconnect only when a new copy needs it, and says so on the home screen until you do

## [0.6.0] - 2026-11-17

- Tap a car copy to open it on your phone, e.g. to sign in
- A–Z strip to jump through a long app list
- Updates to car copies often install without a prompt
- Patching continues if you leave CarBridge
- Clearer messages when a copy can't be made or installed
- Preinstalled apps updated from the Play Store (e.g. YouTube, Chrome) can be added
- Copies made before CarBridge was reinstalled are flagged for re-adding
- Free space is checked first, and patched files are deleted after installing

## [0.5.0] - 2026-11-07

- Choose the screen layout and audio player before adding an app
- Update button on the "copy out of date" notification

## [0.4.0] - 2026-11-07

- Option to keep an app's Android Auto audio player for its car copy
- Android Auto rules are now shared with the CarBridge Patches bundle for Morphe

## [0.3.0] - 2026-11-06

- Update all out-of-date copies in one go
- Warning before copying apps with Google Play copy protection
- Morphe-patched apps point to the CarBridge Patches source

## [0.2.0] - 2026-11-06

- Hide apps that already have Android Auto, with a toggle to show them
- Copies fill landscape car screens; per-app option to keep portrait
- Notification when an app updates and its car copy is out of date

## [0.1.0] - 2026-11-06

- First release
