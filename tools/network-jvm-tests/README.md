# Hiddify focused lifecycle regression checks

The independent Gradle project compiles only the two pure Kotlin helpers
(`InterfaceUpdateRetry`, `PendingNetworkRequests`) and their six existing
JUnit4 tests, with the coroutines dependency included:

```sh
gradle -p tools/network-jvm-tests test
```

This does **not** compile the Flutter app or Android services.

The separate Flutter test runs four Dart lifecycle-ownership scenarios:

```sh
flutter pub get
flutter test test/hiddifycore/core_interface/core_process_restart_guard_test.dart
```

Requirements: Gradle 8.7/JDK 17; Flutter 3.38.5 (per `pubspec.yaml`).
Both checks run on the integration PR; neither implies a production Android,
iOS, desktop or full Flutter release build.
