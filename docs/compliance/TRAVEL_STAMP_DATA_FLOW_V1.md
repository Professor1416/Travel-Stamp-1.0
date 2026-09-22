# Travel Stamp 1.0 — Privacy, Data-Flow & Systems Audit Inventory
**Document ID:** `TS-COMP-02-DATA-FLOW-V1`  
**Revision:** 2.0.0 (TS-COMP-02B Verification & Correction Pass)  
**Audit Scope:** Full repository source code audit (`Professor1416/Travel-Stamp-1.0`)  
**Audit Mode:** Strict Non-Invasive Audit (Codebase analysis only; zero production modifications)  
**Target File:** `docs/compliance/TRAVEL_STAMP_DATA_FLOW_V1.md`  
**Audit Timestamp:** 2026-09-21T02:45:00-07:00 (Local Time: `2026-09-21T02:45:00-07:00`, UTC: `2026-09-21T09:45:00Z`)  
**Environment/Repository State:** Workspace directory `/app/applet` (git repository metadata `.git` not present in build environment container; exact source tree state verified directly).

---

## Audit Confidence and Evidence Rules

This document adheres to strict factual verification rules:
1. **Source Code is Primary Truth:** Only statements directly verified by physical examination of the source code files, build configurations, and generated manifests are stated as facts.
2. **Runtime / Server / Provider Behavior Not Observable is Marked Unknown:** Behavior of external servers (such as the Cloudflare Worker proxy or upstream place search data providers) cannot be proven by inspecting this Android repository and is strictly marked as `EXTERNAL VERIFICATION REQUIRED`.
3. **Dependency Presence is Not Runtime Processing:** The existence of a dependency in `libs.versions.toml` or `app/build.gradle.kts` does not constitute active user data collection or processing. Exact dependency states are distinguished between:
   - `NOT PRESENT`: Not declared anywhere.
   - `VERSION-CATALOG ONLY`: Present in `gradle/libs.versions.toml`, but not referenced in `app/build.gradle.kts`.
   - `COMMENTED OUT`: Present in `app/build.gradle.kts` but commented out.
   - `BUNDLED DEPENDENCY`: Present as an active Gradle `implementation` dependency.
   - `IMPORTED IN SOURCE`: Has active `import` statements in Kotlin source files.
   - `INITIALIZED AT RUNTIME`: Initialized in `Application.onCreate()`, `Activity`, or WorkManager.
   - `ACTIVELY USED FOR USER DATA`: Transmits or processes user PII / journey data.
4. **Commented Sample Configuration is Not Active Configuration:** Sample XML comments in resource files (such as `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`) are not treated as active exclusions or inclusions.

---

## Section A: Audit Findings & Compliance Action Matrix

| ID | Finding | Evidence | Severity | Required Action |
|---|---|---|---|---|
| **COMP-F01** | **Android Backup Configuration Unconstrained** | `AndroidManifest.xml` declares `android:allowBackup="true"`. `data_extraction_rules.xml` and `backup_rules.xml` now explicitly exclude all private app storage domains (`root`, `file`, `database`, `sharedpref`, `external`) from `<cloud-backup>`, `<device-transfer>`, and `<full-backup-content>`. | **RESOLVED (TS-COMP-01)** | Explicit XML exclusion rules configured across all Android versions to prevent automatic cloud backup and device transfer of private travel logs, database records, and photos. |
| **COMP-F02** | **Bundled Unused Firebase / Google Play Dependencies** | `app/build.gradle.kts:80,105,115` actively bundles `platform(libs.firebase.bom)`, `libs.firebase.ai`, and `libs.firebase.appcheck.recaptcha`. However, zero Firebase classes are imported or initialized in `app/src/main/`. | **P1 (High)** | Remove or comment out unused `firebase.ai` and `firebase.appcheck.recaptcha` in `app/build.gradle.kts` to reduce APK footprint, eliminate attack surface, and prevent unintended manifest merges. |
| **COMP-F03** | **INTERNET Permission Direct Manifest Declaration** | `app/src/main/AndroidManifest.xml` explicitly declares `<uses-permission android:name="android.permission.INTERNET" />` for place search HTTPS queries. | **RESOLVED (TS-COMP-01)** | Directly declared in application manifest; does not rely on transient dependency inheritance. |
| **COMP-F04** | **Crashlytics and Crash Reporting Absent** | Neither Firebase Crashlytics nor third-party crash reporting SDKs (Sentry, Bugsnag) are present in `gradle/libs.versions.toml`, `build.gradle.kts`, or application source code. | **P2 (Medium)** | Evaluate whether crash reporting is required for production operations. If required, implement privacy-preserving crash reporting; if omitted, note intentional privacy-first stance in operations documentation. |
| **COMP-F05** | **R8 Code Shrinking / Obfuscation Disabled in Release** | In `app/build.gradle.kts:45`, `buildTypes.release` has `isMinifyEnabled = false`. | **P1 (High)** | Enable `isMinifyEnabled = true` and configure ProGuard rules in `proguard-rules.pro` before building Google Play production release AAB. |
| **COMP-F06** | **Location-Search External Retention & Processing Unknown** | `ProxyLocationSearchDataSource.kt` transmits queries to `https://travel-stamp-api.prashantdasnur11.workers.dev/v1/places/search?q={query}`. Server-side logging, proxy IP retention, and upstream provider processing cannot be verified from client code. | **P1 (High)** | Perform external compliance verification on the Cloudflare Worker script and document worker retention policies and third-party upstream providers (e.g. OpenStreetMap / Nominatim / Mapbox). |
| **COMP-F07** | **SOI (Survey of India) Production Geometry Not Yet Integrated** | Geographic visualization in `InkMapSpatialEngine.kt` uses an abstract 2D coordinate normalization and clustering engine. Official Survey of India border polygon compliance has not been integrated into the repository. | **P2 (Medium)** | Before deploying public map features covering Indian territory, ensure boundary representation complies with National Geospatial Policy and Survey of India guidelines. |

