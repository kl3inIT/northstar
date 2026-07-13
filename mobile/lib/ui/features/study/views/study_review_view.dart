import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:northstar/domain/models/study_review_models.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/study/view_models/study_review_view_model.dart';

class StudyReviewView extends StatefulWidget {
  const StudyReviewView({super.key, required this.viewModel});

  final StudyReviewViewModel viewModel;

  @override
  State<StudyReviewView> createState() => _StudyReviewViewState();
}

class _StudyReviewViewState extends State<StudyReviewView> {
  StudyReviewViewModel get _viewModel => widget.viewModel;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.initialize());
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('study-page'),
      navigationBar: const CupertinoNavigationBar(middle: Text('Study')),
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
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            NorthstarSpacing.md,
            NorthstarSpacing.md,
            NorthstarSpacing.md,
            NorthstarSpacing.xs,
          ),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: CupertinoSlidingSegmentedControl<VocabLanguage>(
              groupValue: _viewModel.language,
              children: const {
                VocabLanguage.english: Padding(
                  padding: EdgeInsets.symmetric(horizontal: 18),
                  child: Text('English'),
                ),
                VocabLanguage.chinese: Padding(
                  padding: EdgeInsets.symmetric(horizontal: 18),
                  child: Text('Chinese'),
                ),
              },
              onValueChanged: (value) {
                if (value != null && !_viewModel.submitting) {
                  unawaited(_viewModel.setLanguage(value));
                }
              },
            ),
          ),
        ),
        Expanded(child: _reviewState(context)),
      ],
    );
  }

  Widget _reviewState(BuildContext context) {
    if (_viewModel.phase == StudyReviewPhase.loading) {
      return const Center(
        child: CupertinoActivityIndicator(
          key: Key('study-loading-indicator'),
          radius: 14,
        ),
      );
    }
    if (_viewModel.phase == StudyReviewPhase.error) {
      return _StudyMessage(
        key: const Key('study-load-error'),
        icon: CupertinoIcons.wifi_exclamationmark,
        title: 'Couldn’t load reviews',
        message: _viewModel.errorMessage ?? 'Study review is unavailable.',
        actionLabel: 'Try again',
        onAction: _viewModel.load,
      );
    }
    if (_viewModel.cards.isEmpty) {
      return _StudyMessage(
        key: const Key('study-empty'),
        icon: CupertinoIcons.check_mark_circled,
        title: 'You’re caught up',
        message: 'No ${_viewModel.language.name} cards are due right now.',
        actionLabel: 'Refresh',
        onAction: _viewModel.load,
      );
    }
    if (_viewModel.isComplete) {
      final reviewed = _viewModel.cards.length;
      return _StudyMessage(
        key: const Key('study-complete'),
        icon: CupertinoIcons.sparkles,
        title: 'Review complete',
        message:
            '$reviewed cards reviewed. The next intervals were saved by '
            'Northstar’s scheduler.',
        actionLabel: 'Review more',
        onAction: _viewModel.load,
      );
    }
    return _ReviewCard(viewModel: _viewModel);
  }
}

class _ReviewCard extends StatelessWidget {
  const _ReviewCard({required this.viewModel});

