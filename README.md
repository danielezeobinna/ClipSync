# ClipSync

> **Sync your clipboard between Windows and Android — no cloud, no account, no limits.**

ClipSync connects your Windows and Android clipboards over your local network. Copy something on your PC and it appears on your phone within seconds. Tap the overlay bubble to push your Android clipboard to Windows. Private, fast, and completely offline.

---

## What It Does

Most clipboard sync tools require a cloud account, send your data through external servers, or only work within one ecosystem. ClipSync does none of that. It runs a lightweight HTTP server on your Windows machine and a foreground service on Android that poll each other every 2 seconds — entirely on your local network.

No account. No internet. No middleman.

---

## Features

### 🔄 Bidirectional Sync

- Copy on Windows → automatically appears on Android within 2 seconds
- Copy on Android → tap the overlay bubble to push to Windows instantly
- Handles large text that Android's native clipboard would truncate

### 🫧 Floating Overlay Bubble
- Always visible, always accessible — floats over any app
- Tap to sync Android → Windows
- Hold to open the ClipSync app
- Fully draggable — move it anywhere on screen

### 🔒 Privacy First
- Runs entirely on your local network
- Your clipboard content never leaves your own devices
- No cloud, no account, no third-party servers

### ⚡ Lightweight
- Windows agent runs silently in the system tray
- Android foreground service runs with a minimal silent notification
- Low battery and memory impact

### 🌐 Network Flexible
- Works on any shared WiFi network
- Works with [Tailscale](https://tailscale.com) for sync outside your home network

---

## How It Works

```
Windows (ClipSync.exe)
        ↕  Local WiFi — HTTP on port 5765
Android (ClipSync App)
        ↕  Foreground Service + Overlay Bubble
```

The Windows agent hosts a small HTTP server and monitors the clipboard for changes. The Android foreground service polls it every 2 seconds and updates the Android clipboard automatically. The overlay bubble lets you push the Android clipboard to Windows with a single tap.

---

## Setup

### Windows

**Option A — Just use the app**
1. Download `ClipSync.exe` from the [Releases](https://github.com/danielezeobinna/ClipSync/releases) page
2. Double-click and run — no Python needed
3. Click **Start Connection**
4. Note the IP address shown in the app

**Option B — Run from source**
1. Install Python 3.8+
2. Navigate to the `Windows` folder and install dependencies:
   ```
   pip install pyperclip pystray Pillow
   ```
3. Run:
   ```
   python clip_server.py
   ```

### Android

1. Install the APK from the [Releases](https://github.com/danielezeobinna/ClipSync/releases) page
2. Grant the following permissions when prompted:
   - **Display over other apps** — for the overlay bubble
   - **Notifications** — for the background service
3. Open ClipSync, enter your **Windows IP address** and tap **Connect**
4. The floating overlay bubble will appear — you're ready to sync!

---

## Usage

| Action | Result |
|---|---|
| Copy on Windows | Automatically syncs to Android within 2 seconds |
| Tap overlay bubble | Pushes Android clipboard to Windows |
| Hold overlay bubble | Opens the ClipSync app |
| Drag overlay bubble | Move it anywhere on screen |
| Tap Disconnect | Stops syncing and removes the overlay |

---

## Known Limitations

- Android 10+ restricts background clipboard reads due to OS-level privacy policies. Android → Windows sync requires tapping the overlay bubble — this is intentional behavior by Android, not a bug.
- Both devices must be on the same network, or connected via [Tailscale](https://tailscale.com) for remote use.
- The Windows agent must be running and sharing for sync to work.

---

## Building from Source

### Windows (exe)
1. Install PyInstaller:
   ```
   pip install pyinstaller
   ```
2. Run `build.bat` from the `Windows` folder — cleans previous builds and generates a fresh `ClipSync.exe` in the `dist` folder

### Android (APK)
1. Open the `Android` folder in Android Studio
2. Build → Generate Signed APK, or run directly on your device

---

## Requirements

### Windows
- Windows 10 or higher
- Python 3.8+ (only if running from source)

### Android
- Android 8.0 (API 26) or higher
- Same WiFi network as Windows (or Tailscale)

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Connect to the Windows HTTP server |
| `FOREGROUND_SERVICE` | Keep the sync service running in the background |
| `SYSTEM_ALERT_WINDOW` | Display the overlay bubble over other apps |
| `POST_NOTIFICATIONS` | Show the background service notification |
| `WAKE_LOCK` | Prevent the CPU from sleeping during active sync |

---

## Tech Stack

| Platform | Tech |
|---|---|
| Windows | Python, `http.server`, pystray, Tkinter, Pillow |
| Android | Kotlin, Jetpack Compose, OkHttp, Foreground Service, WindowManager Overlay |

---

## Developer

Built by **Daniel Eze**
GitHub: [@danielezeobinna](https://github.com/danielezeobinna)

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

*ClipSync — your clipboard, your network, your rules.*
