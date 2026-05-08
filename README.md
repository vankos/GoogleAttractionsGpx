# Stuff around

An Android app that generates GPX files with points of interest around given coordinates. Supports multiple data sources.

## Features

| Source | Description | API Key |
|---|---|---|
| **Google Places** | Attractions with rating ≥ 4.0 and ≥ 20 reviews | Required |
| **OpenStreetMap (Overpass)** | Tourist objects from OSM | Not required |
| **TripAdvisor** | Attractions via TripAdvisor Content API | Required |
| **Wikidata** | Cultural heritage objects from Wikidata | Not required |
| **Wikipedia** | Wikipedia articles by geo-coordinates | Not required |
| **iNaturalist** | Species not yet observed by the user within ±15 days of today | Not required |
| **Need a photo** | Wikidata objects without photos, useful for photo-mapping routes | Not required |

### Track Corridor (OsmAnd integration)

Discover POIs along a section of a GPX track loaded in OsmAnd. The app connects to OsmAnd via AIDL, lets you pick an active track, define a start/end distance (km) and corridor width (m), then runs existing POI sources inside that corridor. The resulting GPX can be sent back to OsmAnd or shared.

**Requirements**: OsmAnd or OsmAnd+ installed on the device.

## Requirements

- Android 9 (API 28) or higher
- Location permission (`ACCESS_FINE_LOCATION`)
- Java 17 or newer for Gradle/Android builds

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Run the app on a device or emulator.
4. Go to **Settings** and enter the required API keys.

## Settings

Open the **Settings** screen (⚙ icon in the top-right corner):

- **Places API Key** — Google Places API key (Nearby Search).
- **TripAdvisor API Key** — TripAdvisor Content API key.
- **iNaturalist Username** — your iNaturalist login for filtering unobserved species.
- **Need a photo → Exclusion categories** — `instance of` categories to skip when generating photo routes.

Default exclusions for **Need a photo**:

- `hotel`
- `hostel`
- `guest house`
- `apartment`
- `neighborhood`
- `quarter`
- `mahalle`
- `battle`
- `ancient city`
- `siege`

## Usage

### POI generation by coordinates

1. Enter coordinates in the `lat,lng` field, tap **Current**, or pick a point on the map.
2. Select one or more data sources.
3. Optionally tap the **color dot** next to any source to change its color in the palette — the chosen color is saved and applied to all waypoints from that source in the output file.
4. After generation the app will prompt you to open the file in an external app (e.g. OsmAnd, Locus Map).

### Track Corridor (OsmAnd)

1. Tap the **route icon** (↗) in the toolbar to open the Track Corridor screen.
2. Tap **Get Tracks from OsmAnd** — the app connects to OsmAnd and lists active/imported tracks.
3. Select a track. If it's the first time, you'll be asked to pick the corresponding GPX file from device storage (the mapping is cached for next time).
4. Enter **Start distance** (km), **End distance** (km), and **Corridor width** (m).
5. Tap **Generate** — the app extracts the track segment, computes a bounding box expanded by the corridor width, and runs POI discovery using your enabled sources.
6. After generation, tap **Send to OsmAnd** to import the result GPX directly, or **Share** to send it via the standard Android chooser.

Generated files are saved to `Android/data/com.example.googleAttractionsGpx/files/`.

## Architecture

```
presentation/   — Compose UI (MainActivity, SettingsScreen, TrackCorridorScreen)
domain/         — models (PointData, Coordinates) and interfaces (IGpxGenerator, SettingsRepository)
data/repository — generator implementations, SettingsRepositoryImpl, OsmAndConnection, CorridorCalculator, GpxTrackParser, TrackCacheRepository
```

## Build

```bash
./gradlew assembleDebug
```

If Gradle picks Java 11 on Windows, point it to a newer JDK first, for example:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
./gradlew assembleDebug
```

For a release build, set the following environment variables:

```
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

## Dependencies

- Jetpack Compose + Material 3
- Navigation Compose
- Google Play Services Location
- OkHttp
