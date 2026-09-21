# Free and Open Source Software (FOSS) Compliance Audit

**Project:** SocialVault  
**Package / Application ID:** `com.heronikostudios.socialvault`  
**Repository:** `https://github.com/GabryDX/SocialVault`  
**Audit Date:** September 21, 2026  
**Auditor / Tool:** Automated & Manual Static Analysis Audit  
**Verdict:** **100% FOSS Client Compliant** (Eligible for F-Droid with `NonFreeNet` anti-feature tag)

---

## 1. Executive Summary

SocialVault has been audited for compliance with the Open Source Definition (OSI), the Free Software Definition (FSF), and the inclusion criteria established by independent repository platforms such as **F-Droid** and **IzzyOnDroid**.

* **Client Codebase:** 100% Free and Open Source under the **Apache License, Version 2.0**.
* **Third-Party Dependencies:** 100% Free Software (permissive Apache 2.0 and copyleft GPL 3.0 or later).
* **License Compatibility & Distribution:** The native client source code is licensed under Apache 2.0. The native YouTube media extraction component (`NewPipeExtractor`) is licensed under GPL-3.0-or-later. According to the Free Software Foundation (FSF), Apache 2.0 is fully compatible with GPLv3. Compiled release binaries combining both components are distributed under the terms of the **GNU General Public License, Version 3.0 or later (GPL-3.0-or-later)**. Both licenses are OSI-approved, FSF-certified Free Software, and completely compliant with F-Droid inclusion requirements.
* **Proprietary Binary Blobs:** **None** (zero proprietary `.jar`, `.aar`, `.so`, or binary blobs in the repository; only standard FOSS `gradle-wrapper.jar` present).
* **Trackers / Analytics / Ad SDKs:** **None** (0 trackers detected in application code; client actively incorporates runtime `TrackerBlocker` to drop remote network tracking beacons).
* **Remote Network Services:** Connects to proprietary third-party platforms chosen by the user (classified as `NonFreeNet` under F-Droid criteria).

---

## 2. Project License & Legal Compatibility

The SocialVault source code repository is licensed under:
* **Primary License:** [Apache License, Version 2.0](LICENSE)
* **OSI Approved:** Yes
* **FSF Approved:** Yes (Free Software)
* **SPDX Identifier:** `Apache-2.0`

### Dual / Combined License Compatibility Analysis

To enable privacy-preserving, direct YouTube stream extraction without proprietary YouTube API SDKs or Google Play Services, SocialVault integrates the open-source `NewPipeExtractor` library, licensed under **GPL-3.0-or-later**:

1. **FSF Compatibility Matrix:** The Free Software Foundation explicitly confirms that Apache 2.0 is compatible with GPLv3 ("The Apache License 2.0 is compatible with GPLv3, because it is a permissive license without copyleft restrictions that contradict GPLv3").
2. **Binary Derivative Works:** When Apache 2.0 source code is compiled together with GPLv3 dependencies into an Android executable package (APK), the resulting binary as a whole is governed by the terms of **GPLv3**.
3. **F-Droid Inclusion Compliance:** F-Droid accepts both Apache 2.0 and GPLv3 software. Because 100% of the combined codebase is available under Free Software licenses with zero proprietary binary blobs, SocialVault complies fully with F-Droid's Strict Inclusion Policy.

---

## 3. Dependency License Inventory

An audit of `releaseRuntimeClasspath` and compile-time dependencies was conducted using Gradle build inspection. Every direct and transitive runtime dependency was identified and verified against OSI and FSF registries:

