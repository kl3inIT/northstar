import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/core/navigation/northstar_destination.dart';
import 'package:northstar/data/services/interaction_telemetry.dart';

class NorthstarShell extends StatelessWidget {
  const NorthstarShell({
    required this.navigationShell,
    required this.telemetry,
    super.key,
  });

  final StatefulNavigationShell navigationShell;
  final InteractionTelemetry telemetry;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < NorthstarLayout.compactBreakpoint) {
          return _CompactShell(
            navigationShell: navigationShell,
            telemetry: telemetry,
          );
        }

        return _ExpandedShell(
          navigationShell: navigationShell,
          telemetry: telemetry,
        );
      },
    );
  }
}

class _CompactShell extends StatelessWidget {
  const _CompactShell({required this.navigationShell, required this.telemetry});

  final StatefulNavigationShell navigationShell;
  final InteractionTelemetry telemetry;

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      child: Column(
        children: [
          Expanded(child: navigationShell),
          CupertinoTabBar(
            currentIndex: navigationShell.currentIndex,
            onTap: (index) => _goToBranch(navigationShell, index, telemetry),
            items: [
              for (final destination in NorthstarDestination.values)
                BottomNavigationBarItem(
                  icon: Icon(destination.icon),
                  activeIcon: Icon(destination.selectedIcon),
                  label: destination.label,
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ExpandedShell extends StatelessWidget {
  const _ExpandedShell({
    required this.navigationShell,
    required this.telemetry,
  });

  final StatefulNavigationShell navigationShell;
  final InteractionTelemetry telemetry;

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      child: Row(
        children: [
          _CupertinoSidebar(
            navigationShell: navigationShell,
            telemetry: telemetry,
          ),
          Expanded(child: navigationShell),
        ],
      ),
    );
  }
}

class _CupertinoSidebar extends StatelessWidget {
  const _CupertinoSidebar({
    required this.navigationShell,
    required this.telemetry,
  });

  final StatefulNavigationShell navigationShell;
  final InteractionTelemetry telemetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('northstar-sidebar'),
      width: NorthstarLayout.sidebarWidth,
      decoration: BoxDecoration(
        color: NorthstarColors.elevatedSurface.resolveFrom(context),
        border: Border(
          right: BorderSide(
            color: NorthstarColors.separator.resolveFrom(context),
            width: 0.5,
          ),
        ),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(NorthstarSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  NorthstarSpacing.sm,
                  NorthstarSpacing.sm,
                  NorthstarSpacing.sm,
                  NorthstarSpacing.lg,
                ),
                child: Row(
                  children: [
                    Icon(
                      CupertinoIcons.sparkles,
                      color: NorthstarColors.accent.resolveFrom(context),
                    ),
                    const SizedBox(width: NorthstarSpacing.xs),
                    Expanded(
                      child: Text(
                        'Northstar',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: NorthstarTextStyles.sectionTitle(context),
                      ),
                    ),
                  ],
                ),
              ),
              for (final (index, destination)
                  in NorthstarDestination.values.indexed)
                Padding(
                  padding: const EdgeInsets.only(bottom: NorthstarSpacing.xxs),
                  child: Semantics(
                    key: Key('destination-${destination.name}-semantics'),
                    label: destination.label,
                    button: true,
                    selected: navigationShell.currentIndex == index,
                    excludeSemantics: true,
                    child: SizedBox(
                      height: 48,
                      child: CupertinoButton(
                        key: Key('destination-${destination.name}'),
                        padding: const EdgeInsets.symmetric(
                          horizontal: NorthstarSpacing.sm,
                        ),
                        color: navigationShell.currentIndex == index
                            ? NorthstarColors.accent
                                  .resolveFrom(context)
                                  .withValues(alpha: 0.14)
                            : null,
                        borderRadius: BorderRadius.circular(NorthstarRadii.sm),
                        onPressed: () =>
                            _goToBranch(navigationShell, index, telemetry),
                        child: Row(
                          children: [
                            Icon(
                              navigationShell.currentIndex == index
                                  ? destination.selectedIcon
                                  : destination.icon,
                              color: navigationShell.currentIndex == index
                                  ? NorthstarColors.accent.resolveFrom(context)
                                  : NorthstarColors.primaryText.resolveFrom(
                                      context,
                                    ),
                            ),
                            const SizedBox(width: NorthstarSpacing.sm),
                            Expanded(
                              child: Text(
                                destination.label,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: NorthstarTextStyles.body(context)
                                    .copyWith(
                                      color: NorthstarColors.primaryText
                                          .resolveFrom(context),
                                      fontWeight:
                                          navigationShell.currentIndex == index
                                          ? FontWeight.w600
                                          : FontWeight.w400,
                                    ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

void _goToBranch(
  StatefulNavigationShell navigationShell,
  int index,
  InteractionTelemetry telemetry,
) {
  telemetry.record('destination.${NorthstarDestination.values[index].name}');
  navigationShell.goBranch(
    index,
    initialLocation: index == navigationShell.currentIndex,
  );
}
