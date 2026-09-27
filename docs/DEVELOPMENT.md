# Development Notes

This document contains build, test, migration, and compatibility details for contributors. The user-facing overview is in the [English README](../README.md), with [Korean](../README.ko.md), [Japanese](../README.ja.md), and [Simplified Chinese](../README.zh-CN.md) versions.

## Target and implementation notes

- App package: `dev.sampple.honeyboardx`.
- Hook target: `com.samsung.android.honeyboard` version `5.9.40.75`; the current target is One UI 8 / Android 16–17. Samsung Keyboard updates may rename or change private symbols and resources.
- The module uses libxposed API 102 and has minSdk 31.
- The APK packages the libxposed service/interface 102 JARs.
- Voice settings are read through libxposed remote preferences. Changes apply from the next voice panel session; translation settings apply on the next translation request.
- Voice capture and the preceding editor context (up to 2,000 UTF-16 characters) are sent to Google's Rambler speech-recognition service. Password fields are blocked. See [`S3_PROTOCOL.md`](S3_PROTOCOL.md), [`GBOARD_VOICE_UI.md`](GBOARD_VOICE_UI.md), and [`HONEYBOARD_HOOKS.md`](HONEYBOARD_HOOKS.md) for protocol and hook details.
- Translation requests run in the app process; the hooked keyboard process reads remote settings. The bridge ContentProvider checks the Samsung Keyboard UID and expected signature. OAuth credentials and pending login state are encrypted with Android Keystore (AES-GCM). Network errors and cancellation are handled without applying a response to changed source text.

## Runtime behavior and settings

- The settings app exposes voice input, translation, and information sections. Settings are persisted through libxposed remote preferences; failed remote writes are surfaced in the app.
- Voice defaults: current keyboard language as the primary language, no extra languages, a 3-second silence timeout, Aurora enabled, haptics enabled, and sound disabled. Up to three additional languages can be selected. The UI offers silence timeout off, 2/3/4/5/7/10/15 seconds (the internal setting accepts 2–30 seconds).
- Tapping the voice button finishes dictation and inserts the result once; long-press pauses or resumes. Closing the Aurora header or switching input views cancels the session. Direct typing or moving the cursor discards any uncommitted result.
- Translation defaults: disabled, `gpt-5.6-luna` with `low` reasoning effort, Samsung fallback disabled. The prompt supports `{source_language}` and `{target_language}` placeholders. Model selection, per-request reasoning effort, custom instructions, fallback, and account/model/usage inspection are available.
- The 5-hour and weekly usage display can trigger automatic selection of `gpt-reserve` when either reaches 0%.

## Replacing the separate modules

The former modules are `dev.sampple.rambler` (SamsungRambler) and `dev.codex.samsungkeyboard` (Codex translation). Remove both before enabling the unified module to avoid duplicate hooks. Their app-private data is not shared with the new package, so voice preferences must be set again and ChatGPT/Codex login must be repeated.

The old translation app used the `codex_translation_settings` keys `enabled`, `model`, `reasoning`, `model_effort.<model-id>`, `prompt`, and `fallback`. The unified module stores the main values (`enabled`, `model`, `reasoning`, `prompt`, `fallback`) in the libxposed remote group `translation`. Record and re-enter settings before uninstalling the old app. Apply any per-model `model_effort.*` value to the selected model's reasoning setting. The old `codex_oauth_secure/token_bundle_v1` cannot be migrated: it is tied to the old app UID and Android Keystore alias.

### ChatGPT/Codex sign-in note

The sign-in flow uses browser PKCE. A browser may show a connection error for `localhost:1455`; the app does not run a local callback server. Copy the complete address-bar URL in the form `http://localhost:1455/auth/callback?code=…&state=…` and paste it into the app's account row. The app validates `state` and will not accept a code by itself. Complete the flow within 15 minutes; if it expires, start sign-in again.

## Build

### Prerequisites

- JDK 17 or newer.
- Android command-line tools: `aapt`, `zipalign`, `apksigner`, `zip`, `javac`, `java`, and `keytool`.
- SDK directory `${SDK:-$HOME/sdk}` containing:
  - `android-36/android.jar`
  - `android-31-aapt.jar`
  - `libxposed-api-102.jar`
  - `r8.jar`

The build script uses `javac --release 17`, D8, and aapt. Override the SDK directory with `SDK=/path/to/sdk` if needed.

```bash
./module/build.sh
```

The signed output is `module/build/HoneyBoardX.apk`. The signing keystore defaults to `${KEYSTORE:-$HOME/.config/samsung-rambler/release.keystore}`; set `KEYSTORE=/path/to/your.keystore` to use another key. If the selected keystore does not exist, the script creates one using the alias `rambler` and the development password `rambler`. Do not use that automatically generated development key for a public release. Updates installed over an existing copy must be signed with the same key.

### Google API key

`build.sh` writes a generated `BuildKeys.GOOGLE_API_KEY` from `$GOOGLE_API_KEY` or `module/local.keys` (`google_api_key=AIza...`, gitignored). Without it the build succeeds and the app asks for the key in settings. `module/tools/run-s3.sh` also needs `GOOGLE_API_KEY` in the environment.

## Tests and static checks

Run the build and static hook-symbol check:

```bash
./module/build.sh
python3 module/tools/verify-hook-symbols.py
```

Standalone voice and transport tests can be run with the local Android JAR:

```bash
mkdir -p module/tools/out/logic
javac --release 17 -cp "$HOME/sdk/android-36/android.jar" -d module/tools/out/logic \
  module/src/main/java/dev/sampple/rambler/{HistoryRange,SilenceDetector,VoiceOptions}.java module/tools/VoiceLogicTest.java
java -cp "module/tools/out/logic:$HOME/sdk/android-36/android.jar" dev.sampple.rambler.VoiceLogicTest
javac --release 17 -d module/tools/out/logic module/src/main/java/dev/sampple/rambler/s3/*.java module/tools/{S3ResponseTest,TransportCancelTest}.java
java -cp module/tools/out/logic dev.sampple.rambler.s3.S3ResponseTest
java -cp module/tools/out/logic dev.sampple.rambler.s3.TransportCancelTest
```

`verify-hook-symbols.py` checks symbols and resources against the original Samsung Keyboard APK; it does **not** verify runtime behavior. The test commands create generated files under `module/tools/out/`.

## Known limitations and device validation

- Hooks depend on private Samsung Keyboard implementation details; a keyboard update can break them. Re-run the symbol check and retest after system or keyboard updates.
- Static checks are not a substitute for testing on a device with the matching keyboard version. Validate the launcher entry, LSPosed activation and scope, sign-in/token refresh, translation callbacks/cancellation/fallback, and voice input on a supported device.
- Rambler voice input was confirmed on-device in the original voice module. The integrated module's translation and login flows still require on-device validation.
- Grant microphone permission to Samsung Keyboard, not only to HoneyBoard X, for voice input.
- The Gboard Rambler speech-recognition service and the Samsung Keyboard hooks are not public stable APIs and may be changed or blocked without notice.
