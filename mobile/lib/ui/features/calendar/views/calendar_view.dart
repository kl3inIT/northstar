import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:intl/intl.dart';
import 'package:northstar/domain/models/calendar_models.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/calendar/view_models/calendar_view_model.dart';

class CalendarView extends StatefulWidget {
  const CalendarView({super.key, required this.viewModel});

  final CalendarViewModel viewModel;

  @override
  State<CalendarView> createState() => _CalendarViewState();
}

class _CalendarViewState extends State<CalendarView> {
  CalendarViewModel get _viewModel => widget.viewModel;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.initialize());
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('calendar-page'),
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Calendar'),
        previousPageTitle: 'More',
      ),
      child: SafeArea(
        bottom: false,
        child: AnimatedBuilder(
          animation: _viewModel,
          builder: (context, _) => Column(
            children: [
              _CalendarRangeBar(viewModel: _viewModel),
              Expanded(child: _body(context)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _body(BuildContext context) {
    if (_viewModel.phase == CalendarPhase.loading) {
      return const Center(
        child: CupertinoActivityIndicator(
          key: Key('calendar-loading-indicator'),
          radius: 14,
        ),
      );
    }
    if (_viewModel.phase == CalendarPhase.error) {
      return _CalendarMessage(
        key: const Key('calendar-load-error'),
        icon: CupertinoIcons.wifi_exclamationmark,
        title: 'Couldn’t load Calendar',
        message: _viewModel.errorMessage ?? 'Calendar is unavailable.',
        onRetry: _viewModel.load,
      );
    }
    if (_viewModel.events.isEmpty) {
      return _CalendarMessage(
        key: const Key('calendar-empty'),
        icon: CupertinoIcons.calendar,
        title: 'No events in this range',
        message: 'Create an event through Assistant or Capture.',
        onRetry: _viewModel.goToToday,
        actionLabel: 'Back to today',
      );
    }
    final groups = <DateTime, List<CalendarAgendaEvent>>{};
    for (final event in _viewModel.events) {
      final local = event.startAt.toLocal();
      final day = DateTime(local.year, local.month, local.day);
      groups.putIfAbsent(day, () => []).add(event);
    }
    return ListView(
      key: const Key('calendar-agenda'),
      padding: const EdgeInsets.only(bottom: NorthstarSpacing.xxl),
      children: [
        Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: NorthstarLayout.readableContentWidth,
            ),
            child: Column(
              children: [
                for (final entry in groups.entries)
                  CupertinoListSection.insetGrouped(
                    header: Text(
                      DateFormat('EEEE, d MMM').format(entry.key).toUpperCase(),
                    ),
                    children: [
                      for (final event in entry.value)
                        CupertinoListTile(
                          key: Key(
                            'calendar-event-${event.id}-${event.startAt.millisecondsSinceEpoch}',
                          ),
                          leading: Icon(
                            event.rrule == null
                                ? CupertinoIcons.calendar
                                : CupertinoIcons.repeat,
                          ),
                          title: Text(event.title),
                          subtitle: event.notes == null || event.notes!.isEmpty
                              ? null
                              : Text(
                                  event.notes!,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                ),
                          additionalInfo: Text(_eventTime(event)),
                        ),
                    ],
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  String _eventTime(CalendarAgendaEvent event) {
    if (event.allDay) return 'All day';
    final start = event.startAt.toLocal();
    final end = event.endAt.toLocal();
    final format = DateFormat.Hm();
    return '${format.format(start)}–${format.format(end)}';
  }
}

class _CalendarRangeBar extends StatelessWidget {
  const _CalendarRangeBar({required this.viewModel});

  final CalendarViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    final busy = viewModel.phase == CalendarPhase.loading;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        NorthstarSpacing.md,
        NorthstarSpacing.sm,
        NorthstarSpacing.md,
        NorthstarSpacing.xs,
      ),
      child: Row(
        children: [
          CupertinoButton(
            key: const Key('calendar-previous-range'),
            padding: EdgeInsets.zero,
            minimumSize: const Size.square(NorthstarLayout.minimumTouchTarget),
            onPressed: busy ? null : viewModel.previousRange,
            child: const Icon(CupertinoIcons.chevron_left),
          ),
          Expanded(
            child: CupertinoButton(
              key: const Key('calendar-today-button'),
              padding: const EdgeInsets.symmetric(horizontal: 8),
              onPressed: busy ? null : viewModel.goToToday,
              child: Text(
                '${DateFormat('d MMM').format(viewModel.anchor)} – '
                '${DateFormat('d MMM').format(viewModel.through.subtract(const Duration(days: 1)))}',
              ),
            ),
          ),
          CupertinoButton(
            key: const Key('calendar-next-range'),
            padding: EdgeInsets.zero,
            minimumSize: const Size.square(NorthstarLayout.minimumTouchTarget),
            onPressed: busy ? null : viewModel.nextRange,
            child: const Icon(CupertinoIcons.chevron_right),
          ),
        ],
      ),
    );
  }
}

class _CalendarMessage extends StatelessWidget {
  const _CalendarMessage({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    required this.onRetry,
    this.actionLabel = 'Try again',
  });

  final IconData icon;
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
            Icon(icon, size: 40),
            const SizedBox(height: NorthstarSpacing.md),
            Text(title, style: NorthstarTextStyles.sectionTitle(context)),
            const SizedBox(height: NorthstarSpacing.xs),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: NorthstarSpacing.lg),
            CupertinoButton.filled(
              key: const Key('calendar-message-action'),
              onPressed: onRetry,
              child: Text(actionLabel),
            ),
          ],
        ),
      ),
    );
  }
}
