import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/finance_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/domain/models/finance_models.dart';

enum FinancePhase { idle, loading, ready, error }

class FinanceViewModel extends ChangeNotifier {
  factory FinanceViewModel({
    required FinanceRepository repository,
    required DeviceTimezoneProvider timezoneProvider,
    DateTime Function()? clock,
  }) {
    return FinanceViewModel._(
      repository,
      timezoneProvider,
      clock ?? DateTime.now,
    );
  }

  FinanceViewModel._(this._repository, this._timezoneProvider, this._clock);

  final FinanceRepository _repository;
  final DeviceTimezoneProvider _timezoneProvider;
  final DateTime Function() _clock;

  FinancePhase _phase = FinancePhase.idle;
  FinanceGlance? _glance;
  String? _errorMessage;
  String? _timezone;

  FinancePhase get phase => _phase;
  FinanceGlance? get glance => _glance;
  String? get errorMessage => _errorMessage;

  Future<void> initialize() async {
    if (_phase != FinancePhase.idle) return;
    await load();
  }

  Future<void> load() async {
    _phase = FinancePhase.loading;
    _errorMessage = null;
    notifyListeners();
    try {
      final timezone = _timezone ??= await _timezoneProvider
          .currentIdentifier();
      _glance = await _repository.glance(
        month: _month(_clock()),
        timezone: timezone,
      );
      _phase = FinancePhase.ready;
    } on Object catch (error) {
      _phase = FinancePhase.error;
      _errorMessage = _messageFor(error);
    }
    notifyListeners();
  }

  String _month(DateTime value) {
    return '${value.year}-${value.month.toString().padLeft(2, '0')}';
  }

  String _messageFor(Object error) {
    final text = error.toString().replaceFirst(
      RegExp(r'^\w+Exception:\s*'),
      '',
    );
    return text.isEmpty ? 'Finance is unavailable.' : text;
  }
}
