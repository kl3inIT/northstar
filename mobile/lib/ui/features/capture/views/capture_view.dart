import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:northstar/domain/models/capture_models.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/capture/view_models/capture_view_model.dart';

class CaptureView extends StatefulWidget {
  const CaptureView({
    super.key,
    required this.viewModel,
    this.promptForReceiptSource = false,
  });

  final CaptureViewModel viewModel;
  final bool promptForReceiptSource;

  @override
  State<CaptureView> createState() => _CaptureViewState();
}

class _CaptureViewState extends State<CaptureView> {
  final _textController = TextEditingController();
  final _textFocus = FocusNode();

  CaptureViewModel get _viewModel => widget.viewModel;

  @override
  void initState() {
    super.initState();
    _viewModel.addListener(_handleChange);
    _textController.text = _viewModel.text;
    if (widget.promptForReceiptSource) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _showReceiptSourcePicker();
        }
      });
    }
  }

  Future<void> _showReceiptSourcePicker() async {
    final source = await showCupertinoModalPopup<ReceiptSource>(
      context: context,
      builder: (sheetContext) => CupertinoActionSheet(
        title: const Text('Scan receipt'),
        message: const Text(
          'Choose a clear image. You will review the extracted transaction '
          'before it is saved.',
        ),
        actions: [
          if (!kIsWeb)
            CupertinoActionSheetAction(
              key: const Key('capture-receipt-camera'),
              onPressed: () =>
                  Navigator.of(sheetContext).pop(ReceiptSource.camera),
              child: const Text('Take photo'),
            ),
          CupertinoActionSheetAction(
            key: const Key('capture-receipt-library'),
            onPressed: () =>
                Navigator.of(sheetContext).pop(ReceiptSource.photoLibrary),
            child: const Text('Choose from Photos'),
          ),
        ],
        cancelButton: CupertinoActionSheetAction(
          key: const Key('capture-receipt-cancel'),
          onPressed: () => Navigator.of(sheetContext).pop(),
          child: const Text('Cancel'),
        ),
      ),
    );
    if (source != null && mounted) {
      await _viewModel.draftReceipt(source);
    }
  }

  @override
  void didUpdateWidget(covariant CaptureView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.viewModel != widget.viewModel) {
      oldWidget.viewModel.removeListener(_handleChange);
      _viewModel.addListener(_handleChange);
      _textController.text = _viewModel.text;
    }
  }

  void _handleChange() {
    if (!mounted) {
      return;
    }
    if (_viewModel.phase == CapturePhase.input &&
        _textController.text != _viewModel.text) {
      _textController.value = TextEditingValue(
        text: _viewModel.text,
        selection: TextSelection.collapsed(offset: _viewModel.text.length),
      );
    }
    setState(() {});
  }

  @override
  void dispose() {
    _viewModel.removeListener(_handleChange);
    _textController.dispose();
    _textFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('capture-page'),
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Capture'),
        previousPageTitle: 'Assistant',
      ),
      child: SafeArea(
        bottom: false,
        child: AnimatedSwitcher(
          duration: const Duration(milliseconds: 180),
          child: switch (_viewModel.phase) {
            CapturePhase.input || CapturePhase.drafting => _CaptureInput(
              key: const ValueKey('capture-input'),
              viewModel: _viewModel,
              textController: _textController,
              textFocus: _textFocus,
            ),
            CapturePhase.review || CapturePhase.saving => _CaptureReview(
              key: ValueKey('capture-review-${_viewModel.draftVersion}'),
              viewModel: _viewModel,
            ),
            CapturePhase.saved ||
            CapturePhase.undoing ||
            CapturePhase.undone => _CaptureSaved(
              key: const ValueKey('capture-saved'),
              viewModel: _viewModel,
            ),
          },
        ),
      ),
    );
  }
}

class _CaptureInput extends StatelessWidget {
  const _CaptureInput({
    super.key,
    required this.viewModel,
    required this.textController,
    required this.textFocus,
  });

  final CaptureViewModel viewModel;
  final TextEditingController textController;
  final FocusNode textFocus;

