# Mobile CI

## Problem

The Flutter client is tested locally on Windows, but the local Android toolchain
has shown native-asset/virtual-memory instability and Windows cannot compile the
iOS target. Without a repository CI gate, platform configuration, secure-storage
plugins, entitlements, and generated native projects can regress unnoticed.

## Decision

Add a dedicated `Mobile CI` GitHub Actions workflow. It is separate from the
server/web image gate because mobile artifacts are not deployed by the current
container pipeline and macOS runner time should only be spent when mobile or its
authentication contract changes.

The workflow installs the pinned Flutter SDK directly from the official Flutter
Git repository and caches it per operating system. It does not rely on a
third-party Flutter setup action.

Every reusable action is pinned to the newest full release tag verified live
against its official Git repository. The same audit updates stale floating
major references in the existing CI and image-build workflows.

The Linux job runs:

- dependency resolution;
- format verification;
- static analysis;
- widget tests;
- Web release compilation;
- Android debug APK compilation and artifact upload.

The macOS job runs an iOS release build with `--no-codesign` and uploads the
unsigned app bundle. Signing, App Store provisioning, and physical-device
accessibility remain release gates rather than CI compile gates.

## Trigger Policy

Run on pull requests and pushes to `main` when the mobile tree, mobile auth
backend contract/migration, or workflow itself changes. Also support manual
dispatch. Cancel superseded runs on the same ref.

## Security And Operations

- workflow permissions are read-only;
- no provider key, JWT secret, signing certificate, or provisioning profile is
  required;
- artifacts are short-lived review outputs, not production releases;
- all build commands are standard Flutter CLI commands used by developers.
