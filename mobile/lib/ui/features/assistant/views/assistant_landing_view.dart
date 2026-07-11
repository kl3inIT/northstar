import 'package:flutter/cupertino.dart';
import 'package:northstar/ui/core/design_system/northstar_surface.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';

class AssistantLandingView extends StatelessWidget {
  const AssistantLandingView({super.key});

  static const _actions = [
    (
      CupertinoIcons.plus_circle,
      'Capture a thought',
      'Turn an idea into a note.',
    ),
    (
      CupertinoIcons.check_mark_circled,
      'Plan my day',
      'Review tasks and open time.',
    ),
    (
      CupertinoIcons.chart_bar,
      'Review finances',
      'See balances and recent activity.',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('assistant-page'),
      navigationBar: const CupertinoNavigationBar(middle: Text('Assistant')),
      child: SafeArea(
        bottom: false,
        child: LayoutBuilder(
          builder: (context, constraints) {
            return SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(
                NorthstarSpacing.md,
                NorthstarSpacing.xl,
                NorthstarSpacing.md,
                NorthstarSpacing.xxl,
              ),
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: NorthstarLayout.readableContentWidth,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'What should we focus on?',
                        style: NorthstarTextStyles.hero(context),
                      ),
                      const SizedBox(height: NorthstarSpacing.sm),
                      Text(
                        'Northstar brings your notes, plans, and personal data '
                        'into one calm place.',
                        style: NorthstarTextStyles.body(context).copyWith(
                          color: NorthstarColors.secondaryText.resolveFrom(
                            context,
                          ),
                        ),
                      ),
                      const SizedBox(height: NorthstarSpacing.xl),
                      Text(
                        'Start here',
                        style: NorthstarTextStyles.sectionTitle(context),
                      ),
                      const SizedBox(height: NorthstarSpacing.sm),
                      _QuickActions(useColumns: constraints.maxWidth >= 700),
                      const SizedBox(height: NorthstarSpacing.xl),
                      const _AssistantComposerPreview(),
                    ],
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _QuickActions extends StatelessWidget {
  const _QuickActions({required this.useColumns});

  final bool useColumns;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final itemWidth = useColumns
            ? (constraints.maxWidth - NorthstarSpacing.md * 2) / 3
            : constraints.maxWidth;

        return Wrap(
          spacing: NorthstarSpacing.md,
          runSpacing: NorthstarSpacing.sm,
          children: [
            for (final action in AssistantLandingView._actions)
              SizedBox(
                width: itemWidth,
                child: NorthstarSurface(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        action.$1,
                        color: NorthstarColors.accent.resolveFrom(context),
                      ),
                      const SizedBox(height: NorthstarSpacing.sm),
                      Text(
                        action.$2,
                        style: NorthstarTextStyles.body(
                          context,
                        ).copyWith(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: NorthstarSpacing.xxs),
                      Text(
                        action.$3,
                        style: NorthstarTextStyles.caption(context),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _AssistantComposerPreview extends StatelessWidget {
  const _AssistantComposerPreview();

  @override
  Widget build(BuildContext context) {
    return NorthstarSurface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CupertinoTextField(
            enabled: false,
            placeholder: 'Ask Northstar…',
            minLines: 2,
            maxLines: 4,
            padding: const EdgeInsets.all(NorthstarSpacing.md),
            suffix: Padding(
              padding: const EdgeInsets.only(right: NorthstarSpacing.xs),
              child: CupertinoButton(
                padding: EdgeInsets.zero,
                onPressed: null,
                child: const Icon(CupertinoIcons.arrow_up_circle_fill),
              ),
            ),
          ),
          const SizedBox(height: NorthstarSpacing.xs),
          Text(
            'Assistant streaming will connect in the next product slice.',
            style: NorthstarTextStyles.caption(context),
          ),
        ],
      ),
    );
  }
}
