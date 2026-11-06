# Bladerunner visualiser

A [Kiosk Satellite](https://kiosksatellite.com) voice skin. When you talk to the assistant, a glowing green HUD appears over animated artwork of Pris, the replicant from Blade Runner, against a black and white city skyline. Its rings pulse with your voice and the answer, race while the assistant thinks, and what you said and the answer appear beside it in terminal type as they arrive.

![Bladerunner visualiser answering a question: a round HUD of green rings over black and white artwork of Pris, with the question and answer beside it](https://raw.githubusercontent.com/MozKaboom/bladerunner-voice-skin/main/assets/voice-skin.webp)

## Installation

### Community Plugins (Recommended)

Bladerunner visualiser is listed in [Community Plugins](https://kiosksatellite.com/plugins/). Use the button below to open it in your Kiosk Satellite and install it.

[![Install in Kiosk Satellite](https://kiosksatellite.com/img/badges/plugin-add.svg)](https://kiosksatellite.com/redirect/plugin/?repository=MozKaboom/bladerunner-voice-skin)

Or search for `Bladerunner visualiser` in **Settings > Plugin Manager > Browse plugins** in Kiosk Satellite.

### Manual

1. Open **Settings > Plugin Manager** in Kiosk Satellite and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste `https://github.com/MozKaboom/bladerunner-voice-skin` and choose **Preview**.
3. Review the plugin and choose **Trust and install**.

## Use it

1. Enable **Bladerunner visualiser** from its entry row in Plugin Manager.
2. Open **Voice Satellite > Appearance** and pick **Bladerunner visualiser** for **Assistant 1 skin**, **Assistant 2 skin** or both.
3. Press **Preview**, or say the wake word.

It has two looks, one for each **Overlay mode**:

- **Full screen:** the conversation on the left and the HUD on the right in landscape, above it in portrait, over a dark backdrop.
- **Docked:** a bar at the bottom of the screen with a small HUD and the latest words. The dashboard stays in view and usable.

While you speak and while the assistant thinks, the HUD holds a dimmed still of the artwork. When the assistant starts speaking its answer, a burst of static breaks the still, and the artwork comes back bright with the Pris clip playing from the start, looping if the answer runs longer. When the voice has been silent for just over a second, longer than the pauses between sentences, it cuts back to the dimmed still, and the words stay on screen. If the voice comes back, the clip carries on from where it stopped. With **Reactive activity bar** off there is no voice level to follow, so the still comes back when Kiosk Satellite says the answer has finished. Its panels show the voice level and whether Pris is listening, thinking or responding. While the answer is still arriving, a cursor blinks after it. If the assistant took actions and **Show tool use** is on, the last three appear under the answer.

It follows these **Voice Satellite > Appearance** settings:

| Setting | What it does |
| --- | --- |
| Background | How dark the full screen backdrop is. Skin default is 88%. |
| Text size | Scales the words in both looks. The docked bar grows taller to fit. |
| Reactive activity bar | While it's off, the rings, glow and level panel stop following the voice. |
| Theme | It is always dark, so it looks the same in the light and dark themes. |

Kiosk Satellite still shows weather cards, images, timers and error messages over the skin, and a double tap still dismisses the turn.

Bladerunner visualiser has no settings of its own.

## Data and privacy

Bladerunner visualiser makes no network requests. It reads the voice state, the voice level and the conversation text from Kiosk Satellite, and draws them with files inside the plugin. Its page is set to refuse anything that isn't one of those files. Nothing is stored or sent anywhere.

## How it works

The HUD is a small web page in the plugin's `assets/hud` folder, shown in the overlay. The plugin passes it the turn as it changes: the words, the Appearance settings and the voice level, about 30 times a second. The page eases toward each new level so the motion stays smooth.

To try the page on a computer, serve `assets/hud` with any web server and open `index.html?demo` for the full screen look or `index.html?mode=docked&demo` for the docked one. The demo plays a made-up question and answer.

## Credits

- The Pris clip is original AI-generated fan artwork by MozKaboom.
- The [Orbitron](https://github.com/theleagueof/orbitron) and [Share Tech Mono](https://fonts.google.com/specimen/Share+Tech+Mono) fonts are included under the SIL Open Font License. The licence texts are in `assets/hud/fonts`.

Bladerunner visualiser is fan art. It is not affiliated with or endorsed by the makers or owners of Blade Runner.

## Build

Install Python 3, JDK 17 or newer and an Android SDK with a platform and build-tools. Set `ANDROID_HOME` and `JAVA_HOME` as needed.

```sh
python3 tools/test.py
python3 tools/build.py
```

The build creates `dist/bladerunner-voice-skin-<version>.zip`. To try it, open **Plugin Manager > Developer Tools > Install from ZIP** in Kiosk Satellite.

Releases are built by the GitHub Actions workflow in `.github/workflows/build.yml` when a release is published.

## License

[Apache-2.0](LICENSE) for the code. The SDK interfaces in `sdk/` and the build tools in `tools/` come from the [Kiosk Satellite plugin template](https://github.com/jxlarrea/kiosk-satellite-plugins) under the same license.
