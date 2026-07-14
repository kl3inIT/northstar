import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('iOS asset catalog build settings remain valid after icon generation', () {
    final projectFile = File('ios/Runner.xcodeproj/project.pbxproj');
    final project = projectFile.readAsStringSync();

    final symbolExtensionValues = RegExp(
      r'ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS = ([^;]+);',
    ).allMatches(project).map((match) => match.group(1)).toSet();

    expect(symbolExtensionValues, {'YES'});
    expect(project, contains('ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;'));
  });
}
