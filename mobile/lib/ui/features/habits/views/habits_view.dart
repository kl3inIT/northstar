import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:northstar/domain/models/today_models.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/habits/view_models/habits_view_model.dart';

class HabitsView extends StatefulWidget {
  const HabitsView({super.key, required this.viewModel});

  final HabitsViewModel viewModel;

  @override
  State<HabitsView> createState() => _HabitsViewState();
}

class _HabitsViewState extends State<HabitsView> {
  HabitsViewModel get _viewModel => widget.viewModel;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.initialize());
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('habits-page'),
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Habits'),
        previousPageTitle: 'More',
        trailing: CupertinoButton(
          key: const Key('habits-refresh-button'),
          padding: EdgeInsets.zero,
          onPressed: _viewModel.phase == HabitsPhase.loading
              ? null
              : _viewModel.load,
          child: const Icon(CupertinoIcons.refresh, semanticLabel: 'Refresh'),
        ),
      ),
      child: SafeArea(
        bottom: false,
        child: AnimatedBuilder(
          animation: _viewModel,
          builder: (context, _) => _body(context),
        ),
      ),
    );
  }

  Widget _body(BuildContext context) {
    if (_viewModel.phase == HabitsPhase.loading) {
      return const Center(
        child: CupertinoActivityIndicator(
          key: Key('habits-loading-indicator'),
          radius: 14,
        ),
      );
    }
    if (_viewModel.phase == HabitsPhase.error && _viewModel.habits.isEmpty) {
      return _HabitsMessage(
        key: const Key('habits-load-error'),
        title: 'Couldn’t load Habits',
        message: _viewModel.errorMessage ?? 'Habits are unavailable.',
        onRetry: _viewModel.load,
      );
    }
    if (_viewModel.habits.isEmpty) {
      return _HabitsMessage(
        key: const Key('habits-empty'),
        title: 'No active habits',
        message: 'Create and configure habits on the web app.',
        onRetry: _viewModel.load,
        actionLabel: 'Refresh',
      );
    }
    return ListView(
      key: const Key('habits-list'),
      padding: const EdgeInsets.only(bottom: NorthstarSpacing.xxl),
      children: [
        if (_viewModel.errorMessage case final error?)
          Padding(
            padding: const EdgeInsets.all(NorthstarSpacing.md),
            child: Text(
              error,
              key: const Key('habits-action-error'),
              style: const TextStyle(color: CupertinoColors.systemRed),
            ),
          ),
        Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: NorthstarLayout.readableContentWidth,
            ),
            child: CupertinoListSection.insetGrouped(
              header: const Text('TODAY'),
              footer: const Text(
                'Schedule editing and long-term insights remain in the web app.',
              ),
              children: [
                for (final habit in _viewModel.habits)
                  _HabitRow(
                    habit: habit,
                    pending: _viewModel.isPending(habit.id),
                    onCheckIn: (checkIn) =>
                        _viewModel.setCheckIn(habit.id, checkIn),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _HabitRow extends StatelessWidget {
  const _HabitRow({
    required this.habit,
    required this.pending,
    required this.onCheckIn,
  });

  final TodayHabit habit;
  final bool pending;
  final ValueChanged<TodayHabitCheckIn?> onCheckIn;

  @override
  Widget build(BuildContext context) {
    final actionable =
        habit.state != TodayHabitState.paused &&
        habit.state != TodayHabitState.notScheduled;
    return Padding(
      key: Key('habits-item-${habit.id}'),
      padding: const EdgeInsets.all(NorthstarSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  habit.title,
                  style: NorthstarTextStyles.body(
                    context,
                  ).copyWith(fontWeight: FontWeight.w600),
                ),
              ),
              if (pending)
                const CupertinoActivityIndicator(radius: 9)
              else
                Text(
                  '${habit.completedThisWeek}/${habit.targetThisWeek}',
                  style: NorthstarTextStyles.caption(context),
                ),
            ],
          ),
          if (habit.cue case final cue?) ...[
            const SizedBox(height: NorthstarSpacing.xxs),
            Text(cue, style: NorthstarTextStyles.caption(context)),
          ],
          const SizedBox(height: NorthstarSpacing.sm),
          if (!actionable)
            Text(
              habit.state == TodayHabitState.paused
                  ? 'Paused'
                  : 'Not scheduled today',
              style: NorthstarTextStyles.caption(context),
            )
          else
            Wrap(
              spacing: NorthstarSpacing.xs,
              runSpacing: NorthstarSpacing.xs,
              children: [
                _Action(
                  key: Key('habits-done-${habit.id}'),
                  label: 'Done',
                  selected: habit.state == TodayHabitState.done,
                  onPressed: pending
                      ? null
                      : () => onCheckIn(TodayHabitCheckIn.done),
                ),
                _Action(
                  key: Key('habits-excuse-${habit.id}'),
                  label: 'Excuse',
                  selected: habit.state == TodayHabitState.excused,
                  onPressed: pending
                      ? null
                      : () => onCheckIn(TodayHabitCheckIn.excused),
                ),
                if (habit.state == TodayHabitState.done ||
                    habit.state == TodayHabitState.excused)
                  _Action(
                    key: Key('habits-clear-${habit.id}'),
                    label: 'Clear',
                    selected: false,
                    onPressed: pending ? null : () => onCheckIn(null),
                  ),
              ],
            ),
        ],
      ),
    );
  }
}

class _Action extends StatelessWidget {
  const _Action({
    super.key,
    required this.label,
    required this.selected,
    required this.onPressed,
  });

  final String label;
  final bool selected;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return selected
        ? CupertinoButton.filled(
            minimumSize: const Size(0, NorthstarLayout.minimumTouchTarget),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            onPressed: onPressed,
            child: Text(label),
          )
        : CupertinoButton.tinted(
            minimumSize: const Size(0, NorthstarLayout.minimumTouchTarget),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            onPressed: onPressed,
            child: Text(label),
          );
  }
}

class _HabitsMessage extends StatelessWidget {
  const _HabitsMessage({
    super.key,
    required this.title,
    required this.message,
    required this.onRetry,
    this.actionLabel = 'Try again',
  });

  final String title;
  final String message;
  final Future<void> Function() onRetry;
  final String actionLabel;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(NorthstarSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(CupertinoIcons.circle_grid_hex, size: 40),
            const SizedBox(height: NorthstarSpacing.md),
            Text(title, style: NorthstarTextStyles.sectionTitle(context)),
            const SizedBox(height: NorthstarSpacing.xs),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: NorthstarSpacing.lg),
            CupertinoButton.filled(
              key: const Key('habits-message-action'),
              onPressed: onRetry,
              child: Text(actionLabel),
            ),
          ],
        ),
      ),
    );
  }
}
