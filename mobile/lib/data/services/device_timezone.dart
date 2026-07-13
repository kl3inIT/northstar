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
    if (offset.inMinutes % Duration.minutesPerHour != 0) {
      return 'UTC';
    }
    final hours = offset.inHours;
    if (hours < -14 || hours > 14) {
      return 'UTC';
    }
    // IANA's Etc/GMT signs are intentionally reversed.
    final sign = hours > 0 ? '-' : '+';
    return 'Etc/GMT$sign${hours.abs()}';
  }
}
