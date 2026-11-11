# Upstream adoption review — Hiddify

- Upstream source: `hiddify/hiddify-app` at `39f78fe0048ac7ec67583ff88c00e8fb54172a7d`.
- Original fork base: `05fb2b34af3c24b31d468e08cdf657ee083b2bed`.
- Fork-specific Android/default-network and desktop lifecycle changes: PR #3, merged.
- Non-blocking Android callbacks, KVM-enabled emulator smoke, and regression checks: PR #5, merged as `cab3d08cfaab682cfb9b5a48b3fc5e00118feb24`.

This record deliberately triggers a **new CI validation on the current fork base**
after PR #5 was merged; previous upstream-sync runs were against an older base
or were held for approval when the PR head was in another repository.

Acceptance for PR #6: Android debug APK assembly, Kotlin JVM regressions,
full Flutter tests and analysis, **and** Android emulator app startup/Wi-Fi
handover all succeed on this exact upstream-derived head. Do not release
or merge on skipped, cancelled, pending, or failing required checks. Keep the
fork's network-change and desktop restart protections in the effective merge.
Version-tag and manually dispatched release workflows are separate.

## Combined-branch CI memory fix

[Workflow 43113066559](https://github.com/AmirrezaFarnamTaheri/hiddify-app/actions/runs/43113066559)
passed the Android network and Flutter/analysis gates but the Android APK task
failed in JetifierTransform of an official Flutter arm64 debug runtime jar:
`Java heap space`. No app compilation error or credentials failure was reported.
The Android Gradle daemon heap is increased from 4048 MB to 5120 MB, with a
bounded 768 MB metaspace and concurrent Gradle workers disabled. The full
APK, test and emulator checks remain required and must be re-run. This fix
does not skip transforms, exclude architectures or suppress the build error.
