# SocialVault 🛡️

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![FOSS Audit](https://img.shields.io/badge/FOSS%20Audit-100%25%20Compliant-brightgreen.svg)](foss_audit.md)
[![Security Audit](https://img.shields.io/badge/Security%20Audit-Hardened-blue.svg)](security_audit.md)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

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
  - **Automatic EXIF Metadata Scrubbing**: Strips GPS coordinates, device identifiers, timestamps, and camera metadata from photos before uploading. Orientation is normalized so photos remain upright. Enabled by default and toggleable via dashboard menu.
  - Cleartext HTTP traffic completely blocked (HTTPS enforced).
  - Cloud backups and ADB device extraction disabled.
  - Zero third-party trackers, closed-source analytics, or ad SDKs.
- **Deep Linking, Share Target & Link Opener**:
  - **In-App "Open Social Link"**: Quick-paste dialog with clipboard auto-detection. Validates URLs strictly against supported social platforms—rejects unsupported domains to preserve sandbox isolation.
  - **Direct Deep Linking (`ACTION_VIEW`)**: Open Instagram Reels, TikTok videos, YouTube clips, Tweets, and other social links directly from messaging apps (e.g. WhatsApp, Telegram, Signal) inside SocialVault.
  - **Android Share Target (`ACTION_SEND`)**: Share text or links from any Android app into SocialVault to open them in their dedicated, sandboxed tab.
  - **Short-Link Resolution**: Built-in support for platform short domains (`instagr.am`, `vm.tiktok.com`, `vt.tiktok.com`, `youtu.be`, `t.co`, `fb.watch`, `fb.me`, `pin.it`, `lnkd.in`, `redd.it`).
- **Rich Media, Downloads & Fullscreen**:
  - **Media Downloader & Long-Press Saving**: Long-press any photo or media link in any social app to save it directly to your `Download/` folder, copy link, or open in a new tab.
  - **DownloadManager Integration**: System-managed background downloads with download progress notifications, authenticated session cookie forwarding, and base64 image decoding.
  - **HTML5 Video Extraction**: Dedicated "Download Video from Page" menu action to inspect and download direct video streams.
  - Fullscreen HTML5 video playback with automatic landscape rotation.
  - Modern file chooser for media uploads with private sandbox caching.
  - Pull-to-refresh (`SwipeRefreshLayout`) and loading progress indicator.
- **Modern Android Architecture**:
  - 100% Kotlin with View Binding and AndroidX libraries.
  - Material 3 theming with Android 13+ Material You themed adaptive icons.
  - Target SDK 37 (Android 16), Min SDK 24 (Android 7.0+).

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

## 🛡️ FOSS & Security Compliance

SocialVault has undergone rigorous open-source compliance and defensive security audits:
- **[FOSS Compliance Audit](foss_audit.md)**: Independent repository inclusion audit covering dependencies, licensing (100% Apache 2.0 / Free Software), zero binary blobs, and zero telemetry trackers.
- **[Security Architecture & Vulnerability Audit](security_audit.md)**: OWASP Mobile Application Security Verification Standard (MASVS v2.0) audit covering WebView sandboxing, intent sanitization, least privilege permissions, and data extraction protection.

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
