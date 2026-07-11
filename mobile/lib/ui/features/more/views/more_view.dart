import 'package:flutter/cupertino.dart';

class MoreView extends StatelessWidget {
  const MoreView({this.username, this.onSignOut, super.key});

  final String? username;
  final Future<void> Function()? onSignOut;

  static const _items = [
    (CupertinoIcons.calendar, 'Calendar'),
    (CupertinoIcons.folder, 'Projects'),
    (CupertinoIcons.compass, 'Disciplines'),
    (CupertinoIcons.gear, 'Settings'),
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
                    onTap: () => _showComingNext(context, item.$2),
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

  Future<void> _showComingNext(BuildContext context, String title) {
    return showCupertinoDialog<void>(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: Text(title),
        content: const Text(
          'This area is planned for a later mobile increment.',
        ),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }
}