| Artifact Group & Name | Resolved Version | License | License Type / Notes |
| :--- | :---: | :---: | :---: |
| `com.github.teamnewpipe:NewPipeExtractor` | `v0.26.5` | GPL-3.0-or-later | Copyleft FOSS (Media Extractor) |
| `com.squareup.okhttp3:okhttp` | `5.5.0` | Apache 2.0 | Permissive FOSS (HTTP client) |
| `com.android.tools:desugar_jdk_libs` | `2.1.5` | Apache 2.0 | Permissive FOSS (Core Library Desugaring) |
| `androidx.appcompat:appcompat` | `1.8.0` | Apache 2.0 | Permissive FOSS |
| `com.google.android.material:material` | `1.14.0` | Apache 2.0 | Permissive FOSS |
| `androidx.swiperefreshlayout:swiperefreshlayout` | `1.2.0` | Apache 2.0 | Permissive FOSS |
| `androidx.core:core-splashscreen` | `1.2.0` | Apache 2.0 | Permissive FOSS |
| `androidx.webkit:webkit` | `1.17.0` | Apache 2.0 | Permissive FOSS |
| `androidx.recyclerview:recyclerview` | `1.2.1` | Apache 2.0 | Permissive FOSS |
| `androidx.activity:activity` | `1.8.0` | Apache 2.0 | Permissive FOSS |
| `androidx.fragment:fragment` | `1.5.4` | Apache 2.0 | Permissive FOSS |
| `androidx.core:core` & `core-ktx` | `1.16.0` | Apache 2.0 | Permissive FOSS |
| `androidx.lifecycle:*` | `2.6.2` | Apache 2.0 | Permissive FOSS |
| `androidx.savedstate:savedstate` | `1.2.1` | Apache 2.0 | Permissive FOSS |
| `androidx.coordinatorlayout:coordinatorlayout` | `1.1.0` | Apache 2.0 | Permissive FOSS |
| `androidx.transition:transition` | `1.5.0` | Apache 2.0 | Permissive FOSS |
| `org.jetbrains.kotlin:kotlin-stdlib` | `2.2.10` | Apache 2.0 | Permissive FOSS |
| `org.jspecify:jspecify` | `1.0.0` | Apache 2.0 | Permissive FOSS |
| `com.google.errorprone:error_prone_annotations` | `2.15.0` | Apache 2.0 | Permissive FOSS |

### Development & Test-Only Dependencies

| Artifact Group & Name | Resolved Version | License | Scope |
| :--- | :---: | :---: | :---: |
| `junit:junit` | `4.13.2` | EPL 1.0 | Unit Tests only |
| `org.json:json` | `20260814` | The JSON License / Public Domain | Unit Tests only |
| `androidx.test.ext:junit` | `1.3.0` | Apache 2.0 | Instrumented Tests |
| `com.android.application` (AGP) | `9.4.0` | Apache 2.0 | Build Tool |
| `Gradle Wrapper` | `9.7.1` | Apache 2.0 | Build Tool |

> **Finding:** 100% of runtime and build dependencies are OSI-approved and FSF-recognized Free Software licenses. No proprietary vendor libraries or binary-only SDKs exist.

---

## 4. Binary Blob & Proprietary Code Audit

A recursive scan of the entire repository tree was conducted for unverified precompiled binaries:

| Asset Type | Scan Result | Status |
| :--- | :---: | :---: |
| Native libraries (`.so`) | None | ✅ Clean |
| Precompiled Java/Kotlin Archives (`.jar`, `.aar`) in repo | Only `gradle-wrapper.jar` | ✅ Clean |
| Google Play Services (`play-services-*`) | None | ✅ Clean |
| Firebase SDKs (`firebase-*`) | None | ✅ Clean |
| Proprietary Crash Reporters (Crashlytics, Bugsnag, Sentry) | None | ✅ Clean |
| Proprietary Ad SDKs (AdMob, Unity, AppLovin) | None | ✅ Clean |
| User Tracking / Analytics (Adjust, AppsFlyer, Segment) | None | ✅ Clean |
| Proprietary Binary Bitmaps (`.png`, `.jpg`, `.webp`) | None (100% Vector XML) | ✅ Clean |

---

## 5. Network Traffic, Privacy & Anti-Features

