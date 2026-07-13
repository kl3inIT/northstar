import 'package:flutter/cupertino.dart';

class AccountView extends StatelessWidget {
  const AccountView({
    super.key,
    required this.username,
    required this.onSignOut,
  });

  final String? username;
  final Future<void> Function() onSignOut;

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('account-page'),
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Account'),
        previousPageTitle: 'More',
      ),
      child: SafeArea(
        child: ListView(
          children: [
            CupertinoListSection.insetGrouped(
              header: const Text('SIGNED IN AS'),
              children: [
                CupertinoListTile(
                  leading: const Icon(CupertinoIcons.person_crop_circle),
                  title: Text(username ?? 'Northstar user'),
                  subtitle: const Text(
                    'Session credentials are stored securely.',
                  ),
                ),
              ],
            ),
            CupertinoListSection.insetGrouped(
              children: [
                CupertinoListTile(
                  key: const Key('account-sign-out'),
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