---

## Section B: Executive Summary & Data Governance Stance

Travel Stamp is architected as an **offline-first, local-custody personal travel log and passport application**. The primary data architecture stores user journeys, personal notes, moments, checklists, and earned travel stamps locally inside the Android sandboxed application environment using SQLite (Room) relational database and private internal storage.

### Core Architectural Findings:
1. **Local-Custody Storage Model:** All core user records (trips, moments, reflection notes, hyperlinks, checklists, travel stamps, journey locations) reside on the user's physical device inside `/data/user/0/com.aistudio.travelstamp.vjknpt/databases/travel_stamp_database` and private internal storage `/data/user/0/com.aistudio.travelstamp.vjknpt/files/moments/`.
2. **Minimalist Network Egress:** There is only **one (1)** active HTTP network integration in the entire production runtime: an anonymous place search query sent to a Cloudflare Worker proxy (`https://travel-stamp-api.prashantdasnur11.workers.dev/v1/places/search?q={query}`). No user identifiers, auth tokens, device IDs, or telemetry payloads are transmitted by app code.
3. **Absence of Tracker/Analytics SDKs:** The application does **NOT** initialize or bundle any advertising SDKs (AdMob, Meta Audience Network), tracking SDKs, commercial crash-reporting platforms (Firebase Crashlytics, Sentry, Bugsnag), or third-party analytics pipelines (Google Analytics, Mixpanel, Amplitude).
4. **Firebase Dependency Status:** `firebase-bom`, `firebase-ai`, and `firebase-appcheck-recaptcha` are actively bundled as dependencies in `app/build.gradle.kts`, but have **zero imports, zero initialization, and zero active runtime calls** in application source code.
5. **Active EXIF Privacy Stripping:** The media pipeline explicitly strips sensitive GPS metadata (`TAG_GPS_LATITUDE`, `TAG_GPS_LONGITUDE`, `TAG_GPS_ALTITUDE`, `TAG_GPS_TIMESTAMP`, etc.) from all photos before committing them to permanent journal storage (`PhotoUtils.kt:356-371`).
6. **Hardened Local Backup Architecture:** The custom backup subsystem exports portable archives (`.tsbackup` ZIP and legacy `.json`) directly to user-selected destinations with strict **Zip Slip vulnerability prevention** (`BackupManager.kt:349-354`).

---

## Section C: Android Manifest & Permission Truth Matrix

### 1. Direct App Manifest Declarations (`app/src/main/AndroidManifest.xml`)

Physical examination of `app/src/main/AndroidManifest.xml` confirms the following declarations:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
...
```

| Permission | In Direct App Manifest? | Protection Level | Purpose in Travel Stamp Code |
|---|---|---|---|
| `android.permission.INTERNET` | **YES** | Normal (Install-time) | Enables place-search queries via Cloudflare Worker proxy (`travel-stamp-api.prashantdasnur11.workers.dev`). |
| `android.permission.CAMERA` | **YES** | Dangerous (Runtime) | Enables in-app photo capture for travel moments using `ActivityResultContracts.TakePicture()`. |
| `android.permission.POST_NOTIFICATIONS` | **YES** | Dangerous (Runtime, API 33+) | Delivers pre-trip preparation and packing notifications scheduled via WorkManager. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | **YES** | Normal (Install-time) | Required by AndroidX WorkManager internals (`SystemAlarmService`, `SystemJobService`) to reschedule pending reminder alarms across system reboots. No application-owned broadcast receiver exists for this action. |

---

### 2. Merged Manifest Manifestation (Debug vs. Release)

Verification executed via `gradle :app:processDebugMainManifest`, `gradle :app:processReleaseManifest`, and analysis of `manifest-merger-debug-report.txt` and `manifest-merger-release-report.txt`:

| Permission | Direct App Manifest | Merged Debug Manifest | Merged Release Manifest | Contributing Artifact(s) |
|---|---|---|---|---|
| `android.permission.INTERNET` | **YES** | **YES** | **YES** | App Manifest (`app/src/main/AndroidManifest.xml`) |
| `android.permission.CAMERA` | **YES** | **YES** | **YES** | App Manifest |
| `android.permission.POST_NOTIFICATIONS` | **YES** | **YES** | **YES** | App Manifest |
| `android.permission.RECEIVE_BOOT_COMPLETED` | **YES** | **YES** | **YES** | App Manifest |
| `android.permission.ACCESS_NETWORK_STATE` | **NO** | **YES** | **YES** | `androidx.work:work-runtime:2.10.0`<br>`com.google.firebase:firebase-ai:17.13.0` |
| `android.permission.WAKE_LOCK` | **NO** | **YES** | **YES** | `androidx.work:work-runtime:2.10.0` |
| `android.permission.FOREGROUND_SERVICE` | **NO** | **YES** | **YES** | `androidx.work:work-runtime:2.10.0` |
| `com.google.android.providers.gsf.permission.READ_GSERVICES` | **NO** | **YES** | **YES** | `com.google.firebase:firebase-ai:17.13.0` |

> **Manifest Compliance Verification:** `android.permission.INTERNET` is explicitly declared in `app/src/main/AndroidManifest.xml`, ensuring network place search functions independently of any third-party AAR dependency manifest contributions. No location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) or broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`) are requested.

