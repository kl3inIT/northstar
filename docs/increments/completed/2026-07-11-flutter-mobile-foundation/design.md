# Flutter Mobile Foundation

## Problem

Northstar has a React web client but no mobile client. Development happens on
Windows, while iOS artifacts will be built on macOS GitHub Actions runners.
The initial project must therefore support local Android/Web development and
include the iOS target without requiring a local Mac.

## Scope

- create one Flutter app at repository-root `mobile/`;
- target Android, iOS, and Web;
- use the reverse-domain identifier `io.github.kl3init.northstar`;
- keep the Flutter app isolated from the Gradle backend and React web client;
- establish a small testable policy that chooses Cupertino controls on iOS and
  Material controls elsewhere;
- establish the feature-oriented UI directory without prematurely adding empty
  data or domain layers.

API integration, mobile authentication, production screens, signing, and the
iOS GitHub Actions workflow are outside this foundation increment.

## Architecture

`mobile/lib/main.dart` is the composition root. It constructs an
`AppDesignPolicy` from Flutter's target platform and injects it into
`NorthstarApp`. Widgets depend on the named policy decision rather than
scattered platform checks. Future features live below `ui/features/`; shared
theme and platform policy code lives below `ui/core/`. Data repositories,
services, and domain use cases are added only when the first API-backed feature
needs them.

## Verification

- `dart format --output=none --set-exit-if-changed lib test`;
- `flutter analyze`;
- `flutter test` with both Cupertino and Material policy branches.
