import 'package:flutter/cupertino.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';

class NorthstarEmptyState extends StatelessWidget {
  const NorthstarEmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 360),
        child: Padding(
          padding: const EdgeInsets.all(NorthstarSpacing.lg),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: NorthstarColors.accent
                      .resolveFrom(context)
                      .withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(NorthstarRadii.lg),
                ),
                alignment: Alignment.center,
                child: Icon(
                  icon,
                  size: 30,
                  color: NorthstarColors.accent.resolveFrom(context),
                ),
              ),
              const SizedBox(height: NorthstarSpacing.md),
              Text(
                title,
                textAlign: TextAlign.center,
                style: NorthstarTextStyles.sectionTitle(context),
              ),
              const SizedBox(height: NorthstarSpacing.xs),
              Text(
                message,
                textAlign: TextAlign.center,
                style: NorthstarTextStyles.body(context).copyWith(
                  color: NorthstarColors.secondaryText.resolveFrom(context),
                ),
              ),
              if (actionLabel != null) ...[
                const SizedBox(height: NorthstarSpacing.lg),
                CupertinoButton.filled(
                  onPressed: onAction,
                  child: Text(actionLabel!),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
