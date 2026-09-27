[![English](https://img.shields.io/badge/English-2F80ED?style=flat-square)](README.md) [![한국어](https://img.shields.io/badge/%ED%95%9C%EA%B5%AD%EC%96%B4-555?style=flat-square)](README.ko.md) [![日本語](https://img.shields.io/badge/%E6%97%A5%E6%9C%AC%E8%AA%9E-555?style=flat-square)](README.ja.md) [![简体中文](https://img.shields.io/badge/%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-555?style=flat-square)](README.zh-CN.md)

# HoneyBoard X

Rambler voice dictation and Codex-powered translation for Samsung Keyboard.

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white) ![LSPosed](https://img.shields.io/badge/LSPosed-libxposed%20API%20102-6f42c1) ![License: MIT](https://img.shields.io/badge/License-MIT-blue)

## Features

- **Rambler voice input:** Adds a voice button to the Samsung Keyboard toolbar. Uses the Gboard Rambler speech-recognition service, with an Aurora-style header, live transcription, multilingual recognition, and voice editing commands. Final text is inserted in one batch. Silence can finish dictation automatically; long-press to pause or resume, with optional haptics and sound.
- **Codex translation:** Replaces requests from Samsung Keyboard's translation panel with ChatGPT/Codex translation. Choose a model and reasoning effort, set translation instructions, and view account models and usage. When either the 5-hour or weekly usage reaches 0%, the app automatically switches to `gpt-reserve`; failed requests can fall back to Samsung's built-in translation.
- **Unified settings app:** A launcher app with a One UI-inspired interface, available in Korean, English, Chinese, and Japanese.

## Requirements

- A Samsung device with Samsung Keyboard. The module targets `com.samsung.android.honeyboard` **5.9.40.75**, on One UI 8 / Android 16–17. Keyboard updates may change private implementation details and break compatibility.
- Android 12 or later (module minimum SDK 31).
- LSPosed with libxposed API 102 support.
- Internet access. Grant microphone permission to Samsung Keyboard to use voice input. ChatGPT/Codex sign-in is required for translation.

## Install

1. If upgrading from the separate Samsung Rambler or Codex translation modules, remove those modules first. Their private settings and login tokens do not transfer; note your preferences and plan to sign in again.
2. Install a signed HoneyBoard X APK from the project's **Releases**, if available. Otherwise, build it from source using the instructions below.
3. In LSPosed, enable **HoneyBoard X** and set its scope to **Samsung Keyboard** (`com.samsung.android.honeyboard`).
4. Force-stop Samsung Keyboard, then reopen it so LSPosed can apply the hooks.
5. Open HoneyBoard X from the launcher. Configure voice input and, for translation, enable the feature and sign in to ChatGPT/Codex.

## Google API key (required for voice input)

HoneyBoard X **does not include** the Google API key that Gboard uses for Rambler. Get the `X-Goog-Api-Key` value (39 characters, starts with `AIza`) from the Gboard APK yourself, then either:

- **In the app:** HoneyBoard X → Voice input → **Google API key**, paste the key; or
- **At build time:** `GOOGLE_API_KEY=AIza... module/build.sh`, or put `google_api_key=AIza...` in `module/local.keys` (gitignored).

The release APK has no key built in, so you must enter one in the app.

## Settings

The app groups options into voice input, translation, and information screens. Voice settings include primary/additional recognition languages, silence timeout, Aurora visuals, haptics, and sound. Translation settings include enable/disable, model, reasoning effort, custom instructions, Samsung fallback, and account/model/usage details.

Tap the voice button to finish and insert the recognized text; long-press to pause or resume. Samsung Keyboard needs microphone permission for dictation.

## Build from source

Requires JDK 17+, Android build tools (`aapt`, `zipalign`, `apksigner`, `zip`), and the SDK files described in [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

```bash
./module/build.sh
```

The signed APK is written to `module/build/HoneyBoardX.apk`. See the development guide for setup, test commands, migration notes, and known limitations.

## Privacy

- Voice audio and the preceding text context (up to 2,000 UTF-16 characters) are sent to Google's Rambler speech-recognition service. The context supports voice editing commands.
- Text submitted for translation is sent to ChatGPT/Codex servers.
- Login tokens are encrypted with Android Keystore. Samsung Keyboard's microphone permission is required for voice input.

## Disclaimer

HoneyBoard X is an independent, unofficial project and is not affiliated with or endorsed by Samsung, Google, or OpenAI. It relies on private APIs and service behavior that may change or be blocked at any time. Use it at your own risk and responsibility.

## Credits

- [PixelBoard](https://github.com/Akshayykadam/PixelBoard) — Rambler implementation reference
- [libxposed](https://github.com/libxposed) — LSPosed module API

## License

[MIT](LICENSE) © 2026 sampple-korea. Bundled libxposed libraries are Apache-2.0 (`module/libs/LICENSE-libxposed.txt`).

## Made by [sampple-korea](https://github.com/sampple-korea)
