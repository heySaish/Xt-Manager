# ⚡ XT Manager

<p align="center">
  <b>A High-Performance Dual-Pane File Manager for Android & Termux powered by a Native Rust Core Engine.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin%20%7C%20Rust-blue.svg" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg" alt="UI Framework">
  <img src="https://img.shields.io/badge/License-MIT-orange.svg" alt="License">
</p>

---

## 🌟 Overview

**XT Manager** is an advanced, ultra-fast file manager built from the ground up for Android power users and Termux environments. Combining Jetpack Compose's modern declarative UI with a high-performance **Rust Native Filesystem Engine (`libxt_fs.so`)**, XT Manager easily handles directories containing tens of thousands of files with zero lag.

---

## 🔥 Key Features

### ⚡ Native Rust Core Engine (`native-fs`)
- **Direct System Scans**: Bypasses slow Java reflection and shell processes using direct C/Rust POSIX system calls.
- **SingleFlight Collapsing**: Prevents duplicate concurrent scans for the same directory path.
- **In-Memory Caching & Metrics**: Provides microsecond-level sorting and scanning statistics.

### 📂 Dual-Pane Management
- **Side-by-Side Panes**: Effortlessly copy, move, compare, and synchronize files across two independent panes.
- **Active Pane Highlighting**: Visual indicators show the active workspace.
- **Fast Scrollbar**: Smooth, hardware-accelerated scrollbar for large directories (>90 items).
- **Custom Density & UI Scaling**: Adjust row density, bottom bar scaling, and thumbnail pre-fetching.

### 📦 Complete Archive Subsystem
- **Supported Formats**: Full extraction, creation, and browsing support for `ZIP`, `7Z`, `TAR`, `GZ`, `BZ2`, `XZ`, `ZST`, `LZ4`, `TGZ`, `TBZ2`, `TXZ`, `TZST`, `TLZ4`, `RAR`, `CAB`, `ISO`, and `CPIO`.
- **Archive Navigation**: Browse archive contents virtually like normal directories.
- **Non-Blocking Background Tasks**: Throttled task queue prevents UI freezes during heavy compression/extraction operations.

### 🔒 Storage Access Framework (SAF) & SD Card Support
- **External SD Card & USB OTG**: Full read/write/delete support for secondary SD cards (`/storage/XXXX-XXXX`) and USB OTG drives using Android's `DocumentFile` SAF abstraction.
- **Fallback Operations**: Automatic fallback to SAF URIs if POSIX permissions are restricted.

### 💻 Embedded Terminal Emulator
- Integrated Termux terminal window allowing shell script execution and CLI operations directly inside the application.

---

## 🛠️ Architecture & Project Structure

```
Xt-Manager/
├── app/                              # Jetpack Compose Android Application
│   ├── src/main/java/com/xtmanager/
│   │   ├── core/
│   │   │   ├── filesystem/           # LocalFileSystem, SafStorageManager, ArchiveFileSystem
│   │   │   ├── logger/               # AppLogger infrastructure
│   │   │   ├── operations/           # OperationManager (Throttled StateFlow Task Queue)
│   │   │   ├── settings/             # DataStore Preferences Manager
│   │   │   └── thumbnail/            # Fast key ThumbnailManager
│   │   ├── ui/
│   │   │   ├── components/           # DualPaneView, PathBar, BottomBar, StartEllipsisText
│   │   │   ├── dialogs/              # OperationProgressDialog, Compress, Extract, ApkInstall
│   │   │   ├── drawer/               # AppNavigationDrawer & Storage Volume Scanner
│   │   │   └── ...                   # Settings & Preview screens
│   │   └── viewmodel/                # FileManagerViewModel State Management
├── native-fs/                        # Rust Native Core Engine (Cargo NDK)
│   ├── src/                          # Native filesystem listing, archive & cancel token logic
│   └── Cargo.toml
├── terminal-emulator/                # Terminal emulator core library
└── terminal-view/                    # Terminal Compose View wrapper
```

---

## 🏗️ Building from Source

### Prerequisites
- **JDK**: 17+
- **Android SDK**: API 34 (Build-Tools 34.0.0)
- **Rust Toolchain**: `stable` with `aarch64-linux-android` target
- **Cargo NDK**: `cargo install cargo-ndk`

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/heySaish/Xt-Manager.git
   cd Xt-Manager
   ```

2. **Build the Native Rust Library**:
   ```bash
   cd native-fs
   cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release
   cd ..
   ```

3. **Build the Android APK**:
   ```bash
   ./gradlew assembleDebug
   ```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🔑 Security & Keystore Configuration

To sign release builds safely, XT Manager loads credentials from environment variables or a local untracked `local.properties` file:

```properties
# local.properties (ignored by Git)
RELEASE_KEYSTORE_PATH=/sdcard/Xt-Manager-Keystore/release.keystore
RELEASE_KEYSTORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

Automatic backup resolution checks `/sdcard/Xt-Manager-Keystore/release.keystore` to preserve signing keys across app reinstalls.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).