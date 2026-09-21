# SocialVault 🛡️

[![License](https://img.shields.io/badge/License-Apache%202.0%20%2F%20GPL%203.0-blue.svg)](LICENSE)
[![FOSS Audit](https://img.shields.io/badge/FOSS%20Audit-100%25%20Compliant-brightgreen.svg)](foss_audit.md)
[![Security Audit](https://img.shields.io/badge/Security%20Audit-Hardened%20(66%2F66%20Tests)-blue.svg)](security_audit.md)
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
- **Dashboard Platform Management**: Dedicated dashboard cards with drag-and-drop reordering, quick actions, and direct platform-scoped data wiping and cache clearing.
- **Strict Privacy Sandboxing & Container Segregation**:
  - **Chromium Multi-Profile Isolation**: On Android 8.0+ / Chromium 88+, SocialVault leverages `WebViewFeature.MULTI_PROFILE` to assign dedicated, hermetic profile containers (`ProfileStore`) to each platform. Cookies, LocalStorage (LevelDB), IndexedDB, Service Workers, and HTTP caches are physically separated between platforms, preventing cross-site session leaking or tracking across tabs.
  - **Real-Time Tracker & Telemetry Blocking (`TrackerBlocker`)**: In-flight network request interception dropping cross-site ad networks, behavioral trackers, and telemetry beacons (Google Analytics/Ads, Meta Pixel cross-site, TikTok Analytics, Criteo, Taboola, Outbrain, Hotjar, Clarity, AppsFlyer, Adjust, etc.) returning empty responses. Toggleable via dashboard menu.
  - **Global Privacy Control (GPC) & Do Not Track (DNT)**: Automatically injects `navigator.globalPrivacyControl = true` and `navigator.doNotTrack = '1'` into DOM environments to signal strict data protection preferences to web servers.
  - **Screen & App Switcher Privacy (`FLAG_SECURE`)**: Protects against shoulder surfing, screen capture, and prevents leaks in Android's recent apps switcher / task thumbnail previews. Toggleable via dashboard menu.
  - **Unconditional Hardware Sensor & Permission Denial**: Unconditionally rejects web geolocation requests (`onGeolocationPermissionsShowPrompt`) and denies camera, microphone, and sensor access requests (`onPermissionRequest`). Zero dangerous permissions declared.
  - **Automatic EXIF Metadata Scrubbing**: Strips GPS coordinates, device identifiers, timestamps, and camera metadata from photos before uploading. Orientation is normalized so photos remain upright. Enabled by default and toggleable via dashboard menu.
  - **Whitelisted Domain Isolation**: Strict per-platform domain perimeters: links leading to external domains are safely opened in your system's default browser.
  - Cleartext HTTP traffic completely blocked (HTTPS enforced across all layers).
  - Cloud backups and ADB device extraction disabled.
  - Zero third-party trackers, closed-source analytics, or proprietary ad SDKs.
- **Private Favourites (Anti-Profiling Bookmarks)**:
  - Save links, profiles, or favorite posts directly inside SocialVault without saving them on the social network itself—bypassing recommendation algorithms that profile user interests.
  - Stored locally in private storage, automatically sanitized with `UrlPolisher`, with a dedicated Favourites drawer / bottom sheet to search, edit titles, and launch.
- **Deep Linking, Share Target & Link Opener**:
  - **Integrated URL Polishing (Tracking & Telemetry Stripping)**: Inspired by Léon / ClearURLs, incoming links (from WhatsApp, external apps, or pasted in-app) are automatically sanitized before opening. Strips tracking tokens (e.g. `igsh` on Instagram, `_t`/`_r`/`sender_device` on TikTok, `si` on YouTube, `s`/`t` on X, `mibextid`/`fbclid` on Facebook, `utm_*`, `gclid`, and more) while strictly preserving functional video IDs and timestamps (`v`, `t`, `start`, `list`). Enabled by default and toggleable via the dashboard menu.
  - **Outgoing Clean Link Sharing**: Click or long-press Share in the navigation bar or menu to share URLs automatically scrubbed of tracking tokens and referral identifiers.
  - **In-App "Open Social Link"**: Quick-paste dialog with clipboard auto-detection and automatic link polishing. Validates URLs strictly against supported social platforms—rejects unsupported domains to preserve sandbox isolation.
  - **Direct Deep Linking (`ACTION_VIEW`)**: Open Instagram Reels, TikTok videos, YouTube clips, Tweets, and other social links directly from messaging apps (e.g. WhatsApp, Telegram, Signal) inside SocialVault with instant link polishing.
  - **Android Share Target (`ACTION_SEND`)**: Share text or links from any Android app into SocialVault to open them in their dedicated, sandboxed tab with tracking parameters stripped.
  - **Short-Link Resolution**: Built-in support for platform short domains (`instagr.am`, `vm.tiktok.com`, `vt.tiktok.com`, `youtu.be`, `t.co`, `fb.watch`, `fb.me`, `pin.it`, `lnkd.in`, `redd.it`).
- **Granular Per-Platform Cache & Data Management**:
  - **Clear Cache for Platform**: Purges temporary disk and RAM cache and CacheStorage for a specific social network without logging you out.
  - **Wipe Data (Log Out)**: Surgically removes cookies, LocalStorage, SessionStorage, IndexedDB, and deletes the platform's Multi-Profile container without touching your other accounts, logins, or app settings. Accessible both from the dashboard card options and directly from the tab's active menu.
- **Rich Media, In-App Video Extraction & Downloads**:
  - **In-App Cobalt Downloader (`cobalt.tools`)**: Integrated privacy-friendly bottom sheet to download media without leaving SocialVault or redirecting to an external browser. Automatically passes the active social post URL for rapid, high-quality downloads.
  - **Native YouTube Video Extraction**: Direct extraction powered by `NewPipeExtractor` with quality and format selection dialog.
  - **Native X (Twitter) Video Extraction**: Direct MP4 video stream extraction via syndication API with resolution selection dialog.
  - **Immersive Native App Full Screen Mode**: Navigate any social media platform edge-to-edge as if using the native application. Respects hardware display cutouts and camera notches (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` / `SHORT_EDGES`), consumes window insets, and resets safe-area bottom padding. Includes a floating 'Exit Full Screen' pill, system back gesture exit integration, and a toggle in the dashboard menu.
  - **Media Downloader & Long-Press Saving**: Long-press any photo or media link in any social app to save it directly to your `Download/` folder, copy link, or open in a new tab.
  - **Safe Filename Resolution**: Automatic filename sanitization (`DownloadHelper`) preventing path traversal attacks (`../`) and inferring accurate MIME types and extensions (preventing generic `.bin` files).
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

# Run unit tests (66 tests across 9 suites) and lint:
./gradlew check lint
```

---

## 🛡️ FOSS & Security Compliance

SocialVault has undergone rigorous open-source compliance and defensive security audits:
- **[FOSS Compliance Audit](foss_audit.md)**: Independent repository inclusion audit covering dependencies, licensing (Free Software / GPLv3-compatible Apache 2.0), zero binary blobs, and zero telemetry trackers.
- **[Security Architecture & Vulnerability Audit](security_audit.md)**: OWASP Mobile Application Security Verification Standard (MASVS v2.0) audit covering WebView multi-profile sandboxing, real-time tracker blocking, GPC/DNT injection, `FLAG_SECURE` screen privacy, intent sanitization, least privilege permissions, and data extraction protection.

---

## 📄 License

SocialVault source code is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.  
Compiled distribution binaries incorporating `NewPipeExtractor` are distributed under the terms of the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**.
