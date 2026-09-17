# SocialVault 🛡️

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=23)

An open-source, privacy-first multi-platform social media vault and sandboxed web wrapper for Android.

---

## 📖 About

Native social media apps often collect extensive device telemetry, access contacts and clipboard history, track location in the background, and persist unique hardware identifiers.

**SocialVault** isolates social media access inside a hardened, sandboxed web environment. It allows you to switch seamlessly between platforms (or add your own custom web apps) without installing bloated, invasive native apps.

---

## ✨ Features

- **Multi-Platform Support**: Built-in instant switching between popular platforms ordered by global popularity:
  - Facebook
  - YouTube
  - Instagram
  - TikTok
  - Reddit
  - X (Twitter)
  - Pinterest
  - LinkedIn
  - Threads
  - Twitch
  - Bluesky
  - Mastodon
- **Custom Platforms**: Add any website or web app with a simple `+` button, managed with local persistence.
- **Strict Privacy Sandboxing**:
  - Whitelisted domain isolation per platform: links leading to external domains are safely opened in your system's default browser.
  - Cleartext HTTP traffic completely blocked (HTTPS enforced).
  - Cloud backups and ADB device extraction disabled.
  - Zero third-party trackers, closed-source analytics, or ad SDKs.
- **Rich Media & Fullscreen**:
  - HTML5 video playback with full-screen expansion and automatic landscape rotation.
  - Modern file chooser for media uploads.
  - Pull-to-refresh (`SwipeRefreshLayout`) and loading progress indicator.
- **Modern Android Architecture**:
  - 100% Kotlin with View Binding and AndroidX libraries.
  - Material 3 theming with Android 13+ Material You themed adaptive icons.
  - Target SDK 37 (Android 16), Min SDK 23 (Android 6.0+).

---

## 🚀 Building from Source

### Prerequisites
- JDK 17 or higher
- Android SDK (API 37)

### Build Commands
```bash
# Debug APK:
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/SocialVault-v1.0-debug.apk

# Release APK:
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/SocialVault-v1.0.apk

# Run tests and lint:
./gradlew check lint
```

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
