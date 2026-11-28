# Lodestar

![Lodestar](docs/lodestar-banner.png)

*Formerly NovaBox. Since 1.5.2 the app is called Lodestar; it is the same app and updates in place.*

An IPTV portal player for Android TV and Google TV. It logs in to Stalker / Ministra
middleware portals with a MAC address (the same kind of portal MAG set-top boxes and
STB emulator apps use) and plays live TV, films and series.

Everything works with a plain D-pad controller: **arrows, OK and Back**. No action needs
the red, green, yellow or blue buttons.

## Features

- Several portal profiles, each with its own **STB settings** laid out like a MAG box: STB model (MAG250 to
  MAG520), firmware, image and hardware version, user agent, screen resolution, MAC address, serial number,
  device IDs and signature. Empty fields show the value NovaBox will use. Plus optional portal login/password and time zone
- Live TV with categories, channel logos and a programme guide (now/next with progress). Once you move to the channels, the category list narrows to give them room, and a small, silent **live preview** of the channel you're on plays in the details panel (Settings → Picture and playback → Live preview turns it off)
- **Real channel logos**: when the portal sends none, NovaBox looks the channel's name up in a public logo list (iptv-org, about 0.5 MB, refreshed monthly) and falls back to the initials when nothing fits
- **TV guide**: a grid of channels against time (Live TV → TV guide). It follows the clock and snaps to now when you come back. ▲ ▼ land on what's on now in the next channel (or the same time, after ◀ ▶ to a later show). Move through the next hours and press OK to watch
- **Recent channels** category (hold OK on a channel, or use **Remove from Recent**, to take it off), and **Back to …** in the live menu (or the remote's last-channel key) to jump to the previous channel
- **Continue watching**: films and episodes pick up where you stopped (saved on the TV, no extra data). Hold OK on a title to remove it from the list. Watched episodes get a ✓ and the show offers the next one
- **Long shows**: every episode is listed, even for daily shows with hundreds of episodes. Long seasons open near the episode you last watched, or at the newest ones
- **Episode lengths** on each episode button: the portal's length when it sends one, else the length once played, else the show's usual length (marked ~). Debrid episodes show it once played
- **Autoplay**: when a portal episode ends, the next one (or the next season's first) starts after a 10-second "Up next". Press Cancel to stop
- **Skip intro and credits, learned by ear**: while an episode plays, NovaBox takes a light audio fingerprint of its first and last five minutes. Once two episodes of a show share a stretch, that's the intro or the end credits. From then on a **Skip intro** button shows during the intro (OK jumps past it), and when the credits start, **Up next** offers the next episode (Cancel keeps watching the credits). Nothing to set up or mark, and nothing leaves the TV. It costs about 31 small FFTs per second, only during those ten minutes, and 20–150 KB per show (at most 40 shows, about 6 MB). Settings → Picture and playback turns it off. Videos whose sound goes to the TV undecoded (Dolby passthrough) can't be listened to, so they don't get it
- **Opens straight away**: your channel list and categories are saved on the TV (about 0.1 MB for a portal with 5,800 channels) and shown at once, then refreshed from the portal in the background. If the portal can't be reached, the saved lists still work
- **Audio and Subtitles** buttons in the player, and in the live menu (**Audio (2)** when a channel has a choice). The remote's own audio-language and subtitle keys open them too. The language you pick is remembered for the next videos
- **Picture** settings: always play the best quality, match the TV's frame rate to the film, HDR output, and an optional light sharpen filter
- Films and series by category, with posters, details, seasons and episodes. Shows a portal files under Movies are moved to the Series tab
  (categories named like "TV shows", "Web series", "Dramas"), so Movies keeps only films
- Favourites (♥) for channels, films and series, saved on the device for each profile
- Search across channels, films and series
- Account info from the portal (expiry date, plan)
- The provider authorizes one MAC address per profile: NovaBox keeps the MAC you enter and never changes it.
  If the portal turns it down, the error screen shows the MAC that was sent
- Network check (Settings → Run network check, or from the "Couldn't connect" screen): tests the TV's connection, internet access and speed, the portal's address, server and login, and a live stream, then says what to fix
- **Free TV**: channels that broadcasters stream free to the public, by country (from the
  community list at iptv-org). No portal, profile or login needed; works even with no profiles set up.
  An **HD · 1080p and up** category, the sharpest feed of each channel first, and if a feed fails NovaBox
  tries the channel's other feeds. Once a month it checks every stream and hides the ones that are offline
  (Info panel → Check channels now runs it any time); new channels arrive whenever the list refreshes
- **Check for updates** (Settings → App updates): downloads the newest NovaBox from this repository and
  installs it over the current one. Profiles, favourites and settings are kept
- **Automatic updates** (Settings → App updates → Automatic updates: On): checks once a day, downloads new
  versions in the background and installs them when the TV next starts up. If Android needs a confirm press
  (the first self-update after installing with Downloader), NovaBox asks the next time you open it
- **Developer tools** (Settings → Developer tools → Unlock): adds a **Debrid** tab (cloud icon) for your own
  Real-Debrid account. Link it with a code shown on the TV (no password typed), then browse and search your RD
  torrents and downloads as poster cards (posters from IMDb's public search, no key needed) and play them in the
  film player. Seasons and episodes of the same show share one card. It only shows what is already in your
  account; it doesn't search torrent sites. When an episode ends, the next one in your account starts after a
  10-second "Up next" (press Cancel to stop)
- **VPN app** (Settings → VPN app, with developer tools unlocked): pick the VPN app you use, like Surfshark, and
  NovaBox opens it with one press from Settings or the Debrid sidebar so you can check it's connected. NovaBox
  shows whether a VPN is on but never requires one
- Stream resolution (720p, 1080p, 4K…) shown briefly in the top-right corner when a new stream starts
- Media3/ExoPlayer playback (HLS, MPEG-TS, MP4), with the portal's MAG headers sent to stream servers

## Screenshots

| | |
|---|---|
| ![Home with all tabs](docs/screenshots/01-home-tabs.png) Live TV and the tab bar | ![Free TV](docs/screenshots/02-free-tv.png) Free TV: no login needed |
| ![Choose a country](docs/screenshots/03-free-tv-country.png) Free TV country picker | ![Free TV category](docs/screenshots/04-free-tv-category.png) Free TV categories |
| ![Settings](docs/screenshots/05-settings-updates.png) Settings with App updates | ![Update available](docs/screenshots/06-update-available.png) An update is available |
| ![Android confirm](docs/screenshots/07-update-confirm.png) Android's update prompt | ![Error screen](docs/screenshots/08-error-free-tv-button.png) Connection error, with a Free TV shortcut |
| ![Developer tools](docs/screenshots/09-developer-tools.png) Settings → Developer tools | ![Link code](docs/screenshots/10-debrid-link-code.png) Linking Real-Debrid with a code |
| ![Debrid posters](docs/screenshots/13-debrid-posters.png) Your RD torrents as poster cards | ![Show with seasons](docs/screenshots/14-debrid-show-seasons.png) One card per show, with a chip per season |
| ![Download episodes](docs/screenshots/15-debrid-download-episodes.png) Downloads: a show's episodes on one card | ![Automatic updates](docs/screenshots/16-automatic-updates.png) Settings → Automatic updates |
| ![Update ready](docs/screenshots/17-update-ready.png) When Android needs a press, NovaBox asks on open | ![Sidebar as icons](docs/screenshots/18-debrid-sidebar-icons.png) Debrid sidebar shrinks to icons while you browse |
| ![Sidebar open](docs/screenshots/19-debrid-sidebar-open.png) Press Left and it opens again | ![VPN app](docs/screenshots/20-vpn-app.png) Settings → VPN app (shown with a test VPN app) |
| ![TV guide](docs/screenshots/21-tv-guide.png) TV guide grid | ![Continue watching](docs/screenshots/22-continue-watching.png) Continue watching |
| ![Resume](docs/screenshots/23-resume-film.png) Films resume where you stopped | ![Series](docs/screenshots/24-series-watched-next.png) Watched episodes and "Next" |
| ![Subtitles](docs/screenshots/25-subtitles-picker.png) Subtitles picker | ![Back to channel](docs/screenshots/26-back-to-last-channel.png) Back to the last channel |
| ![Up next](docs/screenshots/27-debrid-up-next.png) Debrid: Up next | ![Free TV HD](docs/screenshots/28-free-tv-hd-check.png) Free TV: HD category and the monthly check |
| ![Picture](docs/screenshots/29-picture-settings.png) Settings → Picture | ![STB settings](docs/screenshots/30-stb-settings.png) STB settings in a profile |
| ![Icon tabs](docs/screenshots/31-icon-tabs.png) Icon tabs: the one you are on shows its name | ![Player](docs/screenshots/32-player-icons.png) Player buttons as icons |
| ![Continue an episode](docs/screenshots/33-continue-episode.png) Continue watching picks up the episode | ![Episode lengths](docs/screenshots/34-episode-lengths.png) Episode lengths on the buttons |
| ![Scrubbing](docs/screenshots/35-scrub-bar.png) Scrub along the progress bar; exit sits on the far right | ![Skip intro](docs/screenshots/36-skip-intro.png) Skip intro, learned from the show's sound |
| ![Live preview](docs/screenshots/37-live-preview-recent.png) Live preview, narrow categories and Remove from Recent | |

## Using Surfshark with NovaBox

NovaBox doesn't run a VPN itself. When the Surfshark app is connected, NovaBox's traffic (Debrid included) goes through it.

1. On the TV, open the Google Play Store, search for **Surfshark**, install it and sign in.
2. In Surfshark, open **Settings → VPN settings → Bypasser** (split tunnelling) if you only want some apps on the VPN,
   choose **Route via VPN**, and add **NovaBox**. Skip this step to put the whole TV on the VPN.
3. Connect Surfshark to a location.
4. In NovaBox, go to **Settings → Developer tools** (unlock it if needed), then **VPN app → Choose VPN app → Surfshark**.
5. From now on, the lock button in the Debrid sidebar (or **Open Surfshark** in Settings) opens Surfshark. The
   button reads **VPN on** when NovaBox's connection is going through a VPN.

If Real-Debrid refuses a link while the VPN is on, try a different Surfshark location: some VPN servers are blocked by Real-Debrid.

## Controls

| Where | How |
|---|---|
| Top tab bar | Icons for Live TV · Free TV · Movies · Series · Favourites · Search · (Debrid) · Settings. The tab you move onto opens up to show its name. Move with ◀ ▶, then ▼ into the page |
| Add a favourite | The **Favourite** button on a channel's panel (▶ from the list), a film's or series' page, or in the player menu. A ♥ marks favourites in the lists |
| Search | Choose the **Search** tab, press OK on the search box and type with the on-screen keyboard |
| Back | Inside a page, jumps back to the tab bar. On the tab bar, exits the app |
| Live player | ▲ ▼ change channel · ◀ channel list · OK menu (Channel list, Back to the last channel, Audio, Subtitles, Favourite) · Back closes the menu or the player |
| A stream won't load | NovaBox tries 3 times (other feeds of the same Free TV channel first), then asks: **Try again** or **Close player**. ▲ ▼ still change channel |
| Film player | OK shows the icon controls (back 10s, play/pause, forward 30s, start over, audio, subtitles, and exit on the far right; the focused one shows its name) · ◀ ▶ skip back 10s / forward 30s · ▲ from the buttons reaches the progress bar: ◀ ▶ scrub (hold to go faster), the time you'll jump to shows above the bar, OK jumps there (or it jumps when you stop), Back cancels · Back hides the controls, then closes the player |

## Install on Google TV (sideload)

You need the `NovaBox.apk` file (the optimised build; `NovaBox-debug.apk` also works) and a phone or computer on the same Wi-Fi network as the TV.

1. **Turn on developer mode on the TV.** Settings → System → About → scroll to
   *Android TV OS build* and press OK on it 7 times until it says you're a developer.
2. **Allow installs from your file app.** Settings → Apps → Security & restrictions →
   Unknown sources → turn on the app you'll use (for example *Send Files to TV* or *Downloader*).
3. **Get the APK onto the TV.** Pick one:
   - **Send Files to TV** (simplest): install it from the Play Store on both the TV and your
     Android phone, then send the APK from the phone.
   - **Downloader** by AFTVnews: upload the APK somewhere you can link to (Google Drive direct
     link, Dropbox, your own server), type the link into Downloader and install.
   - **ADB from a computer**: Settings → System → Developer options → turn on *USB debugging*
     / *Wireless debugging*, note the TV's IP address, then:
     ```
     adb connect <tv-ip>:5555
     adb install -r NovaBox.apk
     ```
     The TV shows a prompt the first time; choose *Allow*.
4. Open the file and choose **Install**. NovaBox then appears in *Apps* on the home screen.
   To put it in your favourites row, long-press OK on it and choose *Add to favourites*.

Google Play Protect may warn that the app is from an unknown developer, because a debug
build isn't signed with a store key. Choose *Install anyway*.

## First run

Enter your provider's **portal URL** (for example `http://example.com:8080/c/`) and the
**MAC address** registered with them (usually starting `00:1A:79`; colons are optional). Most portals need nothing
else. If yours checks the serial number, device ID or signature, enter those in the profile too.

No portal yet? Choose **Skip and watch Free TV**.

## Updating

From version 1.1.0 on, use **Settings → App updates → Check for updates**. The first time, Android asks
you to allow NovaBox to install apps: turn it on, press Back, and the update carries on by itself.
Choose *Update* on Android's screen; NovaBox closes, then open it again from your apps.

Version 1.0.0 has no update button, so install 1.1.0 over it once with Downloader (same link as
before). Your profiles are kept because every NovaBox build is signed with the same key.

## Build it yourself

Requirements: JDK 17 or newer and the Android SDK (platform 35, build-tools 35).

```
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # portal protocol tests against a mock portal
```

## Project layout

- `data/StalkerClient.kt`: portal protocol (handshake, profile, channels, EPG, VOD, series, stream links, keep-alive)
- `data/Stores.kt`: profiles, favourites and settings
- `data/FreeTv.kt`: Free TV country lists
- `data/Updater.kt`: in-app updates from `release/version.json`
- `data/Logos.kt`: channel logos by name from `release/logos.tsv.gz` (rebuild it with `tools/build_logos.py`)
- `data/RealDebrid.kt`: Real-Debrid device-code login, library and link unrestricting
- `data/Posters.kt`: release-name parsing, IMDb poster lookup and show grouping for the Debrid tab
- `ui/`: Compose for TV screens (tab bar, live TV, films/series, details, favourites, search, settings, profile editor)
- `player/PlayerActivity.kt`: Media3 player with the live TV overlay and channel list

NovaBox doesn't include any channels or content. Use it only with a service you're subscribed to.
