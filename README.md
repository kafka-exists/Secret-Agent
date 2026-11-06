# KeyForge Chat

Native Android AI chat client. Bring your own keys for Gemini, OpenRouter, and Groq. When a key or model hits a limit, KeyForge silently switches and keeps the conversation going. Files the AI produces are validated on-device and auto-repaired before you can download them.

- **Kotlin** · Jetpack Compose · Material 3 (light/dark/AMOLED)
- **Architecture:** MVVM + UDF, single-activity, Hilt DI, 9 Gradle modules
- **Multi-key failover** with a full error-classification table (429, quota, 401/403, 5xx, model-not-found, context-too-long, truncation)
- **Functional file guarantee:** JSON/manifest/HTML/JS/CSS/XML/SVG validators, cross-file reference checker, ZIP round-trip integrity, self-repair loop
- **Security:** keys encrypted at rest with Android Keystore (AES-GCM), HTTPS-only, no analytics, no third-party servers

## Module layout

```
app/                 Hilt application, MainActivity, navigation, splash, adaptive icon
core/network/        Ktor client + shared SSE streaming
core/data/           Room (chats, messages, keys, models), DataStore, Keystore crypto
core/ai/             AiProvider, 3 providers, Router, KeyPool, ErrorClassifier
core/files/          File tools, <<<FILE>>> parser, ZIP builder, validators, self-repair
core/ui/             Material 3 design system (Color/Type/Shape/Spacing/Motion/Elevation)
feature/chat/        Chat screen, message rows, composer, file cards, onboarding
feature/keys/        Key manager (add/test/enable/reorder/delete)
feature/settings/    Appearance, routing preset, security
```

## Build locally

Requirements: JDK 17, Android SDK with platform 35.

```bash
# Generate the Gradle wrapper jar (not committed; it is binary).
gradle wrapper --gradle-version 8.9

./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Signed release builds (CI)

The workflow at `.github/workflows/android.yml` produces a debug APK on every push and a signed release APK + AAB on `v*` tags.

### 1. Generate a keystore

```bash
keytool -genkeypair -v \
  -keystore keyforge.keystore \
  -alias keyforge \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep this file safe and **never commit it** (`.gitignore` already excludes `*.keystore` and `*.jks`).

### 2. Add repository secrets

In your GitHub repo, go to **Settings → Secrets and variables → Actions** and add:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 keyforge.keystore` (on macOS: `base64 -i keyforge.keystore`) |
| `KEYSTORE_PASSWORD` | the keystore password you set |
| `KEY_ALIAS` | `keyforge` (or your alias) |
| `KEY_PASSWORD` | the key password you set |

### 3. Cut a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow builds the signed release, uploads APK + AAB as artifacts, and creates a GitHub Release with the binaries attached.

If the signing secrets are absent, the release build falls back to debug signing so the job never fails — useful for fork builds.

## Versioning notes

The version pins in `gradle/libs.versions.toml` (Kotlin 2.1.0, AGP 8.7.3, Compose BOM 2024.12.01, Ktor 3.0.3, Room 2.6.1, Hilt 2.54) are a verified-compatible set chosen so the project compiles on the first CI run. To move to newer releases (Kotlin 2.4.x, Compose BOM 2026.x), bump the catalog entries together with the matching KSP and Hilt versions and run `./gradlew build`.

## Security model

- API keys are encrypted with AES-GCM via a key held in the Android Keystore (`KeystoreCrypto`). The plaintext key exists only in memory at the moment of dispatch.
- `android:allowBackup="false"` plus backup rules exclude the database and settings from cloud/device backup.
- The network security config blocks all cleartext traffic; only the three provider HTTPS endpoints are contacted.
- Keys are masked (`xxxx…yyyy`) everywhere in the UI and never logged (the Ktor logging level is `NONE`).

## Status

This is a real, compilable foundation covering the critical path: Gradle multi-module setup, full Material 3 design system, the provider abstraction + all three providers with streaming, the failover Router with the complete error-classification table, the file-generation + validation + self-repair pipeline with tests, the key/chat/settings screens with designed loading/empty/error states, CI/CD, and the push guide. Areas explicitly left as extension points (called out in the source): the live "Stop" job cancellation, the WebView sandboxed preview, and Paparazzi screenshot tests — each is stubbed at the seam so the rest compiles.
