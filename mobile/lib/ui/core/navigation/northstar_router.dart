import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';
import 'package:northstar/data/repositories/note_detail_repository.dart';
import 'package:northstar/data/services/interaction_telemetry.dart';
import 'package:northstar/ui/core/navigation/northstar_shell.dart';
import 'package:northstar/ui/features/assistant/views/assistant_landing_view.dart';
import 'package:northstar/ui/features/assistant/view_models/assistant_view_model.dart';
import 'package:northstar/ui/features/auth/view_models/auth_view_model.dart';
import 'package:northstar/ui/features/auth/views/login_view.dart';
import 'package:northstar/ui/features/capture/view_models/capture_view_model.dart';
import 'package:northstar/ui/features/capture/views/capture_view.dart';
import 'package:northstar/ui/features/calendar/view_models/calendar_view_model.dart';
import 'package:northstar/ui/features/calendar/views/calendar_view.dart';
import 'package:northstar/ui/features/finance/view_models/finance_view_model.dart';
import 'package:northstar/ui/features/finance/views/finance_view.dart';
import 'package:northstar/ui/features/habits/view_models/habits_view_model.dart';
import 'package:northstar/ui/features/habits/views/habits_view.dart';
import 'package:northstar/ui/features/today/view_models/today_view_model.dart';
import 'package:northstar/ui/features/today/views/today_view.dart';
import 'package:northstar/ui/features/more/views/more_view.dart';
import 'package:northstar/ui/features/more/views/account_view.dart';
import 'package:northstar/ui/features/more/views/settings_view.dart';
import 'package:northstar/ui/features/notes/views/note_detail_view.dart';
import 'package:northstar/ui/features/study/view_models/study_review_view_model.dart';
import 'package:northstar/ui/features/study/views/study_review_view.dart';

abstract final class NorthstarRoutes {
  static const startup = '/startup';
  static const login = '/login';
  static const capture = '/capture';
  static const assistant = '/assistant';
  static const today = '/today';
  static const tasks = '/tasks';
  static const study = '/study';
  static const notes = '/notes';
  static const finance = '/finance';
  static const more = '/more';
  static const calendar = '/more/calendar';
  static const habits = '/more/habits';
  static const account = '/more/account';
  static const settings = '/more/settings';

  static const protected = {
    capture,
    assistant,
    today,
    tasks,
    study,
    notes,
    finance,
    more,
    calendar,
    habits,
    account,
    settings,
  };
}