  @override
  Widget build(BuildContext context) {
    final drafting = viewModel.phase == CapturePhase.drafting;
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        NorthstarSpacing.md,
        NorthstarSpacing.lg,
        NorthstarSpacing.md,
        NorthstarSpacing.xxl,
      ),
      children: [
        Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 620),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Capture it now',
                  style: NorthstarTextStyles.hero(context),
                ),
                const SizedBox(height: NorthstarSpacing.xs),
                Text(
                  'Northstar will draft a task, note, event, expense, study '
                  'log, or vocabulary card. '
                  'You review it before anything is saved.',
                  style: NorthstarTextStyles.body(context).copyWith(
                    color: NorthstarColors.secondaryText.resolveFrom(context),
                  ),
                ),
                const SizedBox(height: NorthstarSpacing.lg),
                CupertinoTextField(
                  key: const Key('capture-text-field'),
                  controller: textController,
                  focusNode: textFocus,
                  enabled: !drafting,
                  autofocus: !kIsWeb,
                  minLines: 5,
                  maxLines: 10,
                  maxLength: 20000,
                  placeholder: 'Type or paste something…',
                  padding: const EdgeInsets.all(NorthstarSpacing.md),
                  onChanged: viewModel.setText,
                ),
                const SizedBox(height: NorthstarSpacing.sm),
                Text(
                  'Classify as',
                  style: NorthstarTextStyles.caption(context),
                ),
                const SizedBox(height: NorthstarSpacing.xs),
                Wrap(
                  spacing: NorthstarSpacing.xs,
                  runSpacing: NorthstarSpacing.xs,
                  children: [
                    _KindButton(
                      label: 'Auto',
                      icon: CupertinoIcons.sparkles,
                      selected: viewModel.forcedKind == null,
                      onPressed: () => viewModel.setForcedKind(null),
                    ),
                    for (final kind in CaptureKind.values)
                      _KindButton(
                        label: _kindLabel(kind),
                        icon: _kindIcon(kind),
                        selected: viewModel.forcedKind == kind,
                        onPressed: () => viewModel.setForcedKind(kind),
                      ),
                  ],
                ),
                const SizedBox(height: NorthstarSpacing.lg),
                SizedBox(
                  width: double.infinity,
                  child: CupertinoButton.filled(
                    key: const Key('capture-draft-button'),
                    onPressed: drafting ? null : viewModel.draftText,
                    child: drafting
                        ? const CupertinoActivityIndicator(
                            key: Key('capture-drafting-indicator'),
                          )
                        : const Text('Review capture'),
                  ),
                ),
                const SizedBox(height: NorthstarSpacing.md),
                Column(
                  children: [
                    SizedBox(
                      width: double.infinity,
                      child: CupertinoButton.tinted(
                        key: const Key('capture-photo-button'),
                        onPressed: drafting
                            ? null
                            : () => viewModel.draftReceipt(
                                ReceiptSource.photoLibrary,
                              ),
                        child: const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(CupertinoIcons.photo),
                            SizedBox(width: NorthstarSpacing.xs),
                            Text('Receipt photo'),
                          ],
                        ),
                      ),
                    ),
                    if (!kIsWeb) ...[
                      const SizedBox(height: NorthstarSpacing.sm),
                      SizedBox(
                        width: double.infinity,
                        child: CupertinoButton.tinted(
                          key: const Key('capture-camera-button'),
                          onPressed: drafting
                              ? null
                              : () => viewModel.draftReceipt(
                                  ReceiptSource.camera,
                                ),
                          child: const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(CupertinoIcons.camera),
                              SizedBox(width: NorthstarSpacing.xs),
                              Text('Camera'),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
                if (viewModel.errorMessage case final error?) ...[
                  const SizedBox(height: NorthstarSpacing.md),
                  _CaptureError(message: error, onRetry: viewModel.retry),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _CaptureReview extends StatelessWidget {
  const _CaptureReview({super.key, required this.viewModel});

  final CaptureViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    final draft = viewModel.draft;
    if (draft == null) {
      return const Center(child: CupertinoActivityIndicator());
    }
    final saving = viewModel.phase == CapturePhase.saving;
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        NorthstarSpacing.md,
        NorthstarSpacing.lg,
        NorthstarSpacing.md,
        NorthstarSpacing.xxl,
      ),
      children: [
        Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 620),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      _kindIcon(draft.kind),
                      color: NorthstarColors.accent.resolveFrom(context),
                    ),
                    const SizedBox(width: NorthstarSpacing.xs),
                    Text(
                      'Review ${_kindLabel(draft.kind).toLowerCase()}',
                      style: NorthstarTextStyles.sectionTitle(context),
                    ),
                  ],
                ),
                const SizedBox(height: NorthstarSpacing.xs),
                Text(
                  'AI drafted these fields. Correct anything that is wrong '
                  'before saving.',
                  style: NorthstarTextStyles.caption(context),
                ),
                const SizedBox(height: NorthstarSpacing.md),
                _DraftEditor(viewModel: viewModel, draft: draft),
                if (viewModel.errorMessage case final error?) ...[
                  const SizedBox(height: NorthstarSpacing.md),
                  _CaptureError(message: error, onRetry: viewModel.retry),
                ],
                const SizedBox(height: NorthstarSpacing.lg),
                Row(
                  children: [
                    Expanded(
                      child: CupertinoButton(
                        key: const Key('capture-edit-input-button'),
                        onPressed: saving ? null : viewModel.editInput,
                        child: const Text('Back'),
                      ),
                    ),
                    const SizedBox(width: NorthstarSpacing.sm),
                    Expanded(
                      flex: 2,
                      child: CupertinoButton.filled(
                        key: const Key('capture-save-button'),
                        onPressed: saving ? null : viewModel.save,
                        child: saving
                            ? const CupertinoActivityIndicator(
                                key: Key('capture-saving-indicator'),
                              )
                            : Text('Save ${_kindLabel(draft.kind)}'),
                      ),
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
}

class _DraftEditor extends StatelessWidget {
  const _DraftEditor({required this.viewModel, required this.draft});

  final CaptureViewModel viewModel;
  final CaptureDraft draft;

  @override
  Widget build(BuildContext context) {
    return switch (draft) {
      NoteCaptureDraft() => _NoteDraftEditor(
        key: ValueKey('note-${viewModel.draftVersion}'),
        viewModel: viewModel,
        draft: draft as NoteCaptureDraft,
      ),
      TaskCaptureDraft() => _TaskDraftEditor(
        key: ValueKey('task-${viewModel.draftVersion}'),
        viewModel: viewModel,
        draft: draft as TaskCaptureDraft,
      ),
      EventCaptureDraft() => _EventDraftEditor(
        key: ValueKey('event-${viewModel.draftVersion}'),
        viewModel: viewModel,
        draft: draft as EventCaptureDraft,
      ),
      ExpenseCaptureDraft() => _ExpenseDraftEditor(
        key: ValueKey('expense-${viewModel.draftVersion}'),
        viewModel: viewModel,
        draft: draft as ExpenseCaptureDraft,
      ),
      StudyCaptureDraft() => _StudyDraftEditor(
        key: ValueKey('study-${viewModel.draftVersion}'),
        viewModel: viewModel,
        draft: draft as StudyCaptureDraft,
      ),
      VocabCaptureDraft() => _VocabDraftEditor(
        key: ValueKey('vocab-${viewModel.draftVersion}'),
        viewModel: viewModel,
        draft: draft as VocabCaptureDraft,
      ),
    };
  }
}

class _NoteDraftEditor extends StatelessWidget {
  const _NoteDraftEditor({
    super.key,
    required this.viewModel,
    required this.draft,
  });

  final CaptureViewModel viewModel;
  final NoteCaptureDraft draft;

  @override
  Widget build(BuildContext context) {
    return CupertinoFormSection.insetGrouped(
      header: const Text('NOTE DRAFT · SAVES TO STAGING'),
      children: [
        CupertinoTextFormFieldRow(
          key: const Key('capture-note-title'),
          prefix: const Text('Title'),
          initialValue: draft.title,
          onChanged: (value) => viewModel.updateNote(title: value),
        ),
        CupertinoTextFormFieldRow(
          prefix: const Text('Folder'),
          initialValue: draft.folderPath,
          placeholder: 'Root',
          onChanged: (value) => viewModel.updateNote(folderPath: value),
        ),
        CupertinoTextFormFieldRow(
          key: const Key('capture-note-content'),
          prefix: const Text('Content'),
          initialValue: draft.contentMarkdown,
          minLines: 4,
          maxLines: 10,
          textAlign: TextAlign.start,
          onChanged: (value) => viewModel.updateNote(contentMarkdown: value),
        ),
        if (draft.tags.isNotEmpty)
          CupertinoFormRow(
            prefix: const Text('Tags'),
            child: Text(draft.tags.join(', ')),
          ),
      ],
    );
  }
}

class _TaskDraftEditor extends StatelessWidget {
  const _TaskDraftEditor({
    super.key,
    required this.viewModel,
    required this.draft,
  });

  final CaptureViewModel viewModel;
  final TaskCaptureDraft draft;

  @override
  Widget build(BuildContext context) {
    return CupertinoFormSection.insetGrouped(
      header: const Text('TASK DRAFT'),
      children: [
        CupertinoTextFormFieldRow(
          key: const Key('capture-task-title'),
          prefix: const Text('Title'),
          initialValue: draft.title,
          onChanged: (value) => viewModel.updateTask(title: value),
        ),
        CupertinoTextFormFieldRow(
          prefix: const Text('Notes'),
          initialValue: draft.notes,
          minLines: 2,
          maxLines: 5,
          onChanged: (value) => viewModel.updateTask(notes: value),
        ),
        CupertinoFormRow(
          prefix: const Text('Due'),
          child: Text(
            [
              draft.dueDate,
              draft.dueTime,
            ].whereType<String>().join(' · ').ifEmpty('Someday'),
          ),
        ),
        if (draft.disciplineName case final discipline?)
          CupertinoFormRow(
            prefix: const Text('Discipline'),
            child: Text(discipline),
          ),
      ],
    );
  }
}

class _EventDraftEditor extends StatelessWidget {
  const _EventDraftEditor({
    super.key,
    required this.viewModel,
    required this.draft,
  });

  final CaptureViewModel viewModel;
  final EventCaptureDraft draft;

  @override
  Widget build(BuildContext context) {
    return CupertinoFormSection.insetGrouped(
      header: const Text('EVENT DRAFT · LOCAL TIME'),
      children: [
        CupertinoTextFormFieldRow(
          key: const Key('capture-event-title'),
          prefix: const Text('Title'),
          initialValue: draft.title,
          onChanged: (value) => viewModel.updateEvent(title: value),
        ),
        CupertinoTextFormFieldRow(
          prefix: const Text('Date'),
          initialValue: draft.date,
          placeholder: 'yyyy-MM-dd',
          keyboardType: TextInputType.datetime,
          onChanged: (value) => viewModel.updateEvent(date: value),
        ),
        CupertinoTextFormFieldRow(
          prefix: const Text('Start'),
          initialValue: draft.startTime ?? '',
          placeholder: 'All day',
          keyboardType: TextInputType.datetime,
          onChanged: (value) => viewModel.updateEvent(startTime: value),
        ),
        CupertinoTextFormFieldRow(
          prefix: const Text('End'),
          initialValue: draft.endTime ?? '',
          placeholder: 'Automatic',
          keyboardType: TextInputType.datetime,
          onChanged: (value) => viewModel.updateEvent(endTime: value),
        ),
        CupertinoTextFormFieldRow(
          prefix: const Text('Notes'),
          initialValue: draft.notes,
          minLines: 2,
          maxLines: 5,
          onChanged: (value) => viewModel.updateEvent(notes: value),
        ),
      ],
    );
  }
}

class _ExpenseDraftEditor extends StatelessWidget {
  const _ExpenseDraftEditor({
    super.key,
    required this.viewModel,
    required this.draft,
  });

  final CaptureViewModel viewModel;
  final ExpenseCaptureDraft draft;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (var index = 0; index < draft.items.length; index += 1)
          _ExpenseItemEditor(
            key: ValueKey('expense-${viewModel.draftVersion}-$index'),
            index: index,
            item: draft.items[index],
            onChanged: (item) => viewModel.updateExpenseItem(index, item),
          ),
      ],
    );
  }
}

