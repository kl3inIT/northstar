import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/repositories/assistant_repository.dart';
import 'package:northstar/data/repositories/auth_repository.dart';
import 'package:northstar/data/repositories/capture_repository.dart';
import 'package:northstar/data/repositories/calendar_repository.dart';
import 'package:northstar/data/repositories/finance_repository.dart';
import 'package:northstar/data/repositories/habits_repository.dart';
import 'package:northstar/data/repositories/note_detail_repository.dart';
import 'package:northstar/data/repositories/study_review_repository.dart';
import 'package:northstar/data/repositories/today_repository.dart';
import 'package:northstar/data/services/assistant_api.dart';
import 'package:northstar/data/services/authenticated_api_client.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';
import 'package:northstar/data/services/capture_api.dart';
import 'package:northstar/data/services/calendar_api.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/data/services/finance_api.dart';
import 'package:northstar/data/services/habits_api.dart';
import 'package:northstar/data/services/interaction_telemetry.dart';
import 'package:northstar/data/services/mobile_auth_api.dart';
import 'package:northstar/data/services/note_detail_api.dart';
import 'package:northstar/data/services/receipt_picker.dart';
import 'package:northstar/data/services/refresh_token_store.dart';
import 'package:northstar/data/services/today_api.dart';
import 'package:northstar/data/services/study_review_api.dart';
import 'package:northstar/ui/core/design_system/northstar_theme.dart';
import 'package:northstar/ui/core/navigation/northstar_router.dart';
import 'package:northstar/ui/features/auth/view_models/auth_view_model.dart';
import 'package:northstar/ui/features/assistant/view_models/assistant_view_model.dart';
import 'package:northstar/ui/features/capture/view_models/capture_view_model.dart';
import 'package:northstar/ui/features/calendar/view_models/calendar_view_model.dart';
import 'package:northstar/ui/features/finance/view_models/finance_view_model.dart';
import 'package:northstar/ui/features/habits/view_models/habits_view_model.dart';
import 'package:northstar/ui/features/study/view_models/study_review_view_model.dart';
import 'package:northstar/ui/features/today/view_models/today_view_model.dart';

class NorthstarApp extends StatefulWidget {
  const NorthstarApp({
    super.key,
    this.authRepository,
    this.assistantRepository,
    this.captureRepository,
    this.receiptPicker,
    this.todayRepository,
    this.timezoneProvider,
    this.studyReviewRepository,
    this.financeRepository,
    this.calendarRepository,
    this.habitsRepository,
    this.noteDetailRepository,
    this.telemetry,
  });

  final AuthRepository? authRepository;
  final AssistantRepository? assistantRepository;
  final CaptureRepository? captureRepository;
  final ReceiptSourcePicker? receiptPicker;
  final TodayRepository? todayRepository;
  final DeviceTimezoneProvider? timezoneProvider;
  final StudyReviewRepository? studyReviewRepository;
  final FinanceRepository? financeRepository;
  final CalendarRepository? calendarRepository;
  final HabitsRepository? habitsRepository;
  final NoteDetailRepository? noteDetailRepository;
  final InteractionTelemetry? telemetry;

  @override
  State<NorthstarApp> createState() => _NorthstarAppState();
}

class _NorthstarAppState extends State<NorthstarApp> {
  http.Client? _client;
  late final AuthRepository _authRepository;
  late final AuthViewModel _auth;
  late final AssistantViewModel _assistant;
  late final CaptureViewModel _capture;
  late final TodayViewModel _today;
  late final StudyReviewViewModel _study;
  late final FinanceViewModel _finance;
  late final CalendarViewModel _calendar;
  late final HabitsViewModel _habits;
  late final NoteDetailRepository _notes;
  late final GoRouter _router;

