import 'package:flutter/cupertino.dart';
import 'package:northstar/ui/core/design_system/northstar_empty_state.dart';

class FeaturePlaceholderView extends StatelessWidget {
  const FeaturePlaceholderView({
    super.key,
    required this.pageKey,
    required this.title,
    required this.icon,
    required this.message,
  });

  final Key pageKey;
  final String title;
  final IconData icon;
  final String message;

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: pageKey,
      navigationBar: CupertinoNavigationBar(middle: Text(title)),
      child: SafeArea(
        child: NorthstarEmptyState(
          icon: icon,
          title: '$title is next',
          message: message,
        ),
      ),
    );
  }
}
