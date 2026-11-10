# Hiddify integration review — 2026-11-11

Combines Android network-callback / pending-request safety (draft PR #1) and desktop process restart/shutdown safety (draft PR #2) without changing `main`.

## Android
- Retry a missing network interface on a cancellable coroutine, end the retry at first success, and reject stale listener generations.
- On final listener shutdown settle pending network `get()` requests exceptionally; bound `get()` waits and dispose of cancelled waiters.
- Focused Kotlin unit tests: **all six passed in GitHub-hosted Gradle/JUnit4 CI** ([run 42911712372](https://github.com/AmirrezaFarnamTaheri/hiddify-app/actions/runs/42911712372)).

## Desktop
- Guard delayed and in-flight core process startup by lifecycle-generation tokens.
- If shutdown wins the race, stop the newly-created core process instead of adopting it.
- Four Dart tests are supplied and **all four passed in GitHub-hosted Flutter 3.38.5 CI** ([run 42911712372](https://github.com/AmirrezaFarnamTaheri/hiddify-app/actions/runs/42911712372)).

## Additional interface validation

The interface-lookup retry now rejects non-positive interface indices instead of forwarding `0` or `-1` to the native core. Two regression tests cover retries and a bounded invalid-only outcome. The two positive-index regressions passed in hosted JUnit CI on workflow run [42916383599](https://github.com/AmirrezaFarnamTaheri/hiddify-app/actions/runs/42916383599). An additional test verifies that a cancelled lookup propagates immediately instead of being retried. Expected current Android JUnit total: **9**, pending verification of the new workflow.

## Must verify before merging
- `flutter test test/hiddifycore/core_interface/core_process_restart_guard_test.dart`
- Run Android app unit tests, device network handover/airplane mode, no-interface and listener-unregister races.
- Desktop rapid crash/restart/shutdown, certificate changes and child process cleanup.
- Run full Flutter/Android build CI. This branch is **not release-ready**: passing the isolated helpers and Flutter guard tests does not verify native desktop integration, full app builds or device end-to-end behavior.

Individual PRs #1 and #2 remain open for review; avoid merging them on top of this integration without checking overlap.
