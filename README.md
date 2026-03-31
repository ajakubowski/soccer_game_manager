# Soccer Game Manager

Native Android app scaffold for youth soccer game management with:

- season and roster setup
- per-game lineup generation
- live game clock and scorekeeping by half
- substitution round planning
- local history and fairness metrics
- printable one-page lineup reports

## Current status

The source tree is implemented, but this laptop still needs Android build tooling before the app can be compiled:

- a working JDK 17+
- Android Studio or Android SDK command line tools
- Android SDK platform/build tools for the compile SDK in `app/build.gradle.kts`
- Gradle wrapper generation or a local Gradle installation

## Recommended setup on this Mac

1. Install Android Studio stable.
2. Install a JDK if Android Studio does not bundle one in your shell environment.
3. Open the project in Android Studio and let it install the Android SDK components for API 35.
4. Generate the Gradle wrapper if needed:

```bash
gradle wrapper
```

5. Build and run:

```bash
./gradlew assembleDebug
```

## Project shape

- `app/src/main/java/com/example/soccergamemanager/data`
  Local database, DAOs, settings storage, and repository.
- `app/src/main/java/com/example/soccergamemanager/domain`
  Soccer-specific rules, lineup generation, metrics, and report formatting.
- `app/src/main/java/com/example/soccergamemanager/ui`
  ViewModel, navigation, screens, and print helper.

## Notes

- The lineup engine is deterministic and balances group/position exposure using stored finalized-game history.
- Pregame assignments can be cycled manually; once a game is live/finalized, regeneration and edits are blocked.
- Score events are tied to the active half and substitution round for descriptive analytics later.
- Excel import is intentionally deferred; the workbook informed the initial data model and UX.
