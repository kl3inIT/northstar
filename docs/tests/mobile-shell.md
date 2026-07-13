# Mobile Shell Test Matrix

| Behavior | Coverage | Status |
| --- | --- | --- |
| Compact window renders `Today \| Study \| Assistant \| Finance \| More` with Assistant selected by default | Widget test at 390x844 | Covered |
| Today, Study, Assistant, Finance, and More are selectable from compact navigation | Widget interaction and navigation tests | Covered |
| Expanded window renders the same five destinations in a Cupertino sidebar | Widget test at 1024x768 plus real Chromium screenshot | Covered |
| Medium width, large text, and sidebar semantics remain usable | Widget resize/text-scale/semantics tests | Covered |
| Selected destination survives compact-to-expanded resize | Widget resize test | Covered |
| Stateful shell branches retain independent navigation state | Router and widget navigation tests | Covered |
| More routes to Calendar, Habits, Account, and Settings without placeholder destinations | Router and widget interaction tests | Covered |
| Assistant can open a focused note detail without a Notes tab | Router and widget tests | Covered |
| Capture opens from Assistant as a focused route without the tab bar | Widget interaction test | Covered |
| Compact and expanded visual composition | Real Chromium screenshots at 390x844 and 1024x768 | Covered |
| Login and all primary/secondary routes work against a real local API | Chromium walkthrough of Assistant, Today, Study, Finance, More, Calendar, Habits, and Capture | Covered |
| Today, Study, Finance, Calendar, and Habits browser requests succeed through production-style CORS | Real Chromium network walkthrough; all observed API responses `200` | Covered |
| Browser console remains free of application and page errors | Real Chromium compact/expanded walkthrough | Covered |
| Light/dark and isolated component discovery | Widget Preview annotations | Covered |
| Signed-out startup redirects to a Cupertino login screen | Widget test with fake repository | Covered |
| Successful login opens the protected Assistant route | Widget interaction test | Covered |
| Sign out clears state and redirects to login | Widget interaction test | Covered |
| Flutter static analysis, 85-test suite, and Web release compilation | `flutter analyze`, `flutter test`, `flutter build web --release` | Covered |
| Android debug APK compilation | Local Windows Gradle build with Kotlin incremental cache disabled for the cross-drive workspace | Covered |
| Pixel Android rendering inside Orca | `Pixel_10_Pro` attached through Orca, APK installed/launched, and login screen visually inspected | Covered |
| API CORS accepts `X-Timezone` and rejects unlisted headers | Spring MVC integration test plus live preflight | Covered |
| Linux format/analyze/test/Web/Android and macOS IPA packaging | Latest published `main` Mobile CI run `29252163571`; both jobs passed | Covered for published `main`; this local branch requires publication for a branch-specific hosted run |
| Sideloadly IPA structure | macOS job validates `Payload/Runner.app`, bundle ID, ZIP integrity, and SHA-256 without signing secrets | Covered on published `main`; native install remains a gap |
| Assistant waiting state before first SSE frame | ViewModel and compact widget tests | Covered |
| Assistant partial text and tool progress | ViewModel and widget tests with controlled streams | Covered |
| Assistant failure, retry affordance, and stop | ViewModel and widget tests | Covered |
| Assistant history and expanded conversation sidebar | Unit test plus widget test at 1024x768 | Covered |
| Assistant Markdown remains readable on dark Cupertino surfaces | Dark-mode widget contrast assertion plus real Chromium dark-mode render | Covered |
| Assistant SSE parsing and one-time 401 refresh | Service unit tests with streamed HTTP responses | Covered |
| Assistant conversation model selector | Flutter analyzer plus Assistant service/ViewModel/widget suites; real compact UI audit pending native-device validation | Covered |
| Capture text draft, edit, explicit save, and undo for Note, Task, Event, Expense, Study, and Vocabulary | Repository, ViewModel, and compact widget tests | Covered |
| Receipt multipart upload and reviewed batch undo | Service and repository tests | Covered |
| Receipt finance fields remain visible in dark mode | Widget test at 390x844 | Covered |
| Capture against a real local API | Chromium walkthrough at 390x844: draft `200`, note create `201`, undo `204` | Covered |
| Today task/habit optimistic actions and rollback | Repository, ViewModel, and widget tests | Covered |
| Study due queue, reveal, interval previews, and learner rating | API/repository/ViewModel/widget tests | Covered |
| Finance summary and recent activity | API/repository/ViewModel/widget tests | Covered |
| Content-free destination/action telemetry | Unit and widget interaction tests | Covered |
| Native iOS rendering and VoiceOver | Requires signed build on an iPhone | Gap |
| Sideloadly install and live production API login | Requires the generated IPA, Apple ID signing, and an iPhone | Gap |
| Android TalkBack walkthrough | Rendering is verified in Orca, but assistive-technology operation still requires a dedicated manual pass | Gap |