### Static Code Review
- **Chromium Multi-Profile Sandboxing:** On Android 8.0+ / Chromium 88+, leverages `WebViewFeature.MULTI_PROFILE` to assign distinct, isolated profile containers (`ProfileStore`) to each platform. Cookies, LocalStorage, IndexedDB, Service Workers, and HTTP caches are physically separated between platforms, preventing cross-site session leaking or tracking across tabs.
- **In-Flight Tracker & Telemetry Blocking:** Active interception in `shouldInterceptRequest` drops known third-party tracking beacons, ad networks, and telemetry scripts (Google Analytics/Ads, Meta Pixel, TikTok Analytics, Criteo, Taboola, Outbrain, Hotjar, Clarity, AppsFlyer, Adjust, etc.) returning empty responses.
- **Privacy Signal Injection (GPC & DNT):** DOM script injection provides `navigator.globalPrivacyControl = true` and `navigator.doNotTrack = '1'` to assert user privacy preferences.
- **In-App Cobalt Downloader:** Seamless integration with an open-source Cobalt web instance (`cobalt.tools`), sandboxed in a bottom sheet to enable media downloading without external browser redirection.
- **Private Favourites (Anti-Profiling):** Bookmarks are persisted strictly in private client storage outside social media servers, mitigating remote algorithmic profiling.
- **Hardened Domain Isolation:** Navigation is strictly restricted to domains whitelisted per platform (`Platform.isDomainAllowed()`). External navigation is safely dispatched via `ACTION_VIEW` to the system browser.
- **Cleartext Traffic:** Disabled (`usesCleartextTraffic="false"`).
- **Data Backup & Extraction:** Cloud backups and device ADB extraction are disabled (`backup_rules.xml`, `data_extraction_rules.xml`).
- **Telemetry Ingestion:** Zero analytics endpoints or tracking beacons exist in the app code.

### F-Droid Anti-Feature Assessment
Under the F-Droid Inclusion Policy, applications that interact with proprietary remote services must declare the appropriate Anti-Features:

| Anti-Feature | Applicable? | Reason |
| :--- | :---: | :--- |
| `NonFreeNet` | **Yes** | The application serves as a client wrapper for proprietary network services (Facebook, YouTube, TikTok, Reddit, Instagram, X, etc.). |
| `Tracking` | **No** | The client app injects no telemetry, tracking identifiers, or device fingerprinting, and actively blocks remote third-party trackers. |
| `NonFreeAdd` | **No** | No proprietary extensions or non-free addons are promoted. |
| `NonFreeAssets`| **No** | All assets are open-source XML vectors. |

---

## 6. Nominative Trademark Use

The application includes icons and names corresponding to third-party services (e.g., Facebook, YouTube, Instagram, TikTok, Reddit, X, Pinterest, LinkedIn, Threads, Twitch, Bluesky, Mastodon).

* **Usage Context:** Nominative fair use for the sole purpose of enabling the user to identify and launch the respective web services in their sandboxed browser container.
* **Implementation:** Icons are authored directly in Android Vector Drawable XML format and do not bundle proprietary binary asset packages.
* **Disclaimer:** As noted in the project `README.md`, all trademarks, service marks, and logos remain the property of their respective owners.

---

## 7. Build Reproducibility & Offline Compilation

The project uses modern Android Gradle Plugin 9.4.0 with standard, official repositories:
- Google Maven (`maven.google.com`)
- Maven Central (`repo.maven.apache.org`)
- JitPack (`jitpack.io`) for the open-source `NewPipeExtractor` library

The build does not execute closed-source scripts, proprietary Gradle plugins, or non-standard remote downloads during compilation.

```bash
# Verification command (clean release build + lint + 66 unit tests):
JAVA_HOME=/home/trollo/.jdks/jdk-21.0.12.1+1 ANDROID_HOME=/home/trollo/AndroidSDK \
  ./gradlew testDebugUnitTest lintDebug assembleRelease
```

---

## 8. Audit Conclusion & Compliance Checklist

- [x] Primary license is OSI-approved and FSF-free (Apache 2.0).
- [x] All runtime dependencies are 100% FOSS (Apache 2.0 and GPL 3.0+).
- [x] Legal compatibility verified for combined binary distribution under GPLv3.
- [x] No closed-source SDKs (Play Services, Firebase, Facebook SDK).
- [x] Zero tracking or advertising frameworks included in application code.
- [x] Active runtime `TrackerBlocker` drops third-party tracking beacons.
- [x] Zero precompiled binary blobs (`.so`, `.aar`, `.jar`) in source tree.
- [x] Build scripts use only official FOSS repositories.
- [x] F-Droid compatibility confirmed under `NonFreeNet` designation.

**Overall Certification:** SocialVault is fully compliant as Free and Open Source Software (FOSS).
