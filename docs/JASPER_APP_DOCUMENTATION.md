# Jasper — Technical App Documentation

**Version:** 1.0.0
**Platform:** Android (API 26–34)
**Language:** Kotlin 2.0.0
**UI Framework:** Jetpack Compose (BOM 2024.09.00)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Package Structure](#3-package-structure)
4. [ML Pipeline](#4-ml-pipeline)
5. [Database Schema](#5-database-schema)
6. [Feature Modules](#6-feature-modules)
7. [Key Algorithms](#7-key-algorithms)
8. [Settings & Persistence](#8-settings--persistence)
9. [Backup & Restore](#9-backup--restore)
10. [Threading Model](#10-threading-model)
11. [Dependencies](#11-dependencies)
12. [Performance Characteristics](#12-performance-characteristics)
13. [Build Configuration](#13-build-configuration)

---

## 1. Overview

**Jasper** is an on-device face recognition application for Android. It performs all ML inference locally — no cloud calls, no internet permission required. The app covers:

- **Face Registration** — capture multiple face samples per person, stored as 128-dimensional embeddings
- **Real-Time Recognition** — live camera feed with bounding box overlays and confidence labels
- **Attendance Management** — session-based roll-call with per-user cooldown and liveness gating
- **Kiosk / Access Control** — full-screen AUTHORIZED / ACCESS DENIED display with audio feedback
- **Analytics Dashboard** — aggregate daily statistics from recognition history
- **Backup & Restore** — JSON export/import of all registered users across devices

---

## 2. Architecture

The app follows **MVVM (Model–View–ViewModel)** with a single-activity navigation pattern.

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                             │
│  Composable Screens  ◄──────  ViewModels (StateFlow)        │
│  (Jetpack Compose)             (androidx.lifecycle)         │
└──────────────────────────────────┬──────────────────────────┘
                                   │
┌──────────────────────────────────▼──────────────────────────┐
│                      Domain Layer                           │
│   FaceRepository   SettingsRepository   BackupManager       │
└──────────────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────┼──────────────────────┐
        │                          │                      │
┌───────▼──────┐   ┌───────────────▼──────┐   ┌──────────▼───┐
│  Room DB     │   │   ML Components      │   │  DataStore   │
│  (SQLite v3) │   │  FaceDetectorHelper  │   │  Preferences │
│  5 tables    │   │  FaceEmbedder        │   │  (settings)  │
└──────────────┘   │  LinearFaceMatcher   │   └──────────────┘
                   │  FaceQualityChecker  │
                   │  LivenessChecker     │
                   │  FaceAligner         │
                   └──────────────────────┘
```

**Data flow for a recognition event:**

```
Camera Frame (ImageProxy)
        │
        ▼
  rotate/convert to Bitmap
        │
        ▼
  FaceDetectorHelper.detect()  ──► BlazeFace TFLite
        │ List<Detection> (bounding boxes, normalized)
        ▼
  FaceAligner.align()  ──► 112×112 ARGB_8888 crop
        │
        ▼
  FaceQualityChecker.check()  ──► reject if blurry/dark/overexposed
        │
        ▼
  LivenessChecker.check(box)  ──► reject if STATIC (spoof)
        │
        ▼
  FaceEmbedder.embed()  ──► MobileFaceNet TFLite → 128-dim float[]
        │
        ▼
  L2-normalize
        │
        ▼
  LinearFaceMatcher.findBestMatch()
        │
        ▼
  RecognitionResult { label, confidencePercent, isKnown, boundingBox }
        │
        ├──► if isKnown + confidence ≥ 95% → WelcomeAnimation + TTS
        ├──► if isKnown + confidence ≥ threshold → log RecognitionEvent
        └──► if unknown for 15 consecutive frames → UnknownPersonAlert Snackbar
```

---

## 3. Package Structure

```
com.jasper.app/
├── JasperApplication.kt          — App singleton; lazy-init DB, repos, ML helpers
├── MainActivity.kt               — Single activity; NavHost with 8 routes
│
├── data/
│   ├── db/
│   │   ├── FaceDatabase.kt       — Room DB v3, migrations, singleton
│   │   ├── UserEntity.kt         — users table
│   │   ├── UserDao.kt
│   │   ├── EmbeddingEntity.kt    — face_embeddings table
│   │   ├── EmbeddingDao.kt
│   │   ├── RecognitionEventEntity.kt  — recognition_events table
│   │   ├── RecognitionEventDao.kt
│   │   ├── AttendanceSessionEntity.kt — attendance_sessions table
│   │   ├── AttendanceEntryEntity.kt   — attendance_entries table
│   │   ├── AttendanceDao.kt
│   │   ├── Converters.kt         — FloatArray ↔ ByteArray Room converter
│   │   └── UserStats.kt          — JOIN result projection
│   ├── repository/
│   │   ├── FaceRepository.kt     — single facade over all DAOs + ML
│   │   ├── SettingsRepository.kt — DataStore read/write
│   │   └── model/
│   │       ├── RecognitionResult.kt
│   │       └── RegisteredUser.kt
│   └── backup/
│       └── BackupManager.kt      — Gson JSON export/import
│
├── ml/
│   ├── FaceDetectorHelper.kt     — MediaPipe BlazeFace wrapper
│   ├── FaceAligner.kt            — Crop + resize to 112×112
│   ├── FaceEmbedder.kt           — MobileFaceNet TFLite, mutex-protected
│   ├── FaceQualityChecker.kt     — Laplacian + brightness gate
│   ├── LinearFaceMatcher.kt      — Two-phase cosine similarity matcher
│   ├── LivenessChecker.kt        — Temporal variance anti-spoofing
│   └── EmbeddingMatcher.kt       — Interface (swap for ANN at >500 users)
│
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt
│   │   ├── RegistrationScreen.kt
│   │   ├── RecognitionScreen.kt
│   │   ├── HistoryScreen.kt
│   │   ├── SettingsScreen.kt
│   │   ├── AttendanceScreen.kt
│   │   ├── KioskScreen.kt
│   │   └── AnalyticsDashboardScreen.kt
│   ├── components/
│   │   ├── CameraPreview.kt      — CameraX PreviewView + ImageAnalysis
│   │   └── FaceOverlay.kt        — Canvas bounding box + label overlay
│   ├── state/
│   │   ├── HomeUiState.kt
│   │   ├── RecognitionUiState.kt
│   │   ├── RegistrationUiState.kt
│   │   ├── AttendanceUiState.kt
│   │   └── AnalyticsUiState.kt
│   └── theme/
│       └── JasperTheme.kt        — Material3 light/dark theme
│
└── viewmodel/
    ├── HomeViewModel.kt
    ├── RegistrationViewModel.kt
    ├── RecognitionViewModel.kt
    ├── HistoryViewModel.kt
    ├── SettingsViewModel.kt
    ├── AttendanceViewModel.kt
    └── AnalyticsViewModel.kt
```

---

## 4. ML Pipeline

### 4.1 Face Detection — BlazeFace (MediaPipe)

| Parameter | Value |
|-----------|-------|
| Model | `face_detection_short_range.tflite` |
| Provider | MediaPipe Tasks Vision 0.10.14 |
| Running mode | `IMAGE` (synchronous) |
| Min confidence | 0.50 |
| Output | Normalized bounding boxes [0, 1] + keypoints |

The `FaceDetectorHelper` is app-scoped (initialized once in `JasperApplication`) and accessed thread-safely via `synchronized(initLock)` on first use.

### 4.2 Face Alignment — FaceAligner

- Takes the detection bounding box, applies a 10% padding on each side
- Crops the source bitmap, scales to exactly **112 × 112** pixels (ARGB_8888)
- Result is passed directly to `FaceQualityChecker` and `FaceEmbedder`

### 4.3 Face Quality Gate — FaceQualityChecker

Runs entirely in CPU without additional ML inference:

| Check | Method | Threshold | Rejection label |
|-------|--------|-----------|-----------------|
| Blur | Laplacian variance on grayscale | `< 60.0` | `BLURRY` |
| Dark | Mean luminance (ITU-R BT.601) | `< 40.0` | `TOO_DARK` |
| Overexposed | Mean luminance | `> 220.0` | `OVEREXPOSED` |

The Laplacian kernel used is: `center = -4, four orthogonal neighbours = +1`.
Variance is computed as: `σ² = E[x²] − μ²`

### 4.4 Feature Extraction — MobileFaceNet

| Parameter | Value |
|-----------|-------|
| Model | `mobilefacenet.tflite` |
| Input shape | `[1, 112, 112, 3]` float32 |
| Normalization | `(channel / 128f) − 1f` → range `[−1, 1]` |
| Output shape | `[1, 128]` float32 (model has built-in L2 Lambda layer) |
| Post-processing | Additional L2 normalization in `FaceEmbedder.l2Normalize()` |
| Thread safety | `Mutex` — all inference serialized to avoid TFLite race conditions |
| Buffer strategy | `ByteBuffer.allocateDirect` pre-allocated once; `rewind()` before each call |
| Num threads | 2 (`Interpreter.Options.numThreads`) |

**L2 Normalization:**

```
norm = sqrt(Σ eᵢ²)
normalized[i] = eᵢ / norm    (if norm > 1e-10 and all values finite)
```

### 4.5 Embedding Matching — LinearFaceMatcher

See [Section 7.1](#71-two-phase-linear-face-matcher) for the full algorithm.

**Similarity metric:** Cosine similarity via dot product of two L2-normalized vectors.

```
cos(a, b) = a · b  (when both ‖a‖ = ‖b‖ = 1)
```

**Confidence mapping:**

```
confidencePercent = ((similarityScore + 1) / 2) × 100
```

This maps the cosine similarity range `[−1, 1]` to a human-readable `[0%, 100%]` range.

---

## 5. Database Schema

**Room database name:** `jasper_database`
**Current version:** 3

### Table: `users`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `name` | TEXT | NOT NULL |
| `createdAt` | INTEGER | NOT NULL (epoch ms) |

### Table: `face_embeddings`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `userId` | INTEGER | NOT NULL, FK → users(id) ON DELETE CASCADE |
| `embedding` | BLOB | NOT NULL (128 × 4 bytes, FloatArray serialized via `Converters`) |
| `capturedAt` | INTEGER | NOT NULL (epoch ms) |

Index: `index_face_embeddings_userId`

### Table: `recognition_events`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `userId` | INTEGER | NOT NULL |
| `userName` | TEXT | NOT NULL |
| `confidencePercent` | REAL | NOT NULL |
| `recognizedAt` | INTEGER | NOT NULL (epoch ms) |

*Added in migration v1 → v2*

### Table: `attendance_sessions`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `name` | TEXT | NOT NULL |
| `startedAt` | INTEGER | NOT NULL (epoch ms) |
| `endedAt` | INTEGER | nullable (null = session active) |

*Added in migration v2 → v3*

### Table: `attendance_entries`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `sessionId` | INTEGER | NOT NULL, FK → attendance_sessions(id) ON DELETE CASCADE |
| `userId` | INTEGER | NOT NULL |
| `userName` | TEXT | NOT NULL |
| `firstSeenAt` | INTEGER | NOT NULL (epoch ms) |
| `lastSeenAt` | INTEGER | NOT NULL (epoch ms) |
| `scanCount` | INTEGER | NOT NULL, DEFAULT 1 |

Indices: `index_attendance_entries_sessionId`, `index_attendance_entries_userId`

*Added in migration v2 → v3*

### Migration History

| Migration | Change |
|-----------|--------|
| v1 → v2 | Added `recognition_events` table |
| v2 → v3 | Added `attendance_sessions` and `attendance_entries` tables with indices |

### FloatArray Converter

Embeddings are stored as raw bytes using `Converters.kt`:

```kotlin
@TypeConverter fun fromFloatArray(value: FloatArray): ByteArray
@TypeConverter fun toFloatArray(value: ByteArray): FloatArray
```

Uses `ByteBuffer.wrap().order(ByteOrder.nativeOrder())` for zero-copy conversion.

---

## 6. Feature Modules

### 6.1 Registration

**Entry point:** `RegistrationScreen.kt` / `RegistrationViewModel.kt`

**Flow:**
1. User enters a name in the text field
2. Camera preview starts (front camera default)
3. Auto-capture triggers when a face is steady (`FaceDetectorHelper` detects face AND `FaceQualityChecker` returns `OK`)
4. For each capture: `FaceAligner` → `FaceQualityChecker` → `LivenessChecker` → `FaceEmbedder` → store `EmbeddingEntity`
5. Progress shown as `Captured N / targetCaptures` (configurable, default 5)
6. On completion: compute **template embedding** (mean of all captured embeddings, re-normalized) and store
7. Profile photo: first acceptable capture bitmap saved to `files/profile_<userId>.jpg`

**Add More Captures:** Existing users can navigate to `register_add/{userId}` to append additional embeddings. New template is recomputed from all embeddings.

### 6.2 Recognition

**Entry point:** `RecognitionScreen.kt` / `RecognitionViewModel.kt`

**Frame processing (Dispatchers.Default):**
1. `ImageProxy` → rotate by sensor degrees → convert to `Bitmap`
2. Detect faces with `FaceDetectorHelper`
3. For each face: align → quality check → liveness check → embed → match
4. Display `RecognitionResult` list via `FaceOverlay` (color-coded bounding boxes)
5. Log recognized faces to `recognition_events` (once per unique face per recognition session)

**95% Confidence Gate:**
- When any result has `isKnown == true` AND `confidencePercent ≥ 95`:
  - Show `WelcomeCard` (deep navy, slide-up animation, 4-second auto-dismiss, tap-to-dismiss)
  - Speak `"Welcome, [Name]!"` via Android `TextToSpeech`
  - Suspend frame analysis (`imageAnalyzer = null`) for the duration
  - Camera preview remains live (no camera rebind)

**Unknown Person Alert:**
- 15 consecutive frames with detected faces but no known match → emit `unknownAlert` SharedFlow
- `RecognitionScreen` collects the flow and shows a `Snackbar` with message "Unknown person detected"
- Counter resets when a known face appears

**FPS display:** Live FPS counter shown top-right corner during active scanning.

### 6.3 Anti-Spoofing / Liveness Detection

**Component:** `LivenessChecker.kt` (Kotlin `object` singleton)

**Principle:** A live face undergoes micro-movements (breathing, natural hand tremor) that register as non-zero positional variance across frames. A printed photograph or screen replay is perfectly static.

See [Section 7.2](#72-liveness-checker) for the complete algorithm.

**Integration points:**
- `RecognitionViewModel.processFrame()`: STATIC faces have `isKnown` forced to `false`, label changed to `"Spoof?"`, and are not logged
- `AttendanceViewModel.processFrame()`: STATIC faces are skipped entirely (no DB write)
- `RegistrationViewModel`: liveness gate before embedding capture

### 6.4 Face Quality Gate

**Component:** `FaceQualityChecker.kt` (Kotlin `object` singleton)

Applied on the 112×112 aligned crop before embedding. Rejected frames are silently discarded; the auto-capture waits for the next frame.

**Thresholds:**

| Issue | Condition | Threshold |
|-------|-----------|-----------|
| BLURRY | Laplacian variance | < 60.0 |
| TOO_DARK | Mean luminance | < 40.0 |
| OVEREXPOSED | Mean luminance | > 220.0 |

### 6.5 History Screen

**Entry point:** `HistoryScreen.kt` / `HistoryViewModel.kt`

- Displays all `RecognitionEventEntity` rows in reverse chronological order
- Each row: user name, confidence%, formatted timestamp
- "Clear history" action with confirmation dialog (destructive, cannot undo)
- Data source: `RecognitionEventDao.getAllEvents(): Flow<List<RecognitionEventEntity>>`

### 6.6 Settings

**Entry point:** `SettingsScreen.kt` / `SettingsViewModel.kt`

Persisted via **DataStore Preferences** (`SettingsRepository.kt`):

| Setting | Key | Type | Default | Description |
|---------|-----|------|---------|-------------|
| Recognition threshold | `recognition_threshold` | Float | 0.60 | Cosine similarity cutoff (60%) |
| Target captures | `target_captures` | Int | 5 | Embeddings to collect per registration |
| Auto-capture enabled | `auto_capture_enabled` | Boolean | true | Trigger capture when face steady |
| Auto-capture delay | `auto_capture_delay_ms` | Long | 800 | Milliseconds before auto-capture fires |

The threshold slider shows `[threshold × 100]%` with a hint explaining lower = more permissive.

### 6.7 Attendance Mode

**Entry point:** `AttendanceScreen.kt` / `AttendanceViewModel.kt`

**Session lifecycle:**
1. User enters session name (e.g., "Monday Morning Class") and taps "Start"
2. Session row inserted into `attendance_sessions` with `startedAt = now`, `endedAt = null`
3. Camera starts; every frame is processed identically to Recognition mode
4. For each recognized face: check `lastLoggedAt[userId]` (30-second cooldown)
5. If cooldown passed AND liveness = LIVE: upsert `attendance_entries` row
6. Live attendance count shown as "Present: N / Total"
7. "End Session" sets `endedAt = now` and shows summary

**Cooldown (in-memory, no schema change):**
```
lastLoggedAt: HashMap<Int, Long>   // userId → epoch ms of last log
COOLDOWN_MS = 30_000L              // 30 seconds
```

Cooldown map and `LivenessChecker` buffers are reset on `startSession()` and `endSession()`.

**Past sessions** list displayed below the active session panel; expandable to show individual attendees.

### 6.8 Kiosk / Access Control Mode

**Entry point:** `KioskScreen.kt`

Full-screen mode designed for fixed installations (door control, turnstile):

- Shows camera preview behind a large status overlay
- **AUTHORIZED** (green, full-screen) when face matches with confidence ≥ threshold
- **ACCESS DENIED** (red, full-screen) when face is detected but not recognized
- Audio feedback: `ToneGenerator` beep on each state change
- Liveness check active: spoofed faces never trigger AUTHORIZED

### 6.9 Analytics Dashboard

**Entry point:** `AnalyticsDashboardScreen.kt` / `AnalyticsViewModel.kt`

Displays a 2×2 grid of today's statistics computed from the Room DB:

| Card | DAO Query | Icon |
|------|-----------|------|
| Recognitions today | `COUNT(*) WHERE recognizedAt ≥ startOfDay` | Visibility |
| Unique users today | `COUNT(DISTINCT userId) WHERE recognizedAt ≥ startOfDay` | Person |
| Avg confidence | `AVG(confidencePercent) WHERE recognizedAt ≥ startOfDay` | Speed |
| Attendance today | `COUNT(DISTINCT userId) FROM attendance_entries WHERE session.startedAt ≥ startOfDay` | Groups |

"Start of day" is computed as midnight of the current local date via `Calendar.getInstance()`.

Refresh button in TopAppBar re-runs all queries.

### 6.10 Backup & Restore

**Component:** `BackupManager.kt` (Gson serialization)

**Export format (JSON):**
```json
{
  "version": 1,
  "exportedAt": 1700000000000,
  "users": [
    {
      "id": 1,
      "name": "Alice",
      "createdAt": 1699000000000,
      "embeddings": [[0.032, -0.017, ...], ...]
    }
  ]
}
```

- User triggers via Settings → "Export backup" → system file picker (`CreateDocument`)
- Import via "Import backup" → `OpenDocument` → parses JSON, inserts only new users (ID not in DB)
- Profile photos are NOT included in the backup (embeddings are sufficient for recognition)

### 6.11 Welcome Animation

Triggered in `RecognitionScreen` when `confidencePercent ≥ 95f`:

- `WelcomeCard` composable: deep navy surface (`0xEE0A1744`), 24dp rounded corners, 12dp shadow
- Content: 72dp mint-green checkmark icon + "Welcome," subtitle + large bold name + "Tap to dismiss" hint
- Entry animation: `slideInVertically { it / 2 } + fadeIn()`
- Exit animation: `fadeOut()`
- Duration: 4 seconds auto-dismiss, or tap anywhere on card
- Voice: `TextToSpeech.speak("Welcome, [Name]!", QUEUE_FLUSH, null, "welcome_utterance")`
- Recognition paused while card is visible (`imageAnalyzer = null`)

### 6.12 Unknown Person Alert

Triggered in `RecognitionViewModel` after 15 consecutive frames with detected but unrecognized faces:

```
consecutiveUnknownFrames counter
    ├── frame has detected faces, none known → counter++
    │       └── if counter == 15 → _unknownAlert.tryEmit(Unit)
    └── frame has known face → counter = 0
```

`RecognitionScreen` collects `vm.unknownAlert` in a `LaunchedEffect(Unit)` and shows a red `Snackbar`.

---

## 7. Key Algorithms

### 7.1 Two-Phase Linear Face Matcher

```
Algorithm: LinearFaceMatcher.findBestMatch(query, users, threshold)

Input:
  query     : FloatArray[128]   — L2-normalized embedding of face to identify
  users     : List<RegisteredUser>  — each has templateEmbedding and embeddings[]
  threshold : Float             — cosine similarity cutoff (e.g., 0.60)
  NEAR_MARGIN = 0.05

Output: RecognitionResult { label, similarityScore, confidencePercent, isKnown }

── Phase 1: Template Scan ─────────────────────────────────────────
bestTemplateSim ← -1.0
bestUser ← null

FOR each user IN users:
    sim ← dot_product(query, user.templateEmbedding)
    IF sim > bestTemplateSim:
        bestTemplateSim ← sim
        bestUser ← user

IF bestTemplateSim < (threshold − NEAR_MARGIN):
    RETURN unknown(boundingBox, bestTemplateSim)   // far below threshold, skip Phase 2

── Phase 2: Individual Scan (near-threshold candidates only) ──────
bestSim ← bestTemplateSim
bestMatchUser ← bestUser

FOR each user IN users:
    IF dot_product(query, user.templateEmbedding) < (threshold − NEAR_MARGIN):
        CONTINUE   // skip users far below threshold

    FOR each emb IN user.embeddings[]:
        sim ← dot_product(query, emb)
        IF sim > bestSim:
            bestSim ← sim
            bestMatchUser ← user

isKnown ← (bestSim ≥ threshold)
RETURN RecognitionResult(
    label = if isKnown then bestMatchUser.name else "Unknown",
    similarityScore = bestSim,
    confidencePercent = ((bestSim + 1) / 2) × 100,
    isKnown = isKnown
)
```

**Complexity:**

| Phase | Complexity | Condition |
|-------|-----------|-----------|
| Phase 1 (template scan) | O(U × D) | Always |
| Phase 2 (individual scan) | O(K × D) | Only if best template score ≥ threshold − 0.05 |
| Total best case | O(U × D) | Query is far from all users |
| Total worst case | O((U + K) × D) | Query is near threshold for all users |

Where U = number of registered users, K = total individual embeddings across near-threshold users, D = 128 (embedding dimension).

### 7.2 Liveness Checker

```
Algorithm: LivenessChecker.check(box)

Input:  box — normalized RectF bounding box [0,1]
Output: LivenessStatus { COLLECTING | LIVE | STATIC }

Constants:
  BUFFER_SIZE = 5
  MIN_VARIANCE = 0.0003

key ← gridHash(box)   // coarse 10×10 grid hash of face center
buf ← buffers[key]    // ArrayDeque of (cx, cy) pairs

cx ← (box.left + box.right)  / 2
cy ← (box.top  + box.bottom) / 2
buf.append((cx, cy))

IF buf.size > BUFFER_SIZE: buf.removeFirst()
IF buf.size < BUFFER_SIZE: RETURN COLLECTING

Vx ← variance({ p.cx | p ∈ buf })
Vy ← variance({ p.cy | p ∈ buf })

IF (Vx + Vy) ≥ MIN_VARIANCE: RETURN LIVE
ELSE: RETURN STATIC

── Variance helper ────────────────────────────────────────────────
variance(values):
    μ ← mean(values)
    RETURN mean({ (v − μ)² | v ∈ values })
```

**Grid hash** buckets the face center to a 10×10 grid so that a face that moves slightly within the same region does NOT create a new buffer; a completely different face in frame gets a separate buffer.

### 7.3 Attendance Cooldown

```
Algorithm: AttendanceViewModel.processFrame() — per recognized user

State: lastLoggedAt: HashMap<Int, Long>   // userId → last log epoch ms
Constant: COOLDOWN_MS = 30_000

FOR each recognized user in frame:
    IF LivenessChecker.check(user.box) == STATIC: CONTINUE

    now ← System.currentTimeMillis()
    lastLog ← lastLoggedAt[user.id] ?: 0

    IF (now − lastLog) < COOLDOWN_MS: CONTINUE   // skip, still in cooldown

    lastLoggedAt[user.id] ← now
    db.upsertAttendanceEntry(sessionId, user.id, user.name, now)
```

---

## 8. Settings & Persistence

### DataStore Keys

```kotlin
object PreferenceKeys {
    val THRESHOLD       = floatPreferencesKey("recognition_threshold")  // 0.0–1.0
    val TARGET_CAPTURES = intPreferencesKey("target_captures")          // 1–20
    val AUTO_CAPTURE    = booleanPreferencesKey("auto_capture_enabled")
    val AUTO_DELAY_MS   = longPreferencesKey("auto_capture_delay_ms")
}
```

Defaults applied via `DataStore.data.catch { emit(emptyPreferences()) }.map { prefs -> ... }`.

### Settings Repository API

```kotlin
class SettingsRepository(context: Context) {
    val threshold: Flow<Float>
    val targetCaptures: Flow<Int>
    val autoCaptureEnabled: Flow<Boolean>
    val autoCaptureDelayMs: Flow<Long>

    suspend fun setThreshold(value: Float)
    suspend fun setTargetCaptures(value: Int)
    suspend fun setAutoCaptureEnabled(value: Boolean)
    suspend fun setAutoCaptureDelayMs(value: Long)
}
```

---

## 9. Backup & Restore

### Export Flow

1. Settings → "Export backup" → `ActivityResultContracts.CreateDocument("application/json")`
2. User selects destination file in system picker
3. `BackupManager.export(uri)` serializes all `UserEntity` + their `EmbeddingEntity` rows via Gson
4. Written as UTF-8 JSON to the selected URI via `ContentResolver.openOutputStream`

### Import Flow

1. Settings → "Import backup" → `ActivityResultContracts.OpenDocument`
2. User selects a `.json` backup file
3. `BackupManager.import(uri)` reads and deserializes the file
4. For each user in the backup: insert only if no user with the same name already exists
5. Reports count of newly imported users

### Backup Schema

```
BackupFile
├── version: Int = 1
├── exportedAt: Long (epoch ms)
└── users: List<BackupUser>
        ├── id: Int (original DB id, ignored on import)
        ├── name: String
        ├── createdAt: Long
        └── embeddings: List<List<Float>>  (one inner list = one 128-dim embedding)
```

---

## 10. Threading Model

| Work type | Dispatcher | Notes |
|-----------|-----------|-------|
| Frame capture callback | `Dispatchers.Default` | CameraX delivers on executor; VM launches coroutine |
| TFLite inference | `Dispatchers.Default` | Serialized by `Mutex` in `FaceEmbedder` |
| MediaPipe detection | `Dispatchers.Default` | Synchronous call, thread-safe via init lock |
| Room DB reads | `Dispatchers.IO` | All DAO suspend funs |
| Room DB writes | `Dispatchers.IO` | Explicit `withContext(IO)` in ViewModels |
| DataStore reads | `Dispatchers.IO` | Flow collection on default dispatcher |
| UI state updates | `Main` | `StateFlow` → `collectAsStateWithLifecycle` |

**Frame rate control:** `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` drops intermediate frames if the analysis coroutine is busy, preventing a backlog.

---

## 11. Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.0.0 | Primary language |
| KSP | 2.0.0-1.0.24 | Annotation processor (Room) |
| AGP | 8.5.0 | Android Gradle Plugin |
| Jetpack Compose BOM | 2024.09.00 | UI framework (Material3, Navigation, Lifecycle) |
| androidx.core-ktx | 1.13.1 | Android KTX extensions |
| kotlinx-coroutines-android | 1.8.1 | Coroutine runtime |
| androidx.activity-compose | 1.9.2 | ComponentActivity, setContent |
| navigation-compose | 2.8.0 | NavHost, NavController |
| lifecycle-viewmodel-compose | 2.8.5 | viewModel() composable factory |
| lifecycle-runtime-compose | 2.8.5 | collectAsStateWithLifecycle |
| CameraX (core/camera2/lifecycle/view) | 1.3.4 | Camera preview + ImageAnalysis |
| MediaPipe Tasks Vision | 0.10.14 | BlazeFace face detection |
| TensorFlow Lite | 2.16.1 | MobileFaceNet inference |
| TFLite Support | 0.4.4 | ImageProcessor utilities |
| Room (runtime/ktx/compiler) | 2.6.1 | SQLite ORM + migrations |
| DataStore Preferences | 1.1.1 | Settings persistence |
| Coil Compose | 2.7.0 | Async profile photo loading |
| Gson | 2.10.1 | JSON serialization for backup |

---

## 12. Performance Characteristics

### Per-Frame Timing (Approximate, mid-range Android device)

| Stage | Typical latency |
|-------|----------------|
| ImageProxy → Bitmap conversion | ~2–5 ms |
| BlazeFace detection | ~8–15 ms |
| Face alignment (crop + resize) | ~1–2 ms |
| Quality check (Laplacian) | ~0.5–1 ms |
| Liveness check (variance) | ~0.1 ms |
| MobileFaceNet inference | ~20–40 ms |
| LinearFaceMatcher (10 users, 5 embeddings each) | ~0.05 ms |
| **Total per frame (1 face)** | **~32–63 ms → ~16–31 FPS** |

### Memory

| Component | Size |
|-----------|------|
| MobileFaceNet model (TFLite) | ~2 MB |
| BlazeFace model (MediaPipe) | ~0.5 MB |
| Input ByteBuffer (pre-allocated) | 112 × 112 × 3 × 4 = 150,528 bytes (~147 KB) |
| Per-user template embedding | 128 × 4 = 512 bytes |
| Per-user individual embeddings (5) | 2,560 bytes |

### Optimizations Applied

| Optimization | Mechanism |
|-------------|-----------|
| No GC pressure in frame loop | Pre-allocated `ByteBuffer`, no new objects |
| Frame drop under load | `STRATEGY_KEEP_ONLY_LATEST` |
| TFLite thread safety | `Mutex` (no concurrent allocations) |
| DB write deduplication | 30-second cooldown map |
| Phase 2 matcher pruning | Skip users > NEAR_MARGIN below threshold |
| Template embedding | Single dot product per user vs. all individual embeddings |

---

## 13. Build Configuration

| Parameter | Value |
|-----------|-------|
| `compileSdk` | 34 |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` | 34 |
| `versionCode` | 1 |
| `versionName` | 1.0.0 |
| Java compatibility | 17 |
| JDK (local) | 21 (`org.gradle.java.home=C:\jdk-21`) |
| TFLite no-compress | `.tflite` files excluded from compression |
| JNI conflict resolution | `pickFirsts += "**/*.so"` (MediaPipe + TFLite native lib names clash) |
| Release build | `isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard optimized |

### Required Asset Files

Place these files in `app/src/main/assets/` before building:

| File | Source | Size |
|------|--------|------|
| `mobilefacenet.tflite` | Trained MobileFaceNet model | ~2 MB |
| `face_detection_short_range.tflite` | MediaPipe BlazeFace (bundled by tasks-vision dependency — verify via `mediapipe-tasks-vision-0.10.14.aar`) | ~0.5 MB |

### Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
<!-- RECORD_AUDIO not required; TTS uses pre-installed system voices -->
<!-- INTERNET not required; all ML is on-device -->
```

Camera permission is requested at runtime on first use of any screen that opens the camera.