  @override
  void initState() {
    super.initState();
    _client = http.Client();
    _authRepository = widget.authRepository ?? _createAuthRepository();
    _auth = AuthViewModel(_authRepository);
    final assistantRepository =
        widget.assistantRepository ?? _createAssistantRepository();
    _assistant = AssistantViewModel(assistantRepository);
    _capture = CaptureViewModel(
      repository: widget.captureRepository ?? _createCaptureRepository(),
      receiptPicker: widget.receiptPicker ?? ReceiptPicker(),
    );
    final timezoneProvider =
        widget.timezoneProvider ?? const PlatformDeviceTimezoneProvider();
    final telemetry = widget.telemetry ?? const DebugInteractionTelemetry();
    _today = TodayViewModel(
      repository: widget.todayRepository ?? _createTodayRepository(),
      timezoneProvider: timezoneProvider,
      telemetry: telemetry,
    );
    _study = StudyReviewViewModel(
      repository: widget.studyReviewRepository ?? _createStudyRepository(),
      timezoneProvider: timezoneProvider,
      telemetry: telemetry,
    );
    _finance = FinanceViewModel(
      repository: widget.financeRepository ?? _createFinanceRepository(),
      timezoneProvider: timezoneProvider,
    );
    _calendar = CalendarViewModel(
      repository: widget.calendarRepository ?? _createCalendarRepository(),
      timezoneProvider: timezoneProvider,
    );
    _habits = HabitsViewModel(
      repository: widget.habitsRepository ?? _createHabitsRepository(),
      timezoneProvider: timezoneProvider,
      telemetry: telemetry,
    );
    _notes = widget.noteDetailRepository ?? _createNoteDetailRepository();
    _router = createNorthstarRouter(
      _auth,
      _assistant,
      _capture,
      _today,
      _study,
      _finance,
      _calendar,
      _habits,
      _notes,
      telemetry,
    );
    unawaited(_auth.restore());
  }

  static const _configuredBaseUrl = String.fromEnvironment(
    'NORTHSTAR_API_BASE_URL',
    defaultValue: 'http://localhost:8888',
  );

  AuthRepository _createAuthRepository() {
    return MobileAuthRepository(
      api: MobileAuthApi(
        client: _client!,
        baseUrl: Uri.parse(_configuredBaseUrl),
      ),
      refreshTokenStore: createRefreshTokenStore(),
    );
  }

  AssistantRepository _createAssistantRepository() {
    return RemoteAssistantRepository(
      AssistantApi(
        client: _client!,
        baseUrl: Uri.parse(_configuredBaseUrl),
        accessToken: () => _authRepository.accessToken,
        refreshAccessToken: _authRepository.refreshAccessToken,
        onUnauthorized: _auth.expireSession,
      ),
    );
  }

  CaptureRepository _createCaptureRepository() {
    return RemoteCaptureRepository(
      CaptureApi(
        authenticatedClient: AuthenticatedApiClient(
          client: _client!,
          accessToken: () => _authRepository.accessToken,
          refreshAccessToken: _authRepository.refreshAccessToken,
          onUnauthorized: _auth.expireSession,
        ),
        baseUrl: Uri.parse(_configuredBaseUrl),
      ),
    );
  }

  TodayRepository _createTodayRepository() {
    return RemoteTodayRepository(
      TodayApi(
        authenticatedClient: AuthenticatedApiClient(
          client: _client!,
          accessToken: () => _authRepository.accessToken,
          refreshAccessToken: _authRepository.refreshAccessToken,
          onUnauthorized: _auth.expireSession,
        ),
        baseUrl: Uri.parse(_configuredBaseUrl),
      ),
    );
  }

  StudyReviewRepository _createStudyRepository() {
    return RemoteStudyReviewRepository(StudyReviewApi(_createJsonClient()));
  }

  FinanceRepository _createFinanceRepository() {
    return RemoteFinanceRepository(FinanceApi(_createJsonClient()));
  }

  CalendarRepository _createCalendarRepository() {
    return RemoteCalendarRepository(CalendarApi(_createJsonClient()));
  }

  HabitsRepository _createHabitsRepository() {
    return RemoteHabitsRepository(HabitsApi(_createJsonClient()));
  }

  NoteDetailRepository _createNoteDetailRepository() {
    return RemoteNoteDetailRepository(NoteDetailApi(_createJsonClient()));
  }

  AuthenticatedJsonClient _createJsonClient() {
    return AuthenticatedJsonClient(
      authenticatedClient: AuthenticatedApiClient(
        client: _client!,
        accessToken: () => _authRepository.accessToken,
        refreshAccessToken: _authRepository.refreshAccessToken,
        onUnauthorized: _auth.expireSession,
      ),
      baseUrl: Uri.parse(_configuredBaseUrl),
    );
  }

  @override
  void dispose() {
    _router.dispose();
    _assistant.dispose();
    _capture.dispose();
    _today.dispose();
    _study.dispose();
    _finance.dispose();
    _calendar.dispose();
    _habits.dispose();
    _auth.dispose();
    _client?.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoApp.router(
      debugShowCheckedModeBanner: false,
      title: 'Northstar',
      theme: NorthstarTheme.data,
      routerConfig: _router,
    );
  }
}
