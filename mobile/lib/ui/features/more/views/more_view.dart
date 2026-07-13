import 'package:flutter/cupertino.dart';

class MoreView extends StatelessWidget {
  const MoreView({
    this.username,
    this.onSignOut,
    this.onOpenCalendar,
    this.onOpenHabits,
    this.onOpenAccount,
    this.onOpenSettings,
    super.key,
  });

  final String? username;
  final Future<void> Function()? onSignOut;
  final VoidCallback? onOpenCalendar;
  final VoidCallback? onOpenHabits;
  final VoidCallback? onOpenAccount;
  final VoidCallback? onOpenSettings;

  List<(IconData, String, VoidCallback?)> get _items => [
    (CupertinoIcons.calendar, 'Calendar', onOpenCalendar),
    (CupertinoIcons.circle_grid_hex, 'Habits', onOpenHabits),
    (CupertinoIcons.gear, 'Settings', onOpenSettings),
  ];

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('more-page'),
      navigationBar: const CupertinoNavigationBar(middle: Text('More')),
      child: SafeArea(
        child: ListView(
          children: [
            CupertinoListSection.insetGrouped(
              header: const Text('NORTHSTAR'),
              children: [
                for (final item in _items)
                  CupertinoListTile(
                    leading: Icon(item.$1),
                    title: Text(item.$2),
                    trailing: const CupertinoListTileChevron(),
                    onTap: item.$3,
                  ),
              ],
            ),
            if (onSignOut != null)
              CupertinoListSection.insetGrouped(
                header: const Text('ACCOUNT'),
                children: [
                  if (username != null)
                    CupertinoListTile(
                      leading: const Icon(CupertinoIcons.person_crop_circle),
                      title: Text(username!),
                      trailing: onOpenAccount == null
                          ? null
                          : const CupertinoListTileChevron(),
                      onTap: onOpenAccount,
                    ),
                  CupertinoListTile(
                    key: const Key('sign-out-button'),
                    leading: const Icon(
                      CupertinoIcons.square_arrow_right,
                      color: CupertinoColors.systemRed,
                    ),
                    title: const Text(
                      'Sign Out',
                      style: TextStyle(color: CupertinoColors.systemRed),
                    ),
                    onTap: onSignOut,
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}
