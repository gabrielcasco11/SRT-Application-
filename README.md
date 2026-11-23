# SRT Monitor

Personal Android receiver for one SRT stream (H.264 or H.265 in MPEG-TS), built for
showing Moxela / Virtuoso SRT outputs on a phone. Caller and listener modes,
AES passphrase, stream ID, and live SRT statistics.

## Install on the phone
1. Every push to this repo builds the app on GitHub (Actions tab, about 5 minutes).
2. On the phone open `https://github.com/<your-user>/<this-repo>/releases/latest/download/srt-monitor.apk`
   (signed in to GitHub if the repo is private).
3. Allow "Install unknown apps" for the browser when Android asks, then install.
   New builds install over the old one; settings are kept.

## Using it
- **Caller**: the phone connects out. Set the Moxela/Virtuoso SRT output to *Listener*.
  Works on Wi-Fi or mobile data.
- **Listener**: the Moxela/Virtuoso calls the phone. Only allowed on Wi-Fi; the setup
  screen shows the phone's IP and port to type into the output.
- *Keep SRT on Wi-Fi* (Advanced) keeps traffic on a Wi-Fi without internet, which
  Android would otherwise bypass for mobile data.

## Changing the look
- Background: replace `app/src/main/res/drawable/app_background.xml` with your own image
  named `app_background.png` or `.jpg` in `app/src/main/res/drawable-nodpi/`
  (delete the xml so there is only one `app_background`).
- Colours: `app/src/main/java/com/gabriel/srtmonitor/ui/theme/Theme.kt` (`Palette`).
- App icon: `res/drawable/ic_launcher_*.xml`. Name: `res/values/strings.xml`.

## How it works
`srtdroid` (libsrt + OpenSSL) receives the stream → `SrtDataSource` → Media3 `TsExtractor`
→ hardware MediaCodec decode. Stats come from `srt_bistats`.

## Limits
- 4:2:2 HEVC/H.264 (common on contribution encoders) has no hardware decoder on phones —
  set the source to 4:2:0 (Main / Main 10 / High) for the demo.
- MPEG-1 Layer II and AC-3 audio may not play on the phone; AAC works. Video still plays.