---

### 3. Location Permissions & Hardware Telemetry Audit

| Hardware Capability / API | Declared in Manifest? | Present in Dependency Catalog? | Bundled in build.gradle.kts? | Used in Source Code? |
|---|---|---|---|---|
| `android.permission.ACCESS_FINE_LOCATION` | **NO** | N/A | N/A | **NO** |
| `android.permission.ACCESS_COARSE_LOCATION` | **NO** | N/A | N/A | **NO** |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | **NO** | N/A | N/A | **NO** |
| `com.google.android.gms:play-services-location` | N/A | **YES** (`libs.play.services.location`) | **COMMENTED OUT** (`// implementation(libs.play.services.location)`) | **NO** |
| `FusedLocationProviderClient` | N/A | N/A | N/A | **NO** (0 usages) |
| `LocationManager` | N/A | N/A | N/A | **NO** (0 usages) |

The application does not access device GPS, Wi-Fi location, cell tower triangulation, or on-device location hardware.

---

### 4. Storage & Media Permissions Audit

| Storage Permission | Declared in Manifest? | Used in Source Code? | Mechanism Used Instead |
|---|---|---|---|
| `android.permission.READ_EXTERNAL_STORAGE` | **NO** | **NO** | Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) |
| `android.permission.WRITE_EXTERNAL_STORAGE` | **NO** | **NO** | Scoped Storage / Private internal app storage |
| `android.permission.READ_MEDIA_IMAGES` | **NO** | **NO** | Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) |
| `android.permission.READ_MEDIA_VISUAL_USER_SELECTED` | **NO** | **NO** | Android Photo Picker |

The application uses zero-permission media selection and scoped storage.

---

### 5. Exported Android Components & FileProvider

| Component | Class | Exported | Configuration & Permissions | Privacy & Security Risk Assessment |
|---|---|---|---|---|
| `<activity>` | `com.example.MainActivity` | `true` | `android.intent.action.MAIN`<br>`android.intent.category.LAUNCHER` | **Safe.** Standard app launcher entry point. Supports `onNewIntent` for deep navigation when user taps local notification (`EXTRA_TRIP_ID`). |
| `<provider>` | `androidx.core.content.FileProvider` | `false` | `android:authorities="${applicationId}.fileprovider"`<br>`android:grantUriPermissions="true"` | **Safe.** Non-exported. Safely generates temporary, permission-granted `content://` URIs for sharing exports and camera captures. |

**FileProvider Path Mapping (`app/src/main/res/xml/file_paths.xml`):**
```xml
<paths>
    <cache-path name="photos" path="photos/" />
    <cache-path name="stamps" path="stamps/" />
    <cache-path name="posters" path="posters/" />
    <cache-path name="backups" path="backups/" />
    <files-path name="moments" path="moments/" />
</paths>
```

---

## Section D: Android Backup & Data Extraction Truth

Physical inspection of `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`, and `app/src/main/res/xml/data_extraction_rules.xml` reveals:

### 1. Actual Manifest Configuration
```xml
android:allowBackup="true"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

### 2. Actual Content of `res/xml/backup_rules.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="root" path="." />
    <exclude domain="file" path="." />
    <exclude domain="database" path="." />
    <exclude domain="sharedpref" path="." />
    <exclude domain="external" path="." />
