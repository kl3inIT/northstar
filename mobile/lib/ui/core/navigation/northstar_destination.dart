import 'package:flutter/cupertino.dart';

enum NorthstarDestination {
  assistant(
    label: 'Assistant',
    icon: CupertinoIcons.chat_bubble_2,
    selectedIcon: CupertinoIcons.chat_bubble_2_fill,
  ),
  tasks(
    label: 'Tasks',
    icon: CupertinoIcons.check_mark_circled,
    selectedIcon: CupertinoIcons.check_mark_circled_solid,
  ),
  notes(
    label: 'Notes',
    icon: CupertinoIcons.doc_text,
    selectedIcon: CupertinoIcons.doc_text_fill,
  ),
  finance(
    label: 'Finance',
    icon: CupertinoIcons.chart_bar,
    selectedIcon: CupertinoIcons.chart_bar_fill,
  ),
  more(
    label: 'More',
    icon: CupertinoIcons.ellipsis_circle,
    selectedIcon: CupertinoIcons.ellipsis_circle_fill,
  );

  const NorthstarDestination({
    required this.label,
    required this.icon,
    required this.selectedIcon,
  });

  final String label;
  final IconData icon;
  final IconData selectedIcon;
}
