import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:northstar/data/repositories/note_detail_repository.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/notes/view_models/note_detail_view_model.dart';

class NoteDetailView extends StatefulWidget {
  const NoteDetailView({
    super.key,
    required this.repository,
    required this.slug,
  });

  final NoteDetailRepository repository;
  final String slug;

  @override
  State<NoteDetailView> createState() => _NoteDetailViewState();
}

class _NoteDetailViewState extends State<NoteDetailView> {
  late final NoteDetailViewModel _viewModel;

  @override
  void initState() {
    super.initState();
    _viewModel = NoteDetailViewModel(
      repository: widget.repository,
      slug: widget.slug,
    );
    unawaited(_viewModel.load());
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('note-detail-page'),
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Note'),
        previousPageTitle: 'Assistant',
      ),
      child: SafeArea(
        bottom: false,
        child: AnimatedBuilder(
          animation: _viewModel,
          builder: (context, _) {
            if (_viewModel.phase == NoteDetailPhase.loading) {
              return const Center(
                child: CupertinoActivityIndicator(
                  key: Key('note-detail-loading'),
                  radius: 14,
                ),
              );
            }
            if (_viewModel.phase == NoteDetailPhase.error) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(NorthstarSpacing.xl),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(CupertinoIcons.doc_text_search, size: 40),
                      const SizedBox(height: NorthstarSpacing.md),
                      Text(
                        _viewModel.errorMessage ?? 'This note is unavailable.',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: NorthstarSpacing.lg),
                      CupertinoButton.filled(
                        key: const Key('note-detail-retry'),
                        onPressed: _viewModel.load,
                        child: const Text('Try again'),
                      ),
                    ],
                  ),
                ),
              );
            }
            final note = _viewModel.note!;
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
                    constraints: const BoxConstraints(
                      maxWidth: NorthstarLayout.readableContentWidth,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          note.title,
                          style: NorthstarTextStyles.hero(context),
                        ),
                        if (note.tags.isNotEmpty) ...[
                          const SizedBox(height: NorthstarSpacing.sm),
                          Wrap(
                            spacing: NorthstarSpacing.xs,
                            runSpacing: NorthstarSpacing.xs,
                            children: [
                              for (final tag in note.tags)
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 10,
                                    vertical: 5,
                                  ),
                                  decoration: BoxDecoration(
                                    color: NorthstarColors.accent
                                        .resolveFrom(context)
                                        .withValues(alpha: 0.12),
                                    borderRadius: BorderRadius.circular(
                                      NorthstarRadii.pill,
                                    ),
                                  ),
                                  child: Text(tag),
                                ),
                            ],
                          ),
                        ],
                        const SizedBox(height: NorthstarSpacing.lg),
                        MarkdownBody(
                          key: const Key('note-detail-markdown'),
                          data: note.contentMarkdown,
                          selectable: true,
                          styleSheetTheme:
                              MarkdownStyleSheetBaseTheme.cupertino,
                          styleSheet:
                              MarkdownStyleSheet.fromCupertinoTheme(
                                CupertinoTheme.of(context),
                              ).copyWith(
                                p: NorthstarTextStyles.body(context).copyWith(
                                  color: NorthstarColors.primaryText
                                      .resolveFrom(context),
                                ),
                              ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
