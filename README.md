# Leo IPTV

A light, fast, modern Android TV player for your own Xtream Codes login or M3U playlist.

## v3.0 — redesigned from the ground up

- **Home hub**: slim left sidebar (Search, Live TV, Movies, Series, Favorites, TV Guide, Settings, Switch user), a large hero that follows the focused item with Play/Resume and Favorite actions, and rows for Continue Watching, Recently Added and Favorites.
- **Browse**: the same shell per mode — sidebar categories, hero with backdrop art, poster wall (2:3 posters for movies/series, 16:9 logo tiles for live channels). Cards zoom on focus.
- **Player**: full-screen video with an auto-hiding overlay. Channel logo, name, LIVE badge and clock on top; Now/Next guide with a live program progress bar (base64 EPG titles decoded), seek bar for VOD, -30s / Pause / +30s and Favorite below. D-pad UP/DOWN zaps channels.
- **Real EPG** (Now/Next) when your provider supplies it; live "Now:" shown on channel cards.
- **Continue Watching**: movies and episodes resume where you left off (progress bar + time left; ≥90% watched counts as finished).
- **Per-category data cache** with a Refresh button (loads once, saves for next time).
- **Search** in every category, plus a global search screen.
- **Favorites** everywhere (hero button, MENU key in the player, movie details).
- Bigger poster-led cards, and an "Items per row" setting (3–6).
- Auto-login: opens straight to Home when a profile is saved.
- Delete-profile confirmation, so one wrong remote tap can't wipe a login.

Content comes only from the Xtream Codes login or M3U playlist you add. No channels are bundled. Credentials are stored only on the device.

## Build

Manual aapt2 + javac + d8 pipeline, Java only, no libraries, no Gradle.

Prerequisites (as siblings of this repo directory):

- `../jdk` — a JDK (Temurin 17 works)
- `../sdk` — Android SDK with `build-tools/34.0.0` and `platforms/android-34/android.jar`
- `../debug.keystore` — a debug keystore (the standard `android` password; any debug keystore works, the file itself is never committed)

Then run:

```sh
./build-apk.sh
```
