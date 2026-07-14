import 'package:flutter/cupertino.dart';

class NorthstarBrandMark extends StatelessWidget {
  const NorthstarBrandMark({
    super.key,
    this.size = 88,
    this.semanticLabel = 'Northstar logo',
  });

  final double size;
  final String semanticLabel;

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/branding/northstar_mark.png',
      width: size,
      height: size,
      fit: BoxFit.contain,
      semanticLabel: semanticLabel,
    );
  }
}
