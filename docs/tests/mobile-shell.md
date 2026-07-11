# Mobile Shell Test Matrix

| Behavior | Coverage | Status |
| --- | --- | --- |
| Compact window renders Cupertino tabs and Assistant by default | Widget test at 390x844 | Covered |
| Notes can be selected from the compact tab bar | Widget interaction test | Covered |
| Expanded window renders the Cupertino sidebar | Widget test at 1024x768 | Covered |
| Finance can be selected from the expanded sidebar | Widget interaction test | Covered |
| Selected destination survives compact-to-expanded resize | Widget resize test | Covered |
| Unfinished More destinations explain the next dependency | Widget dialog test | Covered |
| Compact and expanded visual composition | Real Chromium screenshots at 390x844 and 1024x768 | Covered |
| Browser console remains free of render errors | Real Chromium walkthrough | Covered |
| Light/dark and isolated component discovery | Widget Preview annotations | Covered |
| Signed-out startup redirects to a Cupertino login screen | Widget test with fake repository | Covered |
| Successful login opens the protected Assistant route | Widget interaction test | Covered |
| Sign out clears state and redirects to login | Widget interaction test | Covered |
| Flutter static analysis and Web release compilation | `flutter analyze`, `flutter build web --release` | Covered |
| Linux format/analyze/test/Web build | Path-scoped `Mobile CI` workflow on GitHub-hosted Ubuntu | Covered; first hosted run passed in Actions run `29144303486` |
| Android debug APK compilation | `Mobile CI` Ubuntu job uploads a seven-day review artifact | Covered; `northstar-android-debug` uploaded successfully in run `29144303486` |
| Sideloadly IPA packaging | macOS job validates `Payload/Runner.app`, bundle ID, ZIP integrity, and SHA-256 then uploads `northstar-ios-sideloadly` without signing secrets | Covered locally by workflow lint; hosted run pending this commit |
| Assistant waiting state before first SSE frame | ViewModel and compact widget tests | Covered |
| Assistant partial text and tool progress | ViewModel and widget tests with controlled streams | Covered |
| Assistant failure, retry affordance, and stop | ViewModel and widget tests | Covered |
| Assistant history and expanded conversation sidebar | Unit test plus widget test at 1024x768 | Covered |
| Assistant SSE parsing and one-time 401 refresh | Service unit tests with streamed HTTP responses | Covered |
| Native iOS rendering and VoiceOver | Requires signed build on an iPhone | Gap |
| Sideloadly install and live production API login | Requires the generated IPA, Apple ID signing, and an iPhone | Gap |
| Android device rendering and TalkBack | Requires attached emulator/device | Gap |
