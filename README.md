# Time Trapper

![Time Trapper Logo](app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp)

Time Trapper is an Android app focused on **voluntary digital wellbeing**.
It detects when a monitored app comes to the foreground, asks the user how much time is allowed for that session, then enforces that choice when time is over.

## Highlights

- Foreground app detection using `AccessibilityService` (`TYPE_WINDOW_STATE_CHANGED`)
- System overlay prompt for session duration
- Session duration options: **1 min**, **2 min**, **5 min (default)**, **15 min**, or **No limit**
- One active session per monitored app
- Session persistence through app switches and screen-off states
- Full-screen blocking screen when session time expires
- Optional **Extend by 5 minutes** action
- Monitored apps list with editable **title + packageName**
- Setup UI that prioritizes missing permissions and de-emphasizes them once validated

## Tech Stack

- Language: Kotlin
- UI: XML (View system, no Compose)
- minSdk: 26 (Android 8.0)
- targetSdk: 34
- Persistence: `SharedPreferences`
- Timing: `Handler`-based ticker

## Project Structure

```text
com.ppenelon.timetrapper
│
├── MainActivity.kt
├── accessibility/
│   └── AppAccessibilityService.kt
├── overlay/
│   └── OverlayManager.kt
├── timer/
│   └── AppTimerManager.kt
├── storage/
│   └── AppPreferences.kt
├── ui/
│   └── BlockedActivity.kt
└── res/
    ├── layout/
    └── xml/
```

## How It Works

1. `AppAccessibilityService` listens for foreground window changes.
2. If the foreground app is monitored and has no active session, `OverlayManager` shows a blocking modal.
3. The user chooses a duration (or No limit).
4. `AppTimerManager` starts/stores session state.
5. When the timer expires, the app is blocked via Home action + `BlockedActivity`.

## Setup

1. Open the project in Android Studio.
2. Let Gradle sync.
3. Run the app on a physical device (recommended).
4. In the app:
   - Enable the accessibility service
   - Grant overlay permission
   - Add or edit monitored apps (title + packageName)

## Build

```bash
./gradlew assembleDebug
```

## Play Store Policy Positioning

This project is designed around:

- Voluntary time control
- Digital wellbeing support
- No root requirement
- Explicit user action for permission enabling and monitoring configuration

## Notes

- If overlay does not appear, verify both permissions are enabled.
- If you change launcher assets, update `docs/logo.webp` if you want the README logo to match.
