# Contributing

Thanks for looking at OpenConvert. The product rule is simple: **files never leave the device**.

## Before you start

- Read [`README.md`](README.md) / [`README_EN.md`](README_EN.md)
- Known limits: [`docs/known-issues.md`](docs/known-issues.md)
- Hardening / release plan: `OpenConvert 后续开发与发布硬化计划书.md`

## Setup

- JDK 17, Android SDK 36
- `./gradlew.bat testBasicDebugUnitTest testOfficeDebugUnitTest`
- On a physical arm64 device, use `scripts/run-office-instrumented.sh`. Do **not** run `connectedOfficeDebugAndroidTest` (split-APK Error -99).

## How we ship

- Direct to `main` on `donalaaaaaaa/OpenConvert`
- Conventional commits: `feat:`, `fix:`, `ci:`, `docs:`, `build:`
- Never commit `app/*.jks`, `signing.properties`, or APKs

## Code notes

- Default locale is Chinese (`res/values`). English lives in `res/values-en`. Keep the two string files 1:1 on keys.
- Domain copy (errors, capability labels, notifications) goes through `AppCopy.getOr` so JVM tests can keep Chinese fallbacks.
- `ConversionPayloadCodec` must encode every field the Worker reads.
- New `scripts/*.sh` must be LF in git.
- TalkBack live region speaks progress; on-screen text stays `${progress}%`.

## Pull requests

1. One change per PR when possible.
2. Include JVM tests for codec / planner / copy changes.
3. Say how you verified (unit tests, PHY110, or why device tests were skipped).
4. Update the plan book or `docs/releases/` if the change is user-visible.