class _ExpenseItemEditor extends StatelessWidget {
  const _ExpenseItemEditor({
    super.key,
    required this.index,
    required this.item,
    required this.onChanged,
  });

  final int index;
  final ExpenseCaptureItem item;
  final ValueChanged<ExpenseCaptureItem> onChanged;

  @override
  Widget build(BuildContext context) {
    return CupertinoFormSection.insetGrouped(
      header: Text('ITEM ${index + 1} · ${item.type.name.toUpperCase()}'),
      children: [
        CupertinoTextFormFieldRow(
          key: Key('capture-expense-description-$index'),
          prefix: const Text('Description'),
          initialValue: item.description,
          onChanged: (value) => onChanged(item.copyWith(description: value)),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-expense-amount-$index'),
          prefix: const Text('Amount'),
          initialValue: item.amount.toString(),
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          onChanged: (value) {
            onChanged(item.copyWith(amount: int.tryParse(value) ?? 0));
          },
        ),
        CupertinoTextFormFieldRow(
          prefix: const Text('Date'),
          initialValue: item.occurredOn,
          keyboardType: TextInputType.datetime,
          onChanged: (value) => onChanged(item.copyWith(occurredOn: value)),
        ),
        CupertinoTextFormFieldRow(
          prefix: const Text('Category'),
          initialValue: item.category,
          placeholder: 'Automatic',
          onChanged: (value) => onChanged(item.copyWith(category: value)),
        ),
        CupertinoFormRow(
          prefix: const Text('One-off'),
          child: CupertinoSwitch(
            value: item.exceptional,
            onChanged: (value) => onChanged(item.copyWith(exceptional: value)),
          ),
        ),
      ],
    );
  }
}