</full-backup-content>
```

### 3. Actual Content of `res/xml/data_extraction_rules.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </device-transfer>
</data-extraction-rules>
```

### 4. Verified Backup Behavior:
- **`android:allowBackup`:** Maintained as `true` in manifest while paired with strict exclusion rules.
- **Cloud Backup Explicit Rules:** All storage domains (`root`, `file`, `database`, `sharedpref`, `external`) are explicitly excluded. Automatic OS cloud backup of Room database records, moment photos, notes, and preferences is completely disabled.
- **Device Transfer Explicit Rules:** All storage domains (`root`, `file`, `database`, `sharedpref`, `external`) are explicitly excluded. Automatic device-to-device migration for V1 is disabled.
- **Legacy Full Backup Explicit Rules (Android 11 and lower):** All storage domains (`root`, `file`, `database`, `sharedpref`, `external`) are explicitly excluded in `<full-backup-content>`.
- **Supported User-Controlled Backup Mechanism:** The existing custom `.tsbackup` archive export and restore workflow (`BackupManager`) remains the sole supported backup/migration mechanism. Users retain full sovereign custody of their journal archives.
- **Zero Privacy Egress:** No location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) or broad storage permissions (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`) are introduced.

---

## Section E: Room Database Schema & Data Dictionary

The local SQLite database is managed by Room (`TravelStampDatabase.kt`), configured with database file name `travel_stamp_database` at **Schema Version 8** (verified from source `@Database(..., version = 8, exportSchema = true)`).

### Entity Manifest (6 Entities):
1. `TripEntity` (`trips`)
2. `ChecklistItemEntity` (`checklist_items`)
3. `MomentEntity` (`moments`)
4. `TravelStampEntity` (`travel_stamps`)
5. `StampSequenceEntity` (`stamp_sequence`)
6. `JourneyLocationEntity` (`journey_locations`)

---

### Table 1: `trips` (`com.example.data.local.entity.TripEntity`)

| Column Name | SQLite Data Type | Nullable | Key / Index | Description & Privacy Classification |
|---|---|---|---|---|
| `id` | `INTEGER` | No | Primary Key (`autoGenerate = true`) | Local auto-incrementing surrogate primary key. |
| `uuid` | `TEXT` | No | Unique Index (`uuid`) | Random UUID v4 for sync/export stability. |
| `name` | `TEXT` | No | None | User-assigned expedition/trip name. **PII: User Journal Data**. |
| `destination` | `TEXT` | No | None | Destination name. **PII: User Travel History**. |
| `date` | `TEXT` | No | None | ISO date string (`YYYY-MM-DD`). Planned or travel date. |
| `startTimeMinutes` | `INTEGER` | Yes | None | Minute of day (0..1439, e.g. 540 = 09:00 AM) for trip departure. |
| `peopleCount` | `INTEGER` | No | None | Number of travelers accompanying the journey (default: 1). |
| `description` | `TEXT` | No | None | Free-form expedition narrative or packing notes. **PII: User Journal Data**. |
| `status` | `TEXT` | No | None | Trip status enum string: `"UPCOMING"`, `"IN_PROGRESS"`, `"ACTIVE"`, `"COMPLETED"`. |
| `stampEarned` | `INTEGER` (Boolean) | No | None | Boolean flag indicating whether certification stamp was minted. |
| `completedAt` | `INTEGER` | Yes | None | Epoch millisecond timestamp when trip was marked completed. |
| `reminderEnabled` | `INTEGER` (Boolean) | No | None | Boolean flag controlling whether pre-trip reminder notifications are active. |
| `reminderPreset` | `TEXT` | No | None | Reminder trigger preset string (`"ONE_DAY_BEFORE"`, `"DEPARTURE_TIME"`, etc.). |
| `reminderTimeMinutes`| `INTEGER` | Yes | None | Custom minute-of-day offset for notification triggering. |
| `createdAt` | `INTEGER` | No | None | Epoch millisecond timestamp of record creation. |
| `updatedAt` | `INTEGER` | No | None | Epoch millisecond timestamp of last record update. |
| `deletedAt` | `INTEGER` | Yes | None | Soft-delete tombstone timestamp (null if active). |

---

### Table 2: `moments` (`com.example.data.local.entity.MomentEntity`)

| Column Name | SQLite Data Type | Nullable | Key / Index | Description & Privacy Classification |
|---|---|---|---|---|
| `id` | `INTEGER` | No | Primary Key (`autoGenerate = true`) | Local auto-incrementing surrogate primary key. |
| `uuid` | `TEXT` | No | Unique Index (`uuid`) | Random UUID v4 for moment identification. |
| `tripId` | `INTEGER` | No | FK -> `trips(id)` ON DELETE CASCADE, Index | Associated parent trip identifier. |
| `category` | `TEXT` | No | None | Moment category enum: `"NOTE"`, `"PHOTO"`, `"HIGHLIGHT"`, `"QUOTE"`, etc. |
| `note` | `TEXT` | No | None | User's written reflection or photo caption. **PII: User Journal Content**. |
| `hyperlinksJson` | `TEXT` | Yes | None | Serialized JSON array of embedded web hyperlinks (`MomentHyperlink`). |
| `imageUri` | `TEXT` | Yes | None | Local filesystem path to permanent photo (`/data/.../files/moments/moment_...jpg`). |
| `timestamp` | `INTEGER` | No | None | Epoch millisecond timestamp of the recorded moment. |
| `createdAt` | `INTEGER` | No | None | Record creation timestamp. |
| `updatedAt` | `INTEGER` | No | None | Record update timestamp. |
| `deletedAt` | `INTEGER` | Yes | None | Soft-delete tombstone timestamp. |

