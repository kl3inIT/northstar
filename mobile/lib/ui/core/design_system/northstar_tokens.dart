import 'package:flutter/cupertino.dart';

abstract final class NorthstarColors {
  static const accent = CupertinoColors.systemIndigo;
  static const positive = CupertinoColors.systemGreen;
  static const warning = CupertinoColors.systemOrange;
  static const destructive = CupertinoColors.systemRed;
  static const background = CupertinoColors.systemGroupedBackground;
  static const surface = CupertinoColors.secondarySystemGroupedBackground;
  static const elevatedSurface = CupertinoColors.systemBackground;
  static const separator = CupertinoColors.separator;
  static const primaryText = CupertinoColors.label;
  static const secondaryText = CupertinoColors.secondaryLabel;
}

abstract final class NorthstarSpacing {
  static const xxs = 4.0;
  static const xs = 8.0;
  static const sm = 12.0;
  static const md = 16.0;
  static const lg = 24.0;
  static const xl = 32.0;
  static const xxl = 48.0;
}

abstract final class NorthstarRadii {
  static const sm = 10.0;
  static const md = 14.0;
  static const lg = 20.0;
  static const pill = 999.0;
}

abstract final class NorthstarLayout {
  static const compactBreakpoint = 600.0;
  static const readableContentWidth = 720.0;
  static const sidebarWidth = 248.0;
  static const minimumTouchTarget = 44.0;
}

abstract final class NorthstarTextStyles {
  static TextStyle hero(BuildContext context) {
    return CupertinoTheme.of(context).textTheme.navLargeTitleTextStyle.copyWith(
      fontSize: 34,
      height: 1.08,
      letterSpacing: -0.8,
    );
  }

  static TextStyle sectionTitle(BuildContext context) {
    return CupertinoTheme.of(context).textTheme.navTitleTextStyle.copyWith(
      fontSize: 20,
      fontWeight: FontWeight.w700,
    );
  }

  static TextStyle body(BuildContext context) {
    return CupertinoTheme.of(
      context,
    ).textTheme.textStyle.copyWith(fontSize: 16, height: 1.35);
  }

  static TextStyle caption(BuildContext context) {
    return body(context).copyWith(
      fontSize: 13,
      color: NorthstarColors.secondaryText.resolveFrom(context),
    );
  }
}
