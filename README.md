# Tele-phone

Android teleprompter that records over the live camera. The script scrolls in a see-through box near the lens, so you read and still see yourself framed.

## Features

- Script editor with Markdown: `#` headings, `**bold**`, `*italic*`, `==highlight==`, `~~strike~~`, lists, `>` quotes, `---` rules. `[pause]` or any bracketed text renders as a stage cue.
- Import `.txt`, or share text into the app from Docs, Notes, etc.
- Overlay controls: speed, text size, background opacity, box height, alignment, mirrored text for beam-splitter rigs.
- Scrolling starts with the recording, after an optional 3/5/10 s countdown. Tap the text to pause.
- Camera: front/back, pinch zoom, tap to focus, exposure, torch.
- Video: 4K / 1080p / 720p, 24/30/60 fps, Standard/High/Max bitrate, stabilization. Unsupported combos step down automatically and the app says what it dropped.
- Bluetooth remotes. Start/stop on play-pause, headset hook, volume, Enter, camera key. Page turners (arrows, page up/down, next/prev) nudge the script. Space pauses scrolling.
- Takes save to `Movies/Teleprompter` through MediaStore, so Photos / Gallery list them as their own album.

## Build

Needs JDK 17+ and the Android SDK (compileSdk 35). Android 10+ on the device.

```
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. Release builds are signed with the local debug key so they sideload without setup. Swap in a real signing config before shipping anywhere else.

## Install

```
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or copy the APK to the phone and open it. Android asks once to allow installs from that source.
