# Carrot Guard

Android floating strategy helper prototype.

The app reads the current screen through Android screen capture permission and shows a draggable overlay. It does not tap, automate gameplay, modify memory, change currency, speed up the game, or bypass purchases.

## Features

- Requests overlay permission.
- Requests Android screen capture permission.
- Shows a draggable floating overlay.
- Reads screen frames periodically.
- Shows basic strategy hints.
- Saves the latest captured screen frame to `Pictures/CarrotGuardOverlay`.
- Saves an annotated analysis image that marks possible route, tower slots, and blockers.
- Loads local guide JSON files from `assets/map_guides`.
- Imports guide JSON files from the Android file picker.
- Shows imported guide files in a manager screen.
- Shows guide details, tips, signature validity, and JSON errors.
- Deletes imported guide files from the manager screen.
- Validates imported JSON before adding it to the library.
- Matches the current screen against guide signatures.
- Shows top guide match candidates with scores.
- Exports a ready-to-edit guide JSON template from the current screen.
- Stores core static UI strings in Android resources.
- Adds a diagnostics screen for inspecting screenshot signatures and match candidates.

## Build

Use GitHub Actions or GitLab CI for cloud builds. See:

- `CLOUD_BUILD.md`
- `GITLAB_BUILD.md`

Local builds require Android Studio or Android SDK, JDK 17, and Gradle.

## Usage

1. Open Carrot Guard.
2. Enable overlay permission.
3. Start the screen analysis overlay.
4. Allow Android screen capture.
5. Open the game.
6. Drag the overlay by its title.
7. Tap `Save screenshot` to save the current frame.
8. Tap `Analyze map` to save an annotated image.
9. Tap `Match guide` to match the current screen against local guide files.
10. Tap `Export guide JSON` to save a guide template.
11. Edit the JSON tips with any text editor.
12. Open the app and tap `Import guide JSON`.
13. Tap `Manage guides` to review or delete imported guides.

## Next Steps

- Add a screenshot gallery/debug screen.
- Add OCR for coins, wave count, and timers.
- Improve strategy hints using map features.
