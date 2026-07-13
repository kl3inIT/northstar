# Northstar Mobile

Adaptive Flutter client for Northstar, targeting Android, iOS, and Web.

## Local development

```powershell
flutter pub get
flutter analyze
flutter test
flutter run -d chrome
```

For an Android emulator while the API runs on the Windows host:

```powershell
flutter run -d emulator-5554 --dart-define=NORTHSTAR_API_BASE_URL=http://10.0.2.2:8888
```

Open the Flutter project from this `mobile/` directory in Android Studio so the
IDE indexes the Flutter root instead of only the nested Android Gradle project.

The app deliberately uses Cupertino presentation on Android and Web as well as
iOS so its iPhone-first direction remains reviewable from Windows. Layout
decisions are still based on available constraints, text scale, and input
capabilities rather than device labels.
