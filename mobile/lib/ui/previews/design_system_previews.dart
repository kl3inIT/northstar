import 'package:flutter/cupertino.dart';
import 'package:flutter/widget_previews.dart';
import 'package:northstar/ui/core/design_system/northstar_empty_state.dart';
import 'package:northstar/ui/core/design_system/northstar_theme.dart';
import 'package:northstar/ui/core/northstar_app.dart';

@Preview(
  name: 'Compact iPhone shell',
  group: 'Northstar shell',
  size: Size(390, 844),
  brightness: Brightness.light,
)
Widget compactShellPreview() => const NorthstarApp();

@Preview(
  name: 'Compact iPhone shell — dark',
  group: 'Northstar shell',
  size: Size(390, 844),
  brightness: Brightness.dark,
)
Widget compactDarkShellPreview() => const NorthstarApp();

@Preview(
  name: 'Expanded shell',
  group: 'Northstar shell',
  size: Size(1024, 768),
  brightness: Brightness.light,
)
Widget expandedShellPreview() => const NorthstarApp();

@Preview(name: 'Empty state', group: 'Design system', size: Size(390, 420))
Widget emptyStatePreview() {
  return const CupertinoApp(
    theme: NorthstarTheme.data,
    home: CupertinoPageScaffold(
      child: SafeArea(
        child: NorthstarEmptyState(
          icon: CupertinoIcons.doc_text,
          title: 'Nothing here yet',
          message: 'New content will appear here when it is ready.',
          actionLabel: 'Create item',
        ),
      ),
    ),
  );
}