---

### Table 3: `checklist_items` (`com.example.data.local.entity.ChecklistItemEntity`)

| Column Name | SQLite Data Type | Nullable | Key / Index | Description & Privacy Classification |
|---|---|---|---|---|
| `id` | `INTEGER` | No | Primary Key (`autoGenerate = true`) | Local surrogate key. |
| `uuid` | `TEXT` | No | Unique Index (`uuid`) | Random UUID v4. |
| `tripId` | `INTEGER` | No | FK -> `trips(id)` ON DELETE CASCADE, Index | Associated trip identifier. |
| `text` | `TEXT` | No | None | Item label/content (e.g. "Passport", "Boots"). **User Personal Data**. |
| `isCompleted` | `INTEGER` (Boolean) | No | None | Checkbox completion status. |
| `sortOrder` | `INTEGER` | No | None | Ordinal position in checklist UI. |
| `createdAt` | `INTEGER` | No | None | Creation timestamp. |
| `updatedAt` | `INTEGER` | No | None | Modification timestamp. |
| `deletedAt` | `INTEGER` | Yes | None | Soft-delete tombstone timestamp. |

---

### Table 4: `travel_stamps` (`com.example.data.local.entity.TravelStampEntity`)

| Column Name | SQLite Data Type | Nullable | Key / Index | Description & Privacy Classification |
|---|---|---|---|---|
| `id` | `INTEGER` | No | Primary Key (`autoGenerate = true`) | Local surrogate key. |
| `uuid` | `TEXT` | No | Unique Index (`uuid`) | Random UUID v4. |
| `tripId` | `INTEGER` | No | FK -> `trips(id)` ON DELETE CASCADE, Unique Index | One-to-one link to completed trip. |
| `stampNumber` | `INTEGER` | No | Unique Index (`stampNumber`) | Monotonic certification sequence number (1, 2, 3...). |
| `stampCode` | `TEXT` | No | None | Formatted official stamp code (e.g. `"#0001"`). |
| `title` | `TEXT` | No | None | Stamp title derived from trip name. |
| `destination` | `TEXT` | No | None | Stamp destination text. |
| `dateText` | `TEXT` | No | None | Formatted date string rendered on visual seal. |
| `peopleCount` | `INTEGER` | No | None | Count of companions commemorated on stamp. |
| `momentsCount` | `INTEGER` | No | None | Snapshot count of journal entries associated with stamp. |
| `inkColorHex` | `TEXT` | No | None | Hexadecimal color string (e.g. `"#1E3A2F"`) for visual rendering. |
| `stampStyle` | `TEXT` | No | None | Vector art style archetype (`"MOUNTAIN"`, `"COMPASS"`, etc.). |
| `inspectionText`| `TEXT` | No | None | Official certification motto string. |
| `issuedAt` | `INTEGER` | No | None | Epoch timestamp when stamp was sealed. |
| `createdAt` | `INTEGER` | No | None | Record creation timestamp. |
| `updatedAt` | `INTEGER` | No | None | Record update timestamp. |
| `completedAt` | `INTEGER` | Yes | None | Trip completion epoch timestamp. |
| `deletedAt` | `INTEGER` | Yes | None | Soft-delete tombstone timestamp. |
| `reflectionNote`| `TEXT` | Yes | None | User's retrospective journey reflection note. **PII: User Journal Data**. |

---

### Table 5: `journey_locations` (`com.example.data.local.entity.JourneyLocationEntity`)

| Column Name | SQLite Data Type | Nullable | Key / Index | Description & Privacy Classification |
|---|---|---|---|---|
| `id` | `INTEGER` | No | Primary Key (`autoGenerate = true`) | Local surrogate key. |
| `uuid` | `TEXT` | No | Unique Index (`uuid`) | Random UUID v4. |
| `tripId` | `INTEGER` | No | FK -> `trips(id)` ON DELETE CASCADE, Index | Associated trip identifier. |
| `label` | `TEXT` | No | None | Landmark or stop name. **Geospatial Reference**. |
| `latitude` | `REAL` (Double) | No | None | Geographic latitude (-90.0..90.0). Selected via search or curated catalog. |
| `longitude` | `REAL` (Double) | No | None | Geographic longitude (-180.0..180.0). Selected via search or curated catalog. |
| `sortOrder` | `INTEGER` | No | None | Itinerary display order index. |
| `createdAt` | `INTEGER` | No | None | Creation timestamp. |
| `updatedAt` | `INTEGER` | No | None | Modification timestamp. |

---

### Table 6: `stamp_sequence` (`com.example.data.local.entity.StampSequenceEntity`)

