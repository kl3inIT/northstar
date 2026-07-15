import 'package:flutter_timezone/flutter_timezone.dart';

abstract interface class DeviceTimezoneProvider {
  Future<String> currentIdentifier();
}

class PlatformDeviceTimezoneProvider implements DeviceTimezoneProvider {
  const PlatformDeviceTimezoneProvider();

  @override
  Future<String> currentIdentifier() async {
    try {
      return (await FlutterTimezone.getLocalTimezone()).identifier;
    } on Object {
      return _fixedOffsetIdentifier(DateTime.now().timeZoneOffset);
    }
  }

  String _fixedOffsetIdentifier(Duration offset) {
    if (offset == Duration.zero) {
      return 'UTC';
    }
    final totalMinutes = offset.inMinutes;
    if (totalMinutes < -14 * Duration.minutesPerHour ||
        totalMinutes > 14 * Duration.minutesPerHour) {
      return 'UTC';
    }
    // A ±HH:mm offset id (accepted by the backend's ZoneId parser) preserves
    // half-hour and 45-minute zones (+05:30, +05:45, +03:30) that whole-hour
    // Etc/GMT ids cannot express.
    final sign = totalMinutes > 0 ? '+' : '-';
    final absMinutes = totalMinutes.abs();
    final hours = (absMinutes ~/ Duration.minutesPerHour).toString().padLeft(
      2,
      '0',
    );
    final minutes = (absMinutes % Duration.minutesPerHour).toString().padLeft(
      2,
      '0',
    );
    return '$sign$hours:$minutes';
  }
}
