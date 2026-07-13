import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:northstar/domain/models/today_models.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/today/view_models/today_view_model.dart';

class TodayView extends StatefulWidget {
  const TodayView({super.key, required this.viewModel});

  final TodayViewModel viewModel;

  @override
  State<TodayView> createState() => _TodayViewState();
}

class _TodayViewState extends State<TodayView> {
  TodayViewModel get _viewModel => widget.viewModel;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.initialize());
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('today-page'),
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Today'),
        trailing: CupertinoButton(
          key: const Key('today-refresh-button'),
          padding: EdgeInsets.zero,
          onPressed: _viewModel.isRefreshing ? null : _viewModel.refresh,
          child: _viewModel.isRefreshing
              ? const CupertinoActivityIndicator(radius: 9)
              : const Icon(
                  CupertinoIcons.refresh,
                  semanticLabel: 'Refresh Today',
                ),
        ),
      ),
      child: SafeArea(
        bottom: false,
        child: AnimatedBuilder(
          animation: _viewModel,
          builder: (context, _) => _buildBody(context),
        ),
      ),
    );
  }

  Widget _buildBody(BuildContext context) {
    if (_viewModel.phase == TodayLoadPhase.loading) {
      return const Center(
        child: CupertinoActivityIndicator(
          key: Key('today-loading-indicator'),
          radius: 14,
        ),
      );
    }
    if (_viewModel.phase == TodayLoadPhase.error) {
      return _TodayLoadError(
        message: _viewModel.loadError ?? 'Today is unavailable.',
        onRetry: _viewModel.refresh,
      );
    }
    if (!_viewModel.hasData) {
      return const SizedBox.shrink();
    }

    return CustomScrollView(
      key: const Key('today-scroll-view'),
      slivers: [
        CupertinoSliverRefreshControl(onRefresh: _viewModel.refresh),
        SliverPadding(
          padding: const EdgeInsets.only(
            top: NorthstarSpacing.md,
            bottom: NorthstarSpacing.xxl,
          ),
          sliver: SliverToBoxAdapter(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final content = _TodayContent(viewModel: _viewModel);
                if (constraints.maxWidth < 840) {
                  return Center(
                    key: const Key('today-compact-layout'),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(
                        maxWidth: NorthstarLayout.readableContentWidth,
                      ),
                      child: content,
                    ),
                  );
                }
                return Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 1120),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (_viewModel.actionError case final message?) ...[
                          _ActionErrorBanner(
                            message: message,
                            onRetry: _viewModel.retryLastAction,
                            onDismiss: _viewModel.clearActionError,
                          ),
                          const SizedBox(height: NorthstarSpacing.md),
                        ],
                        Row(
                          key: const Key('today-expanded-layout'),
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: _TodayTaskColumn(viewModel: _viewModel),
                            ),
                            const SizedBox(width: NorthstarSpacing.md),
                            Expanded(
                              child: _TodayContextColumn(viewModel: _viewModel),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ),
      ],
    );
  }
}

class _TodayContent extends StatelessWidget {
  const _TodayContent({required this.viewModel});

  final TodayViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (viewModel.actionError case final message?)
          _ActionErrorBanner(
            message: message,
            onRetry: viewModel.retryLastAction,
            onDismiss: viewModel.clearActionError,
          ),
        _TodayTaskColumn(viewModel: viewModel),
        _TodayContextColumn(viewModel: viewModel),
      ],
    );
  }
}

class _TodayTaskColumn extends StatelessWidget {
  const _TodayTaskColumn({required this.viewModel});

  final TodayViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        CupertinoListSection.insetGrouped(
          header: const Text('TASKS TODAY'),
          children: viewModel.todayTasks.isEmpty
              ? const [
                  CupertinoListTile(
                    key: Key('today-tasks-empty'),
                    leading: Icon(CupertinoIcons.check_mark_circled),
                    title: Text('Nothing due today'),
                    subtitle: Text('Ask Assistant to plan or capture a task.'),
                  ),
                ]
              : [
                  for (final task in viewModel.todayTasks)
                    _TaskTile(
                      task: task,
                      pending: viewModel.isTaskPending(task.id),
                      onToggle: () => viewModel.toggleTask(task.id),
                    ),
                ],
        ),
        CupertinoListSection.insetGrouped(
          header: const Text('UPCOMING'),
          children: viewModel.upcomingTasks.isEmpty
              ? const [
                  CupertinoListTile(
                    key: Key('today-upcoming-empty'),
                    leading: Icon(CupertinoIcons.calendar_badge_plus),
                    title: Text('No upcoming tasks'),
                  ),
                ]
              : [
                  for (final task in viewModel.upcomingTasks.take(3))
                    _TaskTile(
                      task: task,
                      pending: viewModel.isTaskPending(task.id),
                      onToggle: () => viewModel.toggleTask(task.id),
                    ),
                ],
        ),
      ],
    );
  }
}

