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

## Data coverage

All data is bundled offline (no API keys, no runtime calls for this data).

- **Fishing regulations** (bag/size limits, in-season status) — bundled for **all 50
  states**, shown on the **Regs** tab and in the map's water bubbles.
- **Fish stocking data** (which species have been stocked in a lake/river — the
  "Stocked here" line when you tap a water on the map) — pulled per state from each
  state's fish & wildlife agency, currently **24 states**. Refresh/extend with
  `tools/fetch_stocking.py`.

| State | Regs | Stocking |
|-------|:----:|:--------:|
| Alabama (AL) | ✓ | — |
| Alaska (AK) | ✓ | — |
| Arizona (AZ) | ✓ | — |
| Arkansas (AR) | ✓ | ✓ |
| California (CA) | ✓ | ✓ |
| Colorado (CO) | ✓ | — |
| Connecticut (CT) | ✓ | — |
| Delaware (DE) | ✓ | — |
| Florida (FL) | ✓ | ✓ |
| Georgia (GA) | ✓ | — |
| Hawaii (HI) | ✓ | — |
| Idaho (ID) | ✓ | ✓ |
| Illinois (IL) | ✓ | ✓ |
| Indiana (IN) | ✓ | ✓ |
| Iowa (IA) | ✓ | — |
| Kansas (KS) | ✓ | — |
| Kentucky (KY) | ✓ | — |
| Louisiana (LA) | ✓ | — |
| Maine (ME) | ✓ | — |
| Maryland (MD) | ✓ | ✓ |
| Massachusetts (MA) | ✓ | ✓ |
| Michigan (MI) | ✓ | ✓ |
| Minnesota (MN) | ✓ | — |
| Mississippi (MS) | ✓ | — |
| Missouri (MO) | ✓ | — |
| Montana (MT) | ✓ | ✓ |
| Nebraska (NE) | ✓ | ✓ |
| Nevada (NV) | ✓ | — |
| New Hampshire (NH) | ✓ | ✓ |
| New Jersey (NJ) | ✓ | ✓ |
| New Mexico (NM) | ✓ | ✓ |
| New York (NY) | ✓ | ✓ |
| North Carolina (NC) | ✓ | — |
| North Dakota (ND) | ✓ | ✓ |
| Ohio (OH) | ✓ | ✓ |
| Oklahoma (OK) | ✓ | — |
| Oregon (OR) | ✓ | — |
| Pennsylvania (PA) | ✓ | ✓ |
| Rhode Island (RI) | ✓ | ✓ |
| South Carolina (SC) | ✓ | — |
| South Dakota (SD) | ✓ | — |
| Tennessee (TN) | ✓ | ✓ |
| Texas (TX) | ✓ | — |
| Utah (UT) | ✓ | — |
| Vermont (VT) | ✓ | — |
| Virginia (VA) | ✓ | ✓ |
| Washington (WA) | ✓ | ✓ |
| West Virginia (WV) | ✓ | ✓ |
| Wisconsin (WI) | ✓ | ✓ |
| Wyoming (WY) | ✓ | — |

## Tech

Kotlin · Jetpack Compose · Material 3 (dynamic color) · Navigation Compose · Room (KSP) ·
Coil · CameraX · osmdroid · TensorFlow Lite. minSdk 24 / compileSdk 35.

## Building

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No API keys required — fish ID runs on-device (bundled TFLite model) and all
species/regulations data is bundled in `app/src/main/assets`.

## License

[GNU GPL v3.0](LICENSE).