| Column Name | SQLite Data Type | Nullable | Primary Key | Description |
|---|---|---|---|---|
| `id` | `TEXT` | No | Primary Key (`"STAMP_COUNTER"`) | Singleton counter identifier. |
| `lastAllocatedNumber` | `INTEGER` | No | None | Monotonically incremented counter guaranteeing permanent, non-reused stamp numbers. |

---

### Table 7: Key-Value SharedPreferences (`travel_stamp_prefs.xml`)

Managed by `UserPreferencesRepositoryImpl.kt` (`context.getSharedPreferences("travel_stamp_prefs", Context.MODE_PRIVATE)`):

| Preference Key | Value Type | Default | Description |
|---|---|---|---|
| `key_onboarding_completed` | `Boolean` | `false` | Tracks whether user completed onboarding walkthrough. |
| `key_theme_mode` | `String` | `"SYSTEM"` | Selected application appearance: `"SYSTEM"`, `"LIGHT"`, or `"DARK"`. |
| `key_pre_trip_reminders` | `Boolean` | `true` | Master global toggle enabling/disabling all WorkManager journey notifications. |

---

## Section F: Dependency & Runtime Matrix

| Artifact / Library | Version in Catalog | Status in `app/build.gradle.kts` | Imported in Source? | Initialized at Runtime? | Actively Used for User Data? | Purpose / Assessment |
|---|---|---|---|---|---|---|
| **Firebase BOM** | `34.15.0` (`libs.firebase.bom`) | **BUNDLED DEPENDENCY** (`implementation(platform(libs.firebase.bom))`) | **NO** | **NO** | **NO** | Manages versions for Firebase dependencies. |
| **Firebase AI** | Undefined (BOM managed, `libs.firebase.ai`) | **BUNDLED DEPENDENCY** (`implementation(libs.firebase.ai)`) | **NO** | **NO** | **NO** | Unused. Contributes `INTERNET` and `ACCESS_NETWORK_STATE` permissions to merged manifest. |
| **Firebase App Check** | Undefined (BOM managed, `libs.firebase.appcheck.recaptcha`) | **BUNDLED DEPENDENCY** (`implementation(libs.firebase.appcheck.recaptcha)`) | **NO** | **NO** | **NO** | Unused. Contributes `recaptcha` and `INTERNET` to merged manifest. |
| **Firebase Auth** | Undefined (BOM managed, `libs.firebase.auth`) | **COMMENTED OUT** (`// implementation(libs.firebase.auth)`) | **NO** | **NO** | **NO** | Not compiled into APK. |
| **Firebase Firestore** | Undefined (BOM managed, `libs.firebase.firestore`) | **COMMENTED OUT** (`// implementation(libs.firebase.firestore)`) | **NO** | **NO** | **NO** | Not compiled into APK. |
| **Firebase Analytics** | **NOT PRESENT** | **NOT PRESENT** | **NO** | **NO** | **NO** | Not present anywhere in the project. |
| **Firebase Crashlytics** | **NOT PRESENT** | **NOT PRESENT** | **NO** | **NO** | **NO** | Not present anywhere in the project. |
| **Google Play Services Location** | `21.3.0` (`libs.play.services.location`) | **COMMENTED OUT** (`// implementation(libs.play.services.location)`) | **NO** | **NO** | **NO** | Not compiled into APK. |
| **Google Credential Manager / Google ID** | `1.5.0` / `1.1.1` | **COMMENTED OUT** | **NO** | **NO** | **NO** | Not compiled into APK. |
| **AndroidX WorkManager** | `2.10.0` (`libs.workRuntime`) | **BUNDLED DEPENDENCY** (`implementation(libs.androidx.work.runtime.ktx)`) | **YES** | **YES** | **YES** (Local notifications) | Schedules local trip reminders. |
| **OkHttp & Logging Interceptor** | `4.10.0` | **BUNDLED DEPENDENCY** | `OkHttpClient` **YES**; `HttpLoggingInterceptor` **NO** | `OkHttpClient` **YES** | **YES** (Place search HTTP transport) | HTTP client for place search; logging interceptor not attached. |
| **Retrofit & Moshi** | `2.12.0` / `1.15.2` | **BUNDLED DEPENDENCY** | **YES** | **YES** | **YES** | REST client & JSON parsing for location search. |
| **Coil Compose** | `2.7.0` | **BUNDLED DEPENDENCY** | **YES** | **YES** | **YES** | Async image rendering for local photos & icons. |
| **Room Runtime & KSP** | `2.7.0` | **BUNDLED DEPENDENCY** | **YES** | **YES** | **YES** | SQLite local database engine. |

---

## Section G: Network Egress & Place Search Implementation

