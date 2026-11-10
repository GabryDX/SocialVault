# Security Policy

The SocialVault project takes the privacy and security of users seriously. SocialVault is an open-source, hardened social media vault and sandboxed web wrapper designed to isolate platform access, prevent cross-site tracking, strip metadata, and protect user data.

---

## Supported Versions

Only the latest release versions receive security patches and updates. We recommend always running the latest version available on GitHub Releases.

| Version | Supported |
| :--- | :--- |
| `1.1.x` | :white_check_mark: |
| `1.0.x` | :white_check_mark: |
| `< 1.0.0` | :x: |

---

## Reporting a Vulnerability

If you discover a security vulnerability, sandbox escape, tracker leakage, or credential exposure issue in SocialVault, please report it responsibly so that a fix can be developed and released before details are publicly disclosed.

### Preferred Reporting Method

- **GitHub Private Vulnerability Reporting:**  
  Please report vulnerabilities via GitHub's [Private Vulnerability Reporting](https://github.com/GabryDX/SocialVault/security/advisories/new). This ensures the report is shared only with project maintainers in an encrypted, confidential workspace.

### Alternative Contact

If you cannot submit a report through GitHub Advisories, you may contact the maintainer directly via email:
- **Email:** `gabrydx@hotmail.com`
- **Subject:** `[SECURITY] SocialVault Vulnerability Report`

### What to Include in Your Report

To help us investigate and reproduce the issue quickly, please include:
1. **Description:** A detailed explanation of the vulnerability and its potential privacy/security impact.
2. **Steps to Reproduce / PoC:** Clear, step-by-step instructions or proof-of-concept URL / link payload.
3. **Affected Versions:** The version of SocialVault and Android OS version / API level tested.
4. **Expected vs. Actual Behavior:** What should have occurred versus what actually happened.

### Response & Disclosure Process

- **Acknowledgement:** We will acknowledge receipt of your report within 48 hours.
- **Triage & Remediation:** We will assess severity, verify reproduction, and work on a fix in a private branch.
- **Release & Disclosure:** Once a fix is verified, a patch will be tagged and released on GitHub. An advisory acknowledging your responsible disclosure will be published upon mutual agreement.

---

## Core Security & Privacy Principles

SocialVault adheres to the **Principle of Least Privilege** and defensive engineering:

1. **Zero Dangerous Permissions:** SocialVault declares only `android.permission.INTERNET` access. No camera, microphone, contacts, location, or storage permissions are declared.
2. **Chromium Multi-Profile Isolation:** Each platform runs in a hermetic, segregated Chromium `Profile` container (`WebViewFeature.MULTI_PROFILE`). Cookies, LocalStorage, IndexedDB, and CacheStorage are physically separated between platforms.
3. **Real-Time In-Flight Tracker Blocking (`TrackerBlocker`):** Intercepts network requests in real time to drop known third-party tracking beacons, analytics scripts, and ad networks (Google Analytics/Ads, Meta Pixel, TikTok Analytics, Criteo, Hotjar, Clarity, AppsFlyer, etc.).
4. **Privacy by Default (`FLAG_SECURE`, GPC & DNT):** Protects against shoulder surfing and prevents task switcher thumbnail leaks in the Android recent apps overview (`FLAG_SECURE`). Automatically injects `Sec-GPC: 1` (Global Privacy Control) and `DNT: 1` (Do Not Track) into DOM environments.
5. **Automatic EXIF Metadata Scrubbing:** Automatically scrubs GPS coordinates, hardware serial numbers, and timestamps from media before upload while normalizing image orientation.
6. **Pre-Execution URL Polishing:** Automatically strips tracking tokens (`igsh`, `_t`, `_r`, `si`, `s`, `t`, `mibextid`, `fbclid`, `utm_*`) from incoming deep links, pasted URLs, and outgoing shares.
7. **No Cloud Synchronization & Backup:** Android Auto-Backup (`android:allowBackup="false"`) and ADB extraction (`android:fullBackupContent="false"`) are explicitly disabled in the manifest.

---

## Detailed Audits & Documentation

For complete technical specifications, threat model analysis, WebView sandboxing, and compliance documentation, please see:

- 🛡️ **[Comprehensive Security & Privacy Audit](docs/SECURITY_AUDIT.md)**: Deep dive into the threat model, WebView multi-profile sandboxing, real-time tracker blocking, GPC/DNT injection, and OWASP MASVS compliance.
- ⚖️ **[FOSS Compliance Audit](docs/FOSS_AUDIT.md)**: Full verification of 100% Free and Open Source dependencies, GPL-3.0 compatibility (`NewPipeExtractor`), zero binary blobs, and asset licensing.
