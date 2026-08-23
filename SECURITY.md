# Security Policy

OpenConvert is an offline Android converter. It does not declare `INTERNET`, does not upload files, and does not run a backend.

## Supported versions

| Version | Supported |
|---|---|
| 1.1.x | Yes |
| 1.0.x | Security fixes only while 1.1.0 is rolling out |
| < 1.0 | No |

v1.1.0 uses a new release signing certificate. Installed 1.0.0 builds cannot overlay-update; uninstall first. Details: [`docs/signing-audit.md`](docs/signing-audit.md).

## What we consider in-scope

- Local file handling that could overwrite or leak data outside the chosen SAF tree
- Archive extraction path traversal, zip bombs, or unexpected unpack size
- PDF password / encryption handling that drops protection without the user asking
- Release artifacts whose SHA-256, versionName, or signing cert do not match `BUILD_INFO.txt`

## What is out of scope

- Cloud or account attacks (there is no server)
- Device compromise that already has the user's SAF grant
- Third-party decoder bugs in bundled native engines, unless we ship a known-bad version

## Reporting

Open a [GitHub security advisory](https://github.com/donalaaaaaaa/OpenConvert/security/advisories/new) or a private issue. Please include:

- App edition (Basic / Office) and versionName
- Device / Android version
- Steps, and whether the file stays on-device

Do not attach confidential documents to a public issue.

## Release integrity

GitHub Releases ship `RELEASE_NOTES.md`, `SHA256SUMS.txt`, and `BUILD_INFO.txt` produced by `scripts/package-release.sh`. Verify the APK SHA-256 and the v2 certificate fingerprint before installing sideloaded builds.