### 1. Place Search Request Specification (from Source Code)
- **Base URL:** `https://travel-stamp-api.prashantdasnur11.workers.dev/` (`AppContainer.kt:104`)
- **Endpoint:** `v1/places/search` (`ProxyLocationSearchDataSource.kt:26`)
- **HTTP Method:** `GET`
- **Query Parameter:** `q` (e.g. `GET /v1/places/search?q=Manali`)
- **Query Validation & Limits:**
  - `query.trim()`: Non-empty check (`ProxyLocationSearchDataSource.kt:38`, `TravelViewModel.kt:114`)
  - Maximum query length enforced: **250 characters** (`LocationSearchRepositoryImpl.kt:15-17`)
- **Client-Side OkHttp Configuration:**
  - Connect Timeout: 10 seconds
  - Read Timeout: 10 seconds
  - Attached Interceptors: **NONE** (`OkHttpClient.Builder().connectTimeout(...).readTimeout(...).build()`).
  - No `HttpLoggingInterceptor` is attached.
  - Cookies / Auth Headers / Device Identifiers: **NONE** added by application code.
- **Payload Fields Returned:**
  - `candidates`: List of place objects, each containing:
    - `label`: String (name of place/landmark)
    - `secondaryLabel`: String? (state/country descriptor)
    - `latitude`: Double? (-90.0..90.0)
    - `longitude`: Double? (-180.0..180.0)
    - `category`: String? (place type)
  - Result truncated locally to a maximum of 5 items (`candidates.take(5)`).
- **Server-Side Behavior Assessment:**
  - Server-side IP logging, cloud worker edge analytics, upstream geocoding provider data retention: **EXTERNAL VERIFICATION REQUIRED** (cannot be determined from client Android repository).

### 2. Outbound Intent Boundary Inventory

| Source File & Line | Intent Action | Destination / MIME | Data Handed Over | User Trigger |
|---|---|---|---|---|
| `AboutScreen.kt:215` | `Intent.ACTION_VIEW` | `https://travelstamp.app/privacy` | External browser URI dispatch | User taps "Privacy Policy" link |
| `AboutScreen.kt:256` | `Intent.ACTION_SENDTO` | `mailto:support@travelstamp.app` | Recipient email address & prefilled subject | User taps "Contact & Support" |
| `LinkAnnotationUtils.kt:86` | `Intent.ACTION_VIEW` | External HTTP/HTTPS URI | Opens external web link from moment note | User taps hyperlink in journal |
| `PosterExportScreen.kt:959` | `Intent.ACTION_SEND` | `image/png` | Generated poster bitmap in `cacheDir/posters/` | User taps "Share Poster" |
| `SettingsScreen.kt:449` | `Intent.ACTION_SEND` | `application/octet-stream` | `.tsbackup` ZIP file in `cacheDir/backups/` | User taps "Export Backup" |
| `StampExporter.kt:482` | `Intent.ACTION_SEND` | `image/png` | Generated stamp card bitmap in `cacheDir/stamps/` | User taps "Share Stamp" |

---

## Section H: Media, Camera & Photo Lifecycle Flow

```
[User Camera Capture]            [Android Photo Picker]
         |                                  |
         v                                  v
cacheDir/photos/JPEG_..._temp.jpg   content:// URI (external provider)
         \                                  /
          \                                /
           v                              v
     [PhotoUtils.prepareWorkingImage()]
     - Max source size check: <= 50MB
     - Safe stream reading -> cacheDir/scratch_picker/picker_in_...tmp
     - Bounds-only decode -> inSampleSize calculation (power-of-2)
     - EXIF orientation normalization (Matrix rotate/flip)
     - Color space normalization -> sRGB (API 26+)
     - Max dimension constraint: <= 2560px
     - Compression -> JPEG Quality 86
     - Working copy written to cacheDir/photo_editor/working_...jpg
     - Immediate deletion of scratch file
     - Working directory auto-cleanup (max 5 files)
                    |
                    v
    [PhotoUtils.copyUriToPermanentStorage()]
     - Target: filesDir/moments/moment_<timestamp>.jpg
     - Verification: Non-zero bytes & stream copied
     - PRIVACY PURGE: All GPS EXIF attributes explicitly set to NULL
     - Cleanup: Removes temp camera file from cacheDir/photos/
                    |
                    v
    [Room Database: moments.imageUri]
     - Stores absolute path: /data/user/0/.../files/moments/moment_...jpg
```

### Media Verification Truth:
1. **Camera Permission:** Declared in manifest (`android.permission.CAMERA`). Used exclusively for capturing photos via `ActivityResultContracts.TakePicture()`. Camera file destination is isolated to `cacheDir/photos/`.
2. **Photo Picker:** App uses zero-permission `ActivityResultContracts.PickVisualMedia()`. No broad storage permissions are requested.
3. **EXIF GPS Metadata Purge:** `PhotoUtils.kt:356-371` explicitly nullifies all latitude, longitude, altitude, timestamp, and processing method EXIF tags before writing photos to permanent internal storage.
4. **Internal Storage:** Permanent photos are stored in app-private directory `filesDir/moments/`.
5. **Temporary Cache Cleanliness:** Scratch files in `cacheDir/scratch_picker/` are deleted immediately; editor cache files in `cacheDir/photo_editor/` are auto-pruned to a rolling window of 5 files.

