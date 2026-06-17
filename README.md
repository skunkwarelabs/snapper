# Snapper

An Android fishing companion: log your catches, identify fish from a photo, find
fishable water near you, and look up what's biting (and what's legal) where you are.

Built by **SkunkWare**. The name is a pun — *snapper* the fish, *snapping* the photo.

## Features

- **Catch log** — photo, species, length/weight (imperial), time, and GPS location
  for every catch, stored locally (Room). Sort by newest / species / length / weight,
  with 🥇🥈🥉 medals on your biggest fish.
- **Fish ID** — point the in-app camera at a fish and get an on-device species guess
  (bundled TFLite classifier — no network needed), with a "fish or not" gate.
- **Map ("Go Fish")** — OpenStreetMap (osmdroid, no API key) with one-tap "find water
  here" via the Overpass API; rivers, lakes, and streams highlighted, plus your catches
  plotted as markers.
- **Around** — common gamefish for your area with photos, size ranges, seasons, and
  per-species field-guide details.
- **Regulations & native species** — bag/size limits and what's native to a spot.
- **Stats** — totals, distinct species, and high scores.

## Tech

Kotlin · Jetpack Compose · Material 3 (dynamic color) · Navigation Compose · Room (KSP) ·
Coil · CameraX · osmdroid · TensorFlow Lite. minSdk 24 / compileSdk 35.

## Building

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Some cloud-backed features (regulations, "Around" details) read a Gemini API key from
`local.properties`:

```properties
GEMINI_API_KEY=your_key_here
```

`local.properties` is gitignored. The app degrades gracefully (manual entry / on-device
ID) when the key is absent.

## License

[GNU GPL v3.0](LICENSE).
