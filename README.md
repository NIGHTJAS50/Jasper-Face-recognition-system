# Jasper Face Recognition System

Jasper is an Android application for on-device face recognition, attendance management, and access control. It is designed to process recognition locally on the Android device instead of sending face images to a remote recognition service.

## Project Overview

Jasper helps schools, offices, laboratories, and event organizers replace manual attendance and basic access checks with a camera-based workflow.

The application can:

- Register people using multiple face captures.
- Recognize registered people through the live camera.
- Record recognition events and attendance locally.
- Run attendance sessions with present and absent counts.
- Display authorized or denied access in kiosk mode.
- Show recognition history and daily analytics.
- Export and import registered-user backups as JSON.
- Work without a cloud face-recognition API or an internet connection during recognition.

## Important Privacy Note

Face recognition uses biometric information. Before deploying Jasper with real users:

- Obtain informed consent.
- Explain what information is collected and why.
- Protect the device and exported backup files.
- Define a retention and deletion policy.
- Restrict administrative access.
- Follow the privacy and biometric-data laws that apply to your organization and location.

The application is designed for local processing, but local data is still sensitive data. An offline design does not remove the need for access control and responsible data governance.

## Main Features

### Face registration

An administrator enters a person's name and captures several samples through the front or rear camera. Jasper checks that a face is visible and uses a small movement challenge to help confirm that the capture comes from a live person. The samples are converted into face embeddings and saved in the local Room database.

### Real-time recognition

The Recognize screen displays a live camera preview, detects faces, draws a face overlay, and reports the best matching registered person with a confidence value. Unknown faces are not treated as registered users.

### Attendance sessions

An attendance session has a name, a start time, an optional end time, and attendance entries. When a registered person is recognized during an active session, Jasper marks that person present. A cooldown prevents the same person from being repeatedly counted in a short period.

### Kiosk and access control

Kiosk mode provides a simplified access decision. A matching registered face produces an AUTHORIZED result; an unknown or rejected face produces ACCESS DENIED. This mode is suitable for a demonstration or controlled access workflow.

### History and analytics

Recognition events can be reviewed later. The analytics screen summarizes daily recognitions, unique users, average confidence, and attendance information.

### Backup and restore

Settings provides JSON export and import for registered users. Treat exported backup files as confidential because they contain identity and face-embedding data.

## How Recognition Works

For each usable camera frame, the recognition pipeline is approximately:

```text
Camera frame
    -> image rotation and bitmap conversion
    -> face detection with BlazeFace
    -> face alignment and crop to 112 x 112 pixels
    -> blur, brightness, and exposure checks
    -> temporal liveness check
    -> MobileFaceNet embedding generation
    -> L2 normalization
    -> cosine-similarity matching
    -> known, unknown, or rejected result
```

The application uses a two-stage decision model:

- A high-confidence result can show the welcome or authorization experience.
- A configurable recognition threshold controls when an event is logged as a recognized user.

Recognition is affected by lighting, camera quality, face angle, occlusion, and the quality of the registration samples.

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin 2.0.0 |
| Android UI | Jetpack Compose and Material 3 |
| Architecture | MVVM with ViewModels and StateFlow |
| Camera | CameraX |
| Face detection | MediaPipe Tasks Vision and BlazeFace |
| Face embedding | MobileFaceNet with TensorFlow Lite |
| Local database | Room over SQLite |
| Settings | Android DataStore Preferences |
| Backup format | Gson JSON |
| Minimum Android version | API 26 / Android 8.0 |
| Target Android version | API 34 |
| Java toolchain | Java 17 source compatibility; Java 21 is recommended for the build |

## Architecture

Jasper uses a single-activity, Compose navigation structure with MVVM:

```text
Compose screens
       |
ViewModels and StateFlow
       |
Repositories
       |
+----------------------+---------------------+
| Room database        | ML components       |
| DataStore settings   | CameraX             |
| Backup manager       | TensorFlow Lite     |
+----------------------+---------------------+
```

### Important packages

```text
app/src/main/java/com/jasper/app/
  data/db/              Room entities and DAOs
  data/repository/      Face and application data repositories
  data/backup/          JSON backup and restore
  ml/                   Detection, alignment, quality, liveness, and matching
  ui/screens/           Compose application screens
  ui/components/        Camera preview and face overlay components
  ui/state/             UI state models
  viewmodel/            Screen ViewModels
```

### Database tables

The Room database contains data for:

- Registered users
- Face embeddings
- Recognition events
- Attendance sessions
- Attendance entries

Embeddings are stored as binary data through Room type converters. The database name is `jasper_database`.

## Screens

The application has these main routes:

- **Home:** Registered users and navigation to application features.
- **Register Face:** Create a person and capture samples.
- **Recognize:** Live recognition with camera overlays.
- **History:** Previously logged recognition events.
- **Attendance:** Start, monitor, and end attendance sessions.
- **Analytics:** Daily summary statistics.
- **Kiosk:** Authorization and access-denied display.
- **Settings:** Thresholds, capture options, and backup/restore.

## Requirements

To build or run the project locally, install:

- Android Studio with Android SDK support.
- Android SDK Platform 34.
- Android SDK Build Tools compatible with API 34.
- Java 21 for the Gradle build.
- A physical Android device or an Android emulator.

A physical Android device is recommended for the face-recognition demonstration because its camera is generally more reliable than an emulator camera.

## Build the APK

Open the project directory in Android Studio and allow Gradle synchronization to finish. Then select the `app` configuration and run the debug build.

From PowerShell, when Gradle and Java are configured:

```powershell
./gradlew assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If the repository does not contain a Gradle wrapper in your checkout, use Android Studio's Gradle integration or install a compatible Gradle distribution. The project uses the documented dependency versions in `gradle/libs.versions.toml`.

## Install the APK

With a USB-connected Android device and USB debugging enabled:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

If Android blocks installation, enable installation from the source being used or install through Android Studio. The debug application ID is `com.jasper.app.debug` because the debug build uses an application ID suffix.

## Demonstration Procedure

A reliable demonstration takes approximately 7 to 10 minutes.

1. Open Jasper and show the Home screen.
2. Tap Register Face and enter a demo name.
3. Grant camera permission.
4. Capture the requested face samples.
5. Move the head slightly when the liveness prompt appears.
6. Open Recognize and demonstrate a registered face.
7. Show the face overlay and confidence result.
8. Test an unregistered face and show the unknown result.
9. Create an Attendance session such as `Demo Class`.
10. Recognize a registered person and show that they are marked present.
11. Open History to show the recognition event.
12. Open Analytics to show the daily statistics.
13. Open Kiosk mode and demonstrate AUTHORIZED and ACCESS DENIED states.
14. Show Settings and explain backup and threshold controls.

### Demo preparation checklist

- Charge the phone.
- Test camera permission before the presentation.
- Register at least one demo person in advance.
- Use even lighting and keep the face within the camera frame.
- Keep a second registered person or an unregistered person available for testing.
- Prepare screenshots or a screen recording as a fallback.
- Do not use real biometric data unless the participants have consented.

## Configuration

The Settings screen includes controls for:

- Recognition threshold. A higher value is stricter; a lower value is more permissive.
- Target number of captures per person.
- Automatic capture when a face is steady.
- Automatic capture delay.
- JSON export and import.

For a presentation, use the default threshold first. Only change it when explaining false positives and false negatives, because lowering it can make incorrect matches more likely.

## Troubleshooting

### Camera permission is denied

Open Android Settings, select Jasper, allow Camera permission, and restart the recognition screen.

### No face is detected

Use brighter, even lighting, face the camera directly, move closer, and remove obstructions from the face.

### Recognition confidence is low

Register more samples, improve the lighting, keep the face centered, and verify that the recognition threshold is appropriate.

### The APK will not install

Uninstall an older incompatible build or use:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Confirm that the device runs Android 8.0 or later and that USB debugging is enabled.

### The emulator camera does not work well

Configure the Android Virtual Device camera to use the host webcam. For a live presentation, prefer a physical Android device.

### Build configuration problems

Confirm that Android Studio can locate the SDK, that API 34 is installed, and that Java 21 is selected for Gradle. Do not commit a machine-specific `local.properties` file.

## Limitations

Jasper is a demonstration and application prototype, not a certified security system. It can be affected by:

- Poor or changing illumination
- Motion blur
- Extreme head angles
- Masks, sunglasses, or other occlusion
- Changes in hairstyle or appearance
- Similar-looking people
- Camera sensor limitations
- A small or poorly captured registration dataset
- Spoofing methods beyond the implemented liveness checks

For high-risk access decisions, combine recognition with human review or another authentication factor.

## Future Improvements

Potential future work includes:

- Encrypted storage for embeddings and backups.
- Administrator authentication and role-based permissions.
- Stronger hardware-assisted anti-spoofing.
- CSV and PDF attendance reports.
- Improved matching for larger user databases.
- Better support for masks and partial faces.
- Explicit consent, retention, and deletion workflows.
- Automated tests for recognition and attendance edge cases.
- Multi-device synchronization with secure end-to-end encryption.

## Project Documentation

More technical details are available in:

- [Jasper application documentation](docs/JASPER_APP_DOCUMENTATION.md)
- [Academic article](docs/JASPER_ACADEMIC_ARTICLE.md)

## License

No license file is currently included in this repository. Add an explicit license before distributing or reusing the project publicly.