GoRouter createNorthstarRouter(
  AuthViewModel auth,
  AssistantViewModel assistant,
  CaptureViewModel capture,
  TodayViewModel today,
  StudyReviewViewModel study,
  FinanceViewModel finance,
  CalendarViewModel calendar,
  HabitsViewModel habits,
  NoteDetailRepository notes,
  InteractionTelemetry telemetry,
) {
  final rootNavigatorKey = GlobalKey<NavigatorState>();
  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: NorthstarRoutes.startup,
    refreshListenable: auth,
    redirect: (context, state) {
      final path = state.uri.path;
      final from = _safeDestination(state.uri.queryParameters['from']);

      if (auth.status == AuthStatus.checking) {
        if (path == NorthstarRoutes.startup) {
          return null;
        }
        return Uri(
          path: NorthstarRoutes.startup,
          queryParameters: {'from': _safeDestination(path)},
        ).toString();
      }

      if (!auth.isSignedIn) {
        if (path == NorthstarRoutes.login) {
          return null;
        }
        final destination = from ?? _safeDestination(path);
        return Uri(
          path: NorthstarRoutes.login,
          queryParameters: destination == null ? null : {'from': destination},
        ).toString();
      }

      if (path == NorthstarRoutes.login || path == NorthstarRoutes.startup) {
        return from ?? NorthstarRoutes.assistant;
      }
      return null;
    },
    routes: [
      GoRoute(path: '/', redirect: (_, _) => NorthstarRoutes.assistant),
      GoRoute(
        path: NorthstarRoutes.tasks,
        redirect: (_, _) => NorthstarRoutes.today,
      ),
      GoRoute(
        path: NorthstarRoutes.notes,
        redirect: (_, _) => NorthstarRoutes.study,
      ),
      GoRoute(
        path: NorthstarRoutes.startup,
        builder: (context, state) => const _StartupView(),
      ),
      GoRoute(
        path: NorthstarRoutes.login,
        builder: (context, state) => LoginView(auth: auth),
      ),
      GoRoute(
        path: NorthstarRoutes.capture,
        parentNavigatorKey: rootNavigatorKey,
        pageBuilder: (context, state) => CupertinoPage<void>(
          key: state.pageKey,
          child: CaptureView(
            viewModel: capture,
            promptForReceiptSource:
                state.uri.queryParameters['intent'] == 'receipt',
          ),
        ),
      ),
      GoRoute(
        path: '/notes/:slug',
        parentNavigatorKey: rootNavigatorKey,
        pageBuilder: (context, state) => CupertinoPage<void>(
          key: state.pageKey,
          child: NoteDetailView(
            repository: notes,
            slug: state.pathParameters['slug']!,
          ),
        ),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return NorthstarShell(
            navigationShell: navigationShell,
            telemetry: telemetry,
          );
        },
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: NorthstarRoutes.today,
                builder: (context, state) => TodayView(viewModel: today),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: NorthstarRoutes.study,
                builder: (context, state) => StudyReviewView(viewModel: study),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: NorthstarRoutes.assistant,
                builder: (context, state) => AssistantLandingView(
                  viewModel: assistant,
                  onOpenCapture: () => context.push(NorthstarRoutes.capture),
                  onOpenReceiptCapture: () => context.push(
                    Uri(
                      path: NorthstarRoutes.capture,
                      queryParameters: const {'intent': 'receipt'},
                    ).toString(),
                  ),
                  onOpenDocument: (uri) {
                    final location = _noteDetailLocation(uri);
                    if (location != null) context.push(location);
                  },
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: NorthstarRoutes.finance,
                builder: (context, state) => FinanceView(viewModel: finance),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: NorthstarRoutes.more,
                builder: (context, state) => MoreView(
                  username: auth.session?.username,
                  onSignOut: auth.logout,
                  onOpenCalendar: () => context.push(NorthstarRoutes.calendar),
                  onOpenHabits: () => context.push(NorthstarRoutes.habits),
                  onOpenAccount: () => context.push(NorthstarRoutes.account),
                  onOpenSettings: () => context.push(NorthstarRoutes.settings),
                ),
                routes: [
                  GoRoute(
                    path: 'calendar',
                    builder: (context, state) =>
                        CalendarView(viewModel: calendar),
                  ),
                  GoRoute(
                    path: 'habits',
                    builder: (context, state) => HabitsView(viewModel: habits),
                  ),
                  GoRoute(
                    path: 'account',
                    builder: (context, state) => AccountView(
                      username: auth.session?.username,
                      onSignOut: auth.logout,
                    ),
                  ),
                  GoRoute(
                    path: 'settings',
                    builder: (context, state) => const SettingsView(),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    ],
  );
}

String? _safeDestination(String? path) {
  if (NorthstarRoutes.protected.contains(path)) return path;
  if (path != null && path.startsWith('/notes/')) return path;
  return null;
}

String? _noteDetailLocation(String value) {
  final uri = Uri.tryParse(value);
  if (uri == null) return null;
  final segments = uri.pathSegments;
  final notesIndex = segments.indexOf('notes');
  if (notesIndex < 0 || notesIndex + 1 >= segments.length) return null;
  final slug = segments[notesIndex + 1].trim();
  if (slug.isEmpty) return null;
  return '/notes/${Uri.encodeComponent(slug)}';
}

class _StartupView extends StatelessWidget {
  const _StartupView();

  @override
  Widget build(BuildContext context) {
    return const CupertinoPageScaffold(
      key: Key('startup-page'),
      child: Center(child: CupertinoActivityIndicator(radius: 14)),
    );
  }
}