class _TodayContextColumn extends StatelessWidget {
  const _TodayContextColumn({required this.viewModel});

  final TodayViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _NextEventSection(event: viewModel.nextEvent),
        _HabitsSection(viewModel: viewModel),
      ],
    );
  }
}

class _TaskTile extends StatelessWidget {
  const _TaskTile({
    required this.task,
    required this.pending,
    required this.onToggle,
  });

  final TodayTask task;
  final bool pending;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    final metadata = [
      if (task.plannedDate case final date?) 'Planned $date',
      if (task.dueDate case final date?)
        task.dueTime == null ? 'Due $date' : 'Due $date · ${task.dueTime}',
    ].join('  ·  ');
    return CupertinoListTile(
      key: Key('today-task-${task.id}'),
      leadingSize: NorthstarLayout.minimumTouchTarget,
      leading: Semantics(
        button: true,
        label: task.isDone ? 'Reopen ${task.title}' : 'Complete ${task.title}',
        child: CupertinoButton(
          key: Key('today-task-toggle-${task.id}'),
          padding: EdgeInsets.zero,
          minimumSize: const Size.square(NorthstarLayout.minimumTouchTarget),
          onPressed: pending ? null : onToggle,
          child: pending
              ? const CupertinoActivityIndicator(radius: 9)
              : Icon(
                  task.isDone
                      ? CupertinoIcons.check_mark_circled_solid
                      : CupertinoIcons.circle,
                  color: task.isDone
                      ? NorthstarColors.positive.resolveFrom(context)
                      : NorthstarColors.secondaryText.resolveFrom(context),
                ),
        ),
      ),
      title: Text(
        task.title,
        style: task.isDone
            ? const TextStyle(decoration: TextDecoration.lineThrough)
            : null,
      ),
      subtitle: metadata.isEmpty ? null : Text(metadata),
    );
  }
}

class _NextEventSection extends StatelessWidget {
  const _NextEventSection({required this.event});

  final TodayCalendarEvent? event;

  @override
  Widget build(BuildContext context) {
    return CupertinoListSection.insetGrouped(
      header: const Text('NEXT EVENT'),
      children: [
        if (event case final event?)
          CupertinoListTile(
            key: const Key('today-next-event'),
            leading: const Icon(CupertinoIcons.calendar),
            title: Text(event.title),
            subtitle: Text(_eventTime(event)),
          )
        else
          const CupertinoListTile(
            key: Key('today-next-event-empty'),
            leading: Icon(CupertinoIcons.calendar),
            title: Text('No event in the next 7 days'),
          ),
      ],
    );
  }

  String _eventTime(TodayCalendarEvent event) {
    if (event.allDay) {
      return 'All day · ${_shortDate(event.startAt.toLocal())}';
    }
    final start = event.startAt.toLocal();
    final end = event.endAt.toLocal();
    return '${_shortDate(start)} · ${_time(start)}–${_time(end)}';
  }

  String _shortDate(DateTime value) => '${value.day}/${value.month}';

  String _time(DateTime value) {
    return '${value.hour.toString().padLeft(2, '0')}:'
        '${value.minute.toString().padLeft(2, '0')}';
  }
}

class _HabitsSection extends StatelessWidget {
  const _HabitsSection({required this.viewModel});

  final TodayViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    return CupertinoListSection.insetGrouped(
      header: const Text('HABITS'),
      children: viewModel.habits.isEmpty
          ? const [
              CupertinoListTile(
                key: Key('today-habits-empty'),
                leading: Icon(CupertinoIcons.circle_grid_hex),
                title: Text('No habits due today'),
              ),
            ]
          : [
              for (final habit in viewModel.habits)
                _HabitTile(
                  habit: habit,
                  pending: viewModel.isHabitPending(habit.id),
                  onCheckIn: (status) =>
                      viewModel.setHabitCheckIn(habit.id, status),
                ),
            ],
    );
  }
}

class _HabitTile extends StatelessWidget {
  const _HabitTile({
    required this.habit,
    required this.pending,
    required this.onCheckIn,
  });

  final TodayHabit habit;
  final bool pending;
  final ValueChanged<TodayHabitCheckIn?> onCheckIn;

