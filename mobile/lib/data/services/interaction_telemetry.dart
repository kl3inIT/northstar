import 'package:flutter/foundation.dart';

abstract interface class InteractionTelemetry {
  void record(String action);
}

class NoopInteractionTelemetry implements InteractionTelemetry {
  const NoopInteractionTelemetry();

  @override
  void record(String action) {}
}

class DebugInteractionTelemetry implements InteractionTelemetry {
  const DebugInteractionTelemetry();

  @override
  void record(String action) {
    if (kDebugMode) {
      debugPrint('northstar_interaction surface=mobile action=$action');
    }
  }
}
