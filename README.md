# LearnHarness (mobile)

A Kotlin Multiplatform app for Android and iOS that uses an AI tutor to move you from what you already understand to a goal you choose. Teaching happens one reasoning step at a time. What you come to hold is kept in a local knowledge graph.

This is an independent reimplementation of the teaching ideas behind [learnharness](https://github.com/amosblomqvist/learnharness). It shares no source with that project.

## What it does

1. You start a session with a goal (for example, “why does ice float?”).
2. The tutor probes where your understanding ends, plans the shortest route, then teaches one step at a time with a check after each step.
3. Ideas that land are stored as propositions linked to their premises, so later sessions can build on them.

Your chats and graph are stored on the device and excluded from cloud backups. API keys are encrypted with the Android Keystore or iOS Keychain. Requests go only to the model provider you configure.

## Setup

### Android

```bash
./gradlew :androidApp:assembleDebug
```

Install the APK under `androidApp/build/outputs/apk/debug/`. Open **Settings**, choose Anthropic or an OpenAI-compatible endpoint, paste your API key, and start a session.

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode on a Mac, set your Team ID in `iosApp/Configuration/Config.xcconfig`, then run the `iosApp` scheme. The shared framework is built by the Xcode Gradle phase.

### Tests

```bash
./gradlew :shared:testAndroidHostTest
```

## Providers

| Provider | Default endpoint | Notes |
| --- | --- | --- |
| Anthropic | `https://api.anthropic.com` | Messages API with tools |
| OpenAI-compatible | `https://api.openai.com/v1` | Chat Completions; set a custom base URL for OpenRouter or a local server |

Custom endpoints should use HTTPS. Plain HTTP is allowed only for local servers: `localhost`, `127.0.0.1`, and the Android emulator host `10.0.2.2` on Android, and local-network hosts on iOS.

## License

Apache License 2.0. See [LICENSE](LICENSE).
