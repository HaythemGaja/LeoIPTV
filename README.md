# Leo IPTV

A simple Android TV player for your own Xtream Codes login or M3U playlist.
Live TV, movies, series, EPG, favorites, Continue Watching. On-device only —
no channels are bundled, and content comes only from the account or playlist
you add.

## Features (v2.2)

- Xtream Codes login and M3U playlist, with saved profiles (multiple accounts)
- Live TV, Movies, Series, Favorites, TV Guide
- Real EPG (Now/Next) when your provider supplies it
- Media player with transport controls, buffering spinner, and error retry
- Per-category data cache with a Refresh button (loads once, saves for next time)
- Search in every category
- Recently Added and Favorites rows per category
- Continue Watching — movies and series episodes resume where you left off
  (progress bar + time left; ≥90% watched counts as finished)
- Bigger poster-led cards, and an "Items per row" setting (3–6)
- Auto-login: opens straight to your channels when a profile is saved
- Delete-profile confirmation, so one wrong remote tap can't wipe a login

## Build

Manual aapt2 + javac + d8 pipeline, Java only, no libraries, no Gradle.

Prerequisites (as siblings of this repo directory):

- `../jdk` — a JDK (Temurin 17 works)
- `../sdk` — Android SDK with `build-tools/34.0.0` and
  `platforms/android-34/android.jar`
- `../debug.keystore` — a debug keystore (the standard `android` password;
  any debug keystore works, the file itself is never committed)

Then run:

```sh
./build-apk.sh
```

Output: `build/out/LeoIPTV-v2.2.apk` (package `com.leo.iptv`, versionCode 5,
versionName 2.2, minSdk 24, targetSdk 34).

Install on an Android TV box with `adb install -r LeoIPTV-v2.2.apk`.
The app keeps its data (profiles, favorites, recent, progress) on update.

## Privacy

Credentials are stored only on the device (SharedPreferences). Nothing is
uploaded, and no provider URL, username, or password is in this repo.