class _StudyDraftEditor extends StatelessWidget {
  const _StudyDraftEditor({
    super.key,
    required this.viewModel,
    required this.draft,
  });

  final CaptureViewModel viewModel;
  final StudyCaptureDraft draft;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (var index = 0; index < draft.items.length; index += 1)
          _StudyItemEditor(
            key: Key('capture-study-item-$index'),
            index: index,
            item: draft.items[index],
            onChanged: (item) => viewModel.updateStudyItem(index, item),
          ),
      ],
    );
  }
}

class _StudyItemEditor extends StatelessWidget {
  const _StudyItemEditor({
    super.key,
    required this.index,
    required this.item,
    required this.onChanged,
  });

  final int index;
  final StudyCaptureItem item;
  final ValueChanged<StudyCaptureItem> onChanged;

  @override
  Widget build(BuildContext context) {
    StudyCaptureItem update({
      String? skill,
      StudyCaptureKind? kind,
      String? occurredOn,
      String? notes,
      int? durationMinutes,
      int? scoreRaw,
      int? scoreMax,
    }) {
      return StudyCaptureItem(
        skill: skill ?? item.skill,
        kind: kind ?? item.kind,
        occurredOn: occurredOn ?? item.occurredOn,
        notes: notes ?? item.notes,
        durationMinutes: durationMinutes,
        scoreRaw: scoreRaw,
        scoreMax: scoreMax,
        disciplineName: item.disciplineName,
      );
    }

    return CupertinoFormSection.insetGrouped(
      header: Text('ACTIVITY ${index + 1}'),
      children: [
        CupertinoTextFormFieldRow(
          key: Key('capture-study-skill-$index'),
          prefix: const Text('Skill'),
          initialValue: item.skill,
          onChanged: (value) => onChanged(
            update(
              skill: value,
              durationMinutes: item.durationMinutes,
              scoreRaw: item.scoreRaw,
              scoreMax: item.scoreMax,
            ),
          ),
        ),
        CupertinoFormRow(
          prefix: const Text('Kind'),
          child: CupertinoSlidingSegmentedControl<StudyCaptureKind>(
            groupValue: item.kind,
            children: const {
              StudyCaptureKind.practice: Text('Practice'),
              StudyCaptureKind.mock: Text('Mock'),
            },
            onValueChanged: (value) {
              if (value != null) {
                onChanged(
                  update(
                    kind: value,
                    durationMinutes: item.durationMinutes,
                    scoreRaw: item.scoreRaw,
                    scoreMax: item.scoreMax,
                  ),
                );
              }
            },
          ),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-study-date-$index'),
          prefix: const Text('Date'),
          initialValue: item.occurredOn,
          keyboardType: TextInputType.datetime,
          onChanged: (value) => onChanged(
            update(
              occurredOn: value,
              durationMinutes: item.durationMinutes,
              scoreRaw: item.scoreRaw,
              scoreMax: item.scoreMax,
            ),
          ),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-study-duration-$index'),
          prefix: const Text('Minutes'),
          initialValue: item.durationMinutes?.toString() ?? '',
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          onChanged: (value) => onChanged(
            update(
              durationMinutes: int.tryParse(value),
              scoreRaw: item.scoreRaw,
              scoreMax: item.scoreMax,
            ),
          ),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-study-score-raw-$index'),
          prefix: const Text('Score'),
          initialValue: item.scoreRaw?.toString() ?? '',
          placeholder: 'Raw',
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          onChanged: (value) => onChanged(
            update(
              durationMinutes: item.durationMinutes,
              scoreRaw: int.tryParse(value),
              scoreMax: item.scoreMax,
            ),
          ),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-study-score-max-$index'),
          prefix: const Text('Out of'),
          initialValue: item.scoreMax?.toString() ?? '',
          placeholder: 'Maximum',
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          onChanged: (value) => onChanged(
            update(
              durationMinutes: item.durationMinutes,
              scoreRaw: item.scoreRaw,
              scoreMax: int.tryParse(value),
            ),
          ),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-study-notes-$index'),
          prefix: const Text('Notes'),
          initialValue: item.notes,
          minLines: 2,
          maxLines: 5,
          onChanged: (value) => onChanged(
            update(
              notes: value,
              durationMinutes: item.durationMinutes,
              scoreRaw: item.scoreRaw,
              scoreMax: item.scoreMax,
            ),
          ),
        ),
      ],
    );
  }
}

