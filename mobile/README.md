# Northstar Mobile

Adaptive Flutter client for Northstar, targeting Android, iOS, and Web.

## Local development

```powershell
flutter pub get
flutter analyze
flutter test
flutter run -d chrome
```

Open the Flutter project from this `mobile/` directory in Android Studio so the
IDE indexes the Flutter root instead of only the nested Android Gradle project.

The app selects Cupertino controls on iOS and Material controls on other
platforms. Layout decisions should still be based on available constraints,
not device labels.
