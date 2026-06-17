# Map Guide Library

Carrot Guard can match the current screen against local guide files stored under:

```text
app/src/main/assets/map_guides/
```

Each guide is a JSON file. Example:

```json
{
  "id": "theme_stage_001",
  "name": "Theme Stage 1",
  "season": "Example",
  "note": "Short source or personal note.",
  "signature": [112, 118, 126],
  "tips": [
    "Place area damage near the first bend.",
    "Clear blockers that open central tower slots.",
    "Upgrade towers that cover multiple route segments."
  ]
}
```

The real `signature` should contain 64 numbers. Use the overlay button:

```text
Export guide JSON
```

It saves a ready-to-edit JSON file under:

```text
Documents/CarrotGuardOverlay/
```

Edit the JSON tips, then import it from the app main screen with:

```text
Import guide JSON
```

## Recommended Workflow

1. Open a real map in the game.
2. Tap `Export guide JSON`.
3. Save a normal screenshot too.
4. Edit the exported JSON name and tips.
5. Open Carrot Guard.
6. Tap `Import guide JSON`.
7. Start the overlay again.
8. Tap `Match guide` on the same map to verify it matches.

Imported guide files are copied into the app's private guide library. After importing, you do not need to rebuild the APK.

Use the main screen button:

```text
Manage guides
```

to view imported JSON files and delete guides you no longer want.

The app validates imported files before adding them. A valid imported guide needs:

- a `signature` array with exactly 64 numbers
- at least one item in `tips`
- valid JSON syntax

When matching a screen, the overlay shows the best match and up to three closest guide candidates with scores. Lower scores mean closer matches.

Use `Diagnostics` on the main screen to select a saved screenshot and inspect:

- the current match threshold
- the best guide candidates and scores
- the generated 64-value signature

Do not bundle third-party guide images unless you have permission. JSON notes and signatures are enough for matching and personal strategy hints.