---

## Section I: Background Work, Notifications & Reminders

1. **Permissions & Schedulers:**
   - Declares `android.permission.POST_NOTIFICATIONS` in manifest (requested at runtime on Android 13+).
   - Declares `android.permission.RECEIVE_BOOT_COMPLETED` in manifest. There are **zero application-owned broadcast receivers**; this permission is utilized exclusively by AndroidX WorkManager's internal background alarms to reschedule trip reminders across device reboots.
2. **WorkManager Worker:**
   - Worker class: `com.example.data.notification.TripReminderWorker` (extends `CoroutineWorker`).
   - Unique work name: `trip_reminder_{tripId}` with `ExistingWorkPolicy.REPLACE`.
3. **Fail-Closed Privacy Gates (`TripReminderWorker.evaluateSafety`):**
   - Aborts if `tripId <= 0` or trigger timestamp <= 0.
   - Aborts if trip record is missing or soft-deleted (`deletedAt != null`).
   - Aborts if trip status is `COMPLETED`, `stampEarned == true`, or `completedAt != null`.
   - Aborts if user disabled master reminder toggle (`key_pre_trip_reminders == false`) or trip-level toggle (`trip.reminderEnabled == false`).
   - Aborts if worker execution is delayed by more than 2 hours (`MAX_EXECUTION_LATENESS`).

---

## Section J: Backup, Restore & Archive Architecture

1. **Manual Backup Export Pipeline (`BackupManager.createExportFile`):**
   - Export format: `.tsbackup` archive (standard ZIP format).
   - Temporary file path: `cacheDir/backups/TravelStamp_Backup_<yyyy-MM-dd>.tsbackup`.
   - Contents:
     - `manifest.json`: App ID, Schema Version (3 in JSON export serializer), timestamp, item counts.
     - `data.json`: Serialized trips, moments, checklists, stamps, sequence, locations.
     - `media/`: Physical JPEG files from `filesDir/moments/` associated with active moments.
   - Egress: Dispatched via native Android share sheet (`Intent.ACTION_SEND`).
2. **Manual Backup Import Pipeline (`BackupManager.importBackup`):**
   - Stream inspection detects `.tsbackup` ZIP header or legacy `.json`.
   - **Zip Slip Defense (`BackupManager.kt:349-354`):** Verifies that `canonicalDest.startsWith(tempDir.canonicalPath)` before extracting any archive entry, preventing path traversal attacks.
   - Restores database records in an atomic Room transaction and copies media to `filesDir/moments/`.

---

## Section K: Logging, Diagnostics & Telemetry Audit

1. **Crashlytics / Analytics:**
   - No Google Analytics, Firebase Analytics, Crashlytics, Sentry, or Bugsnag SDKs exist in the project.
2. **Network Payloads:**
   - `HttpLoggingInterceptor` is not registered with `OkHttpClient`. Network request/response bodies are never written to Logcat.
3. **Logcat / Standard Output Statements:**
   - `SettingsScreen.kt:328`: `println("DEBUG_SWITCH_CLICKED: $enabled")` (toggles boolean state; zero PII).
   - `PosterExporter.kt`, `StampExporter.kt`, `PhotoUtils.kt`, `TravelNavHost.kt`, `BackupManager.kt`: Standard `e.printStackTrace()` on I/O or navigation exceptions. No user notes, trip names, photos, or coordinates are logged.

---

## Section L: Google Play Data Safety Form Readiness Guide

Based on physical evidence audited from the source code:

| Form Question / Category | Value / Declaration | Technical Explanation |
|---|---|---|
| **Data Collection / Sharing** | **Yes** | Place search query text is sent over HTTPS to Cloudflare Worker proxy; photos/backups can be shared by user action. |
| **Encryption in Transit** | **Yes** | All HTTP communication to the place search proxy uses TLS / HTTPS. |
| **Data Deletion Mechanism** | **Yes** | Users can delete individual trips, moments, checklists, and photos, which unlinks local database records and deletes internal files. |
| **Location Data** | **Not Collected via Hardware** | No GPS/Coarse location is accessed. Search text entered by the user is sent to the search proxy. |
| **Photos / Videos** | **Collected locally** | Stored on-device in private internal storage. Not uploaded to any cloud server by the app. |
| **Personal Info** | **None Collected** | No account creation, name, email, or user IDs collected by the app. |
| **Financial / Health / Contacts** | **None Collected** | Not requested or accessed. |
| **Device or other IDs** | **None Collected** | No advertising ID, IMEI, MAC address, or Android ID accessed or transmitted. |
| **Crashlytics / Diagnostics** | **None Collected** | No diagnostic telemetry pipeline present. |

---

**Audit Completed By:** Senior Android Privacy & Security Architecture Agent  
**Certification Status:** RE-VERIFIED AGAINST CURRENT SOURCE TREE (`Travel-Stamp-1.0`)