  @override
  Widget build(BuildContext context) {
    final actionable = switch (habit.state) {
      TodayHabitState.paused || TodayHabitState.notScheduled => false,
      _ => true,
    };
    return Padding(
      key: Key('today-habit-${habit.id}'),
      padding: const EdgeInsets.fromLTRB(
        NorthstarSpacing.md,
        NorthstarSpacing.sm,
        NorthstarSpacing.md,
        NorthstarSpacing.md,
      ),
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
                const CupertinoActivityIndicator(
                  key: Key('today-habit-pending'),
                  radius: 9,
                )
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
            Text(_habitStateLabel(habit.state))
          else
            Wrap(
              spacing: NorthstarSpacing.xs,
              runSpacing: NorthstarSpacing.xs,
              children: [
                _HabitAction(
                  key: Key('today-habit-done-${habit.id}'),
                  label: 'Done',
                  icon: CupertinoIcons.check_mark,
                  selected: habit.state == TodayHabitState.done,
                  onPressed: pending
                      ? null
                      : () => onCheckIn(TodayHabitCheckIn.done),
                ),
                _HabitAction(
                  key: Key('today-habit-excuse-${habit.id}'),
                  label: 'Excuse',
                  icon: CupertinoIcons.forward,
                  selected: habit.state == TodayHabitState.excused,
                  onPressed: pending
                      ? null
                      : () => onCheckIn(TodayHabitCheckIn.excused),
                ),
                if (habit.state == TodayHabitState.done ||
                    habit.state == TodayHabitState.excused)
                  _HabitAction(
                    key: Key('today-habit-clear-${habit.id}'),
                    label: 'Clear',
                    icon: CupertinoIcons.clear,
                    selected: false,
                    onPressed: pending ? null : () => onCheckIn(null),
                  ),
              ],
            ),
        ],
      ),
    );
  }

  String _habitStateLabel(TodayHabitState state) => switch (state) {
    TodayHabitState.paused => 'Paused',
    TodayHabitState.notScheduled => 'Not scheduled today',
    TodayHabitState.missed => 'Missed',
    TodayHabitState.open => 'Open',
    TodayHabitState.done => 'Done',
    TodayHabitState.excused => 'Excused',
  };
}

class _HabitAction extends StatelessWidget {
  const _HabitAction({
    super.key,
    required this.label,
    required this.icon,
    required this.selected,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final child = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16),
        const SizedBox(width: NorthstarSpacing.xxs),
        Text(label),
      ],
    );
    return Semantics(
      button: true,
      selected: selected,
      label: label,
      child: selected
          ? CupertinoButton.filled(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              minimumSize: const Size(0, NorthstarLayout.minimumTouchTarget),
              onPressed: onPressed,
              child: child,
            )
          : CupertinoButton.tinted(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              minimumSize: const Size(0, NorthstarLayout.minimumTouchTarget),
              onPressed: onPressed,
              child: child,
            ),
    );
  }
}

class _ActionErrorBanner extends StatelessWidget {
  const _ActionErrorBanner({
    required this.message,
    required this.onRetry,
    required this.onDismiss,
  });

  final String message;
  final Future<void> Function() onRetry;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('today-action-error'),
      margin: const EdgeInsets.symmetric(horizontal: NorthstarSpacing.md),
      padding: const EdgeInsets.all(NorthstarSpacing.md),
      decoration: BoxDecoration(
        color: CupertinoColors.systemRed.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(NorthstarRadii.md),
      ),
      child: Row(
        children: [
          const Icon(
            CupertinoIcons.exclamationmark_triangle,
            color: CupertinoColors.systemRed,
          ),
          const SizedBox(width: NorthstarSpacing.sm),
          Expanded(child: Text(message)),
          CupertinoButton(
            key: const Key('today-action-retry'),
            padding: const EdgeInsets.symmetric(horizontal: 8),
            onPressed: onRetry,
            child: const Text('Retry'),
          ),
          CupertinoButton(
            key: const Key('today-action-dismiss'),
            padding: EdgeInsets.zero,
            minimumSize: const Size.square(NorthstarLayout.minimumTouchTarget),
            onPressed: onDismiss,
            child: const Icon(CupertinoIcons.clear, semanticLabel: 'Dismiss'),
          ),
        ],
      ),
    );
  }
}

class _TodayLoadError extends StatelessWidget {
  const _TodayLoadError({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(NorthstarSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              CupertinoIcons.wifi_exclamationmark,
              size: 36,
              color: CupertinoColors.systemOrange,
            ),
            const SizedBox(height: NorthstarSpacing.md),
            Text(
              'Couldn’t load Today',
              style: NorthstarTextStyles.sectionTitle(context),
            ),
            const SizedBox(height: NorthstarSpacing.xs),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: NorthstarSpacing.lg),
            CupertinoButton.filled(
              key: const Key('today-load-retry'),
              onPressed: onRetry,
              child: const Text('Try again'),
            ),
          ],
        ),
      ),
    );
  }
}
