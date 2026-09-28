# ZaloX

ZaloX is an Android privacy and usability module for Zalo, implemented with the Xposed API and designed to run under LSPosed. It demonstrates runtime method hooking, analysis of an obfuscated Android application, defensive hook isolation, and a small configuration UI written in Kotlin.

> [!IMPORTANT]
> The current class map targets **Zalo 26.09.01**. Obfuscated hooks may stop working after a Zalo update and must be revalidated before use.

## Features

- **Anti-Seen** — suppresses `mark_read` events before they are dispatched.
- **Anti-Recall** — blocks single-message, batch, and persistence paths used by `chat.undo`.
- **Ghost Mode** — best-effort suppression of outgoing presence updates while leaving incoming presence data intact.
- **Anti-Typing** — best-effort suppression of Zalo-owned text watchers that may schedule typing indicators.
- **Screenshot Bypass** — strips Android's `FLAG_SECURE` from Zalo windows.
- **Copy Bypass** — restores the standard text-selection action mode where Zalo installs a restrictive callback.
- **Runtime Settings** — exposes individual switches through a dedicated Android configuration screen.

## Technical Overview

The module entry point is registered in `app/src/main/assets/xposed_init`. When LSPosed loads the target package (`com.zing.zalo`), `ZaloXEntry` installs each hook independently. Every hook is wrapped in error isolation so a changed or missing obfuscated class does not disable the rest of the module.

Two hook styles are used:

1. **Version-specific hooks** target classes and methods identified through APKTool/JADX analysis. These cover read receipts, recalls, and presence handling.
2. **Framework-level hooks** target stable Android APIs such as `Window.setFlags` and `TextView.setCustomSelectionActionModeCallback`. These are less sensitive to Zalo obfuscation.

Preferences are stored by the configuration app and read from the hooked Zalo process. Changes take effect without rebuilding the module.

## Project Structure

```text
app/src/main/
├── assets/xposed_init             # Xposed entry-point declaration
├── kotlin/dev/longdo/zalox/
│   ├── ZaloXEntry.kt              # Package filtering and hook orchestration
│   ├── ZaloXPrefs.kt              # Cross-process module preferences
│   ├── hooks/                     # Individual runtime hooks
│   └── ui/MainActivity.kt         # Module configuration interface
└── res/                            # Android UI resources
```

## Requirements

- Android 9 (API 28) or newer
- Rooted Android device with Magisk and LSPosed
- Zalo 26.09.01 for the current obfuscated class map
- Java 17 and Android SDK 35 to build from source

## Installation

1. Download the APK from the repository's **Releases** page.
2. Install the APK on the target Android device.
3. Open LSPosed, enable **ZaloX**, and select Zalo as its scope.
4. Force-stop and reopen Zalo, or reboot the device.
5. Open ZaloX and enable only the features you want to test.

## Build

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Updating the Class Map

After a Zalo update:

1. Decompile the matching APK in an isolated research workspace.
2. Locate stable protocol or serialization markers such as `mark_read`, `chat.undo`, `isOnline`, and `lastActive`.
3. Trace their call sites and update the constants in `ZaloXEntry.kt`.
4. Validate every hook independently on a test account and device.
5. Update the compatibility statement and release notes.

## Known Limitations

- Ghost Mode and Anti-Typing are heuristic, best-effort hooks and may not block every network path.
- Hooks based on obfuscated names are tied to the documented Zalo version.
- The debug APK is intended for development and testing, not production distribution.
- Rooting, LSPosed, or modifying application behavior can reduce device security and may violate an application's terms of service.

## Responsible Use

This project is provided for educational Android research, interoperability testing, and personal experimentation on devices and accounts you are authorized to use. Do not use it to access other people's data, defeat account controls, or violate applicable law or platform policies.

Zalo and its trademarks belong to their respective owner. This repository is independent, unofficial, and is not affiliated with or endorsed by VNG Corporation. It does not distribute the Zalo application or its proprietary source code.

