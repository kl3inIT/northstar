import 'package:flutter/cupertino.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';

abstract final class NorthstarTheme {
  static const data = CupertinoThemeData(
    primaryColor: NorthstarColors.accent,
    primaryContrastingColor: CupertinoColors.white,
    barBackgroundColor: NorthstarColors.elevatedSurface,
    scaffoldBackgroundColor: NorthstarColors.background,
    applyThemeToAll: true,
  );
}