class _VocabDraftEditor extends StatelessWidget {
  const _VocabDraftEditor({
    super.key,
    required this.viewModel,
    required this.draft,
  });

  final CaptureViewModel viewModel;
  final VocabCaptureDraft draft;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (var index = 0; index < draft.items.length; index += 1)
          _VocabItemEditor(
            key: Key('capture-vocab-item-$index'),
            index: index,
            item: draft.items[index],
            onChanged: (item) => viewModel.updateVocabItem(index, item),
          ),
      ],
    );
  }
}

class _VocabItemEditor extends StatelessWidget {
  const _VocabItemEditor({
    super.key,
    required this.index,
    required this.item,
    required this.onChanged,
  });

  final int index;
  final VocabCaptureItem item;
  final ValueChanged<VocabCaptureItem> onChanged;

  @override
  Widget build(BuildContext context) {
    return CupertinoFormSection.insetGrouped(
      header: Text('CARD ${index + 1}'),
      children: [
        CupertinoTextFormFieldRow(
          key: Key('capture-vocab-front-$index'),
          prefix: const Text('Word'),
          initialValue: item.front,
          onChanged: (value) => onChanged(item.copyWith(front: value)),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-vocab-back-$index'),
          prefix: const Text('Meaning'),
          initialValue: item.back,
          minLines: 2,
          maxLines: 4,
          onChanged: (value) => onChanged(item.copyWith(back: value)),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-vocab-reading-$index'),
          prefix: const Text('Reading'),
          initialValue: item.reading,
          onChanged: (value) => onChanged(item.copyWith(reading: value)),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-vocab-pos-$index'),
          prefix: const Text('Part of speech'),
          initialValue: item.partOfSpeech,
          onChanged: (value) => onChanged(item.copyWith(partOfSpeech: value)),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-vocab-example-$index'),
          prefix: const Text('Example'),
          initialValue: item.example,
          minLines: 2,
          maxLines: 4,
          onChanged: (value) => onChanged(item.copyWith(example: value)),
        ),
        CupertinoFormRow(
          prefix: const Text('Language'),
          child: CupertinoSlidingSegmentedControl<VocabCaptureLanguage>(
            groupValue: item.language,
            children: const {
              VocabCaptureLanguage.english: Text('English'),
              VocabCaptureLanguage.chinese: Text('Chinese'),
            },
            onValueChanged: (value) {
              if (value != null) {
                onChanged(item.copyWith(language: value));
              }
            },
          ),
        ),
        CupertinoTextFormFieldRow(
          key: Key('capture-vocab-deck-$index'),
          prefix: const Text('Deck'),
          initialValue: item.deck,
          placeholder: 'Default',
          onChanged: (value) => onChanged(item.copyWith(deck: value)),
        ),
      ],
    );
  }
}

