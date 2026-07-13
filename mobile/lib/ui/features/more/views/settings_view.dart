import 'package:flutter/cupertino.dart';

class SettingsView extends StatelessWidget {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('settings-page'),
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Settings'),
        previousPageTitle: 'More',
      ),
      child: SafeArea(
        child: CupertinoListSection.insetGrouped(
          header: const Text('MOBILE'),
          footer: const Text(
            'Dense configuration stays on the web app. Mobile follows the '
            'device appearance and timezone.',
          ),
          children: const [
            CupertinoListTile(
              leading: Icon(CupertinoIcons.circle_lefthalf_fill),
              title: Text('Appearance'),
              additionalInfo: Text('System'),
            ),
            CupertinoListTile(
              leading: Icon(CupertinoIcons.time),
              title: Text('Timezone'),
              additionalInfo: Text('Device'),
            ),
          ],
        ),
      ),
    );
  }
}
