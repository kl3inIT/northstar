import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';
import 'package:northstar/ui/core/navigation/northstar_shell.dart';
import 'package:northstar/ui/features/assistant/views/assistant_landing_view.dart';
import 'package:northstar/ui/features/assistant/view_models/assistant_view_model.dart';
import 'package:northstar/ui/features/auth/view_models/auth_view_model.dart';
import 'package:northstar/ui/features/auth/views/login_view.dart';
import 'package:northstar/ui/features/capture/view_models/capture_view_model.dart';
import 'package:northstar/ui/features/capture/views/capture_view.dart';
import 'package:northstar/ui/features/more/views/more_view.dart';
import 'package:northstar/ui/features/shell/views/feature_placeholder_view.dart';

abstract final class NorthstarRoutes {
  static const startup = '/startup';
  static const login = '/login';
  static const capture = '/capture';
  static const assistant = '/assistant';
  static const tasks = '/tasks';
  static const notes = '/notes';
  static const finance = '/finance';
  static const more = '/more';

  static const protected = {capture, assistant, tasks, notes, finance, more};
}

GoRouter createNorthstarRouter(
  AuthViewModel auth,
  AssistantViewModel assistant,
  CaptureViewModel capture,
) {
  return GoRouter(
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
        path: NorthstarRoutes.startup,
        builder: (context, state) => const _StartupView(),
      ),
      GoRoute(
        path: NorthstarRoutes.login,
        builder: (context, state) => LoginView(auth: auth),
      ),
      GoRoute(
        path: NorthstarRoutes.capture,
        pageBuilder: (context, state) => CupertinoPage<void>(
          key: state.pageKey,
          child: CaptureView(
            viewModel: capture,
            promptForReceiptSource:
                state.uri.queryParameters['intent'] == 'receipt',
          ),
        ),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return NorthstarShell(navigationShell: navigationShell);
        },
        branches: [
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
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: NorthstarRoutes.tasks,
                builder: (context, state) => const FeaturePlaceholderView(
                  pageKey: Key('tasks-page'),
                  title: 'Tasks',
                  icon: CupertinoIcons.check_mark_circled,
                  message: 'Task lists and daily planning are coming next.',
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: NorthstarRoutes.notes,
                builder: (context, state) => const FeaturePlaceholderView(
                  pageKey: Key('notes-page'),
                  title: 'Notes',
                  icon: CupertinoIcons.doc_text,
                  message: 'Your knowledge base will appear here soon.',
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: NorthstarRoutes.finance,
                builder: (context, state) => const FeaturePlaceholderView(
                  pageKey: Key('finance-page'),
                  title: 'Finance',
                  icon: CupertinoIcons.chart_bar,
                  message: 'Balances, activity, and budgets are coming next.',
                ),
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
                ),
              ),
            ],
          ),
        ],
      ),
    ],
  );
}

String? _safeDestination(String? path) {
  return NorthstarRoutes.protected.contains(path) ? path : null;
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
