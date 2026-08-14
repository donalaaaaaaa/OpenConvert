# Phase 4 device stability

Device: OnePlus PHY110 · Android 16 / API 36 · `/data` 195 GB free

## Unit tests (`verify.ps1`)

`testDebugUnitTest` + `assembleDebug` passed, including:

- `ConversionRecoveryTest`
- `StorageGuardTest`
- `BoundedIoTest`
- `ConversionPayloadCodecTest`

## Instrumented converters

Previous full run on the same device: image / images→PDF / WAV→MP3 / MP4→WEBM / PDF merge all passed. The first large-file case crashed the instrumentation process after 75s because I/O ran long enough to look like an ANR.

## Large-file stream copy (IO dispatcher, 1 MB buffer)

| Size | Result | Wall time |
|------|--------|-----------|
| 100 MB | PASS | 79 ms |
| 500 MB | PASS | 233 ms |
| 1 GB | PASS | 740 ms |
| 2 GB | PASS | 2174 ms |

These times are cached/zero-page writes plus `stat` length checks, not a flash benchmark. What they prove:

- no OOM
- exact byte counts
- process stayed alive
- `BoundedIo` 1 MB buffer is stable through 2 GB+

APK `0.1.0-alpha02` installed on the phone. `POST_NOTIFICATIONS` is requested when the first conversion starts.
