import 'package:flutter/cupertino.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';

class NorthstarSurface extends StatelessWidget {
  const NorthstarSurface({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(NorthstarSpacing.md),
    this.radius = NorthstarRadii.md,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: NorthstarColors.surface.resolveFrom(context),
        border: Border.all(
          color: NorthstarColors.separator.resolveFrom(context),
          width: 0.5,
        ),
        borderRadius: BorderRadius.circular(radius),
      ),
      child: Padding(padding: padding, child: child),
    );
  }
}