  final StudyReviewViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    final card = viewModel.currentCard!;
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 840;
        final cardSurface = _CardSurface(
          card: card,
          revealed: viewModel.revealed,
        );
        final controls = _ReviewControls(viewModel: viewModel, card: card);
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            NorthstarSpacing.md,
            NorthstarSpacing.sm,
            NorthstarSpacing.md,
            NorthstarSpacing.xxl,
          ),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1040),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${viewModel.index + 1} of ${viewModel.cards.length}',
                          style: NorthstarTextStyles.caption(context),
                        ),
                      ),
                      Text(
                        card.isProduction ? 'PRODUCTION' : 'RECOGNITION',
                        style: NorthstarTextStyles.caption(context).copyWith(
                          color: NorthstarColors.accent.resolveFrom(context),
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: NorthstarSpacing.xs),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(NorthstarRadii.pill),
                    child: LayoutBuilder(
                      builder: (context, constraints) => Container(
                        height: 5,
                        alignment: Alignment.centerLeft,
                        color: NorthstarColors.separator.resolveFrom(context),
                        child: SizedBox(
                          width:
                              constraints.maxWidth *
                              (viewModel.index / viewModel.cards.length),
                          child: ColoredBox(
                            color: NorthstarColors.accent.resolveFrom(context),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: NorthstarSpacing.lg),
                  if (wide)
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(child: cardSurface),
                        const SizedBox(width: NorthstarSpacing.lg),
                        Expanded(child: controls),
                      ],
                    )
                  else ...[
                    cardSurface,
                    const SizedBox(height: NorthstarSpacing.lg),
                    controls,
                  ],
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _CardSurface extends StatelessWidget {
  const _CardSurface({required this.card, required this.revealed});

  final VocabReviewCard card;
  final bool revealed;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('study-review-card'),
      constraints: const BoxConstraints(minHeight: 300),
      padding: const EdgeInsets.all(NorthstarSpacing.xl),
      decoration: BoxDecoration(
        color: NorthstarColors.elevatedSurface.resolveFrom(context),
        borderRadius: BorderRadius.circular(NorthstarRadii.lg),
        border: Border.all(
          color: NorthstarColors.separator.resolveFrom(context),
        ),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            card.isProduction ? 'Recall the word' : 'Recall the meaning',
            textAlign: TextAlign.center,
            style: NorthstarTextStyles.caption(context),
          ),
          const SizedBox(height: NorthstarSpacing.md),
          Text(
            card.prompt,
            key: const Key('study-card-prompt'),
            textAlign: TextAlign.center,
            style: NorthstarTextStyles.hero(context),
          ),
          if (!card.isProduction || revealed)
            if (card.metadata.reading case final reading?) ...[
              const SizedBox(height: NorthstarSpacing.xs),
              Text(
                reading,
                textAlign: TextAlign.center,
                style: NorthstarTextStyles.body(context).copyWith(
                  color: NorthstarColors.secondaryText.resolveFrom(context),
                ),
              ),
            ],
          if (revealed) ...[
            const SizedBox(height: NorthstarSpacing.xl),
            Container(
              height: 0.5,
              color: NorthstarColors.separator.resolveFrom(context),
            ),
            const SizedBox(height: NorthstarSpacing.xl),
            Text(
              card.answer,
              key: const Key('study-card-answer'),
              textAlign: TextAlign.center,
              style: NorthstarTextStyles.sectionTitle(context),
            ),
            if (card.metadata.example case final example?) ...[
              const SizedBox(height: NorthstarSpacing.md),
              Text(
                example,
                textAlign: TextAlign.center,
                style: NorthstarTextStyles.body(context),
              ),
            ],
          ],
        ],
      ),
    );
  }
}

class _ReviewControls extends StatelessWidget {
  const _ReviewControls({required this.viewModel, required this.card});

  final StudyReviewViewModel viewModel;
  final VocabReviewCard card;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (viewModel.errorMessage case final error?) ...[
          Container(
            key: const Key('study-rating-error'),
            padding: const EdgeInsets.all(NorthstarSpacing.md),
            decoration: BoxDecoration(
              color: CupertinoColors.systemRed.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(NorthstarRadii.md),
            ),
            child: Text(error),
          ),
          const SizedBox(height: NorthstarSpacing.md),
        ],
        if (!viewModel.revealed)
          CupertinoButton.filled(
            key: const Key('study-reveal-button'),
            onPressed: viewModel.submitting ? null : viewModel.reveal,
            child: const Text('Show answer'),
          )
        else ...[
          Text(
            'How well did you remember?',
            style: NorthstarTextStyles.sectionTitle(context),
          ),
          const SizedBox(height: NorthstarSpacing.xs),
          Text(
            'Only your rating changes the schedule.',
            style: NorthstarTextStyles.caption(context),
          ),
          const SizedBox(height: NorthstarSpacing.md),
          Wrap(
            spacing: NorthstarSpacing.xs,
            runSpacing: NorthstarSpacing.xs,
            children: [
              for (final rating in VocabRating.values)
                _RatingButton(
                  rating: rating,
                  preview: card.previewFor(rating),
                  submitting: viewModel.submitting,
                  onPressed: () => viewModel.rate(rating),
                ),
            ],
          ),
          if (viewModel.submitting) ...[
            const SizedBox(height: NorthstarSpacing.md),
            const Center(
              child: CupertinoActivityIndicator(
                key: Key('study-rating-progress'),
              ),
            ),
          ],
        ],
      ],
    );
  }
}

class _RatingButton extends StatelessWidget {
  const _RatingButton({
    required this.rating,
    required this.preview,
    required this.submitting,
    required this.onPressed,
  });

  final VocabRating rating;
  final VocabRatingPreview? preview;
  final bool submitting;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final label = switch (rating) {
      VocabRating.again => 'Again',
      VocabRating.hard => 'Hard',
      VocabRating.good => 'Good',
      VocabRating.easy => 'Easy',
    };
    return SizedBox(
      width: 112,
      child: CupertinoButton.tinted(
        key: Key('study-rating-${rating.name}'),
        padding: const EdgeInsets.symmetric(vertical: NorthstarSpacing.sm),
        onPressed: submitting ? null : onPressed,
        child: Column(
          children: [
            Text(label),
            const SizedBox(height: 2),
            Text(
              preview?.intervalLabel ?? '—',
              style: NorthstarTextStyles.caption(context),
            ),
          ],
        ),
      ),
    );
  }
}

class _StudyMessage extends StatelessWidget {
  const _StudyMessage({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    required this.actionLabel,
    required this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String actionLabel;
  final Future<void> Function() onAction;

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
              onPressed: onAction,
              child: Text(actionLabel),
            ),
          ],
        ),
      ),
    );
  }
}