class _CaptureSaved extends StatelessWidget {
  const _CaptureSaved({super.key, required this.viewModel});

  final CaptureViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    final saved = viewModel.saved;
    final undone = viewModel.phase == CapturePhase.undone;
    final undoing = viewModel.phase == CapturePhase.undoing;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(NorthstarSpacing.lg),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: Column(
            children: [
              Icon(
                undone
                    ? CupertinoIcons.arrow_counterclockwise_circle_fill
                    : CupertinoIcons.check_mark_circled_solid,
                key: const Key('capture-success-icon'),
                size: 52,
                color: undone
                    ? NorthstarColors.secondaryText.resolveFrom(context)
                    : NorthstarColors.positive.resolveFrom(context),
              ),
              const SizedBox(height: NorthstarSpacing.md),
              Text(
                undone ? 'Capture undone' : 'Saved to Northstar',
                style: NorthstarTextStyles.sectionTitle(context),
              ),
              const SizedBox(height: NorthstarSpacing.xs),
              Text(
                saved?.title ?? '',
                textAlign: TextAlign.center,
                style: NorthstarTextStyles.body(context),
              ),
              if (viewModel.errorMessage case final error?) ...[
                const SizedBox(height: NorthstarSpacing.md),
                _CaptureError(message: error, onRetry: viewModel.retry),
              ],
              const SizedBox(height: NorthstarSpacing.lg),
              if (!undone)
                CupertinoButton(
                  key: const Key('capture-undo-button'),
                  onPressed: undoing ? null : viewModel.undo,
                  child: undoing
                      ? const CupertinoActivityIndicator()
                      : const Text(
                          'Undo save',
                          style: TextStyle(color: CupertinoColors.systemRed),
                        ),
                ),
              SizedBox(
                width: double.infinity,
                child: CupertinoButton.filled(
                  key: const Key('capture-another-button'),
                  onPressed: undoing ? null : viewModel.captureAnother,
                  child: const Text('Capture another'),
                ),
              ),
              CupertinoButton(
                onPressed: undoing ? null : () => Navigator.of(context).pop(),
                child: const Text('Done'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _KindButton extends StatelessWidget {
  const _KindButton({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return CupertinoButton(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      color: selected
          ? NorthstarColors.accent.resolveFrom(context)
          : NorthstarColors.elevatedSurface.resolveFrom(context),
      onPressed: onPressed,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            size: 16,
            color: selected
                ? CupertinoColors.white
                : NorthstarColors.primaryText.resolveFrom(context),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              color: selected
                  ? CupertinoColors.white
                  : NorthstarColors.primaryText.resolveFrom(context),
            ),
          ),
        ],
      ),
    );
  }
}

class _CaptureError extends StatelessWidget {
  const _CaptureError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('capture-error'),
      width: double.infinity,
      padding: const EdgeInsets.all(NorthstarSpacing.md),
      decoration: BoxDecoration(
        color: CupertinoColors.systemRed
            .resolveFrom(context)
            .withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(NorthstarRadii.md),
      ),
      child: Row(
        children: [
          const Icon(CupertinoIcons.exclamationmark_circle_fill),
          const SizedBox(width: NorthstarSpacing.sm),
          Expanded(child: Text(message)),
          CupertinoButton(
            padding: EdgeInsets.zero,
            onPressed: onRetry,
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}

String _kindLabel(CaptureKind kind) {
  return switch (kind) {
    CaptureKind.note => 'Note',
    CaptureKind.task => 'Task',
    CaptureKind.event => 'Event',
    CaptureKind.expense => 'Expense',
    CaptureKind.study => 'Study',
    CaptureKind.vocab => 'Vocab',
  };
}

IconData _kindIcon(CaptureKind kind) {
  return switch (kind) {
    CaptureKind.note => CupertinoIcons.doc_text,
    CaptureKind.task => CupertinoIcons.check_mark_circled,
    CaptureKind.event => CupertinoIcons.calendar,
    CaptureKind.expense => CupertinoIcons.money_dollar_circle,
    CaptureKind.study => CupertinoIcons.book,
    CaptureKind.vocab => CupertinoIcons.tag,
  };
}

extension on String {
  String ifEmpty(String fallback) => isEmpty ? fallback : this;
}
