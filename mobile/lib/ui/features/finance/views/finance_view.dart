import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:intl/intl.dart';
import 'package:northstar/domain/models/finance_models.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/finance/view_models/finance_view_model.dart';

class FinanceView extends StatefulWidget {
  const FinanceView({super.key, required this.viewModel});

  final FinanceViewModel viewModel;

  @override
  State<FinanceView> createState() => _FinanceViewState();
}

class _FinanceViewState extends State<FinanceView> {
  FinanceViewModel get _viewModel => widget.viewModel;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.initialize());
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('finance-page'),
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Finance'),
        trailing: CupertinoButton(
          key: const Key('finance-refresh-button'),
          padding: EdgeInsets.zero,
          onPressed: _viewModel.phase == FinancePhase.loading
              ? null
              : _viewModel.load,
          child: const Icon(CupertinoIcons.refresh, semanticLabel: 'Refresh'),
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
    final glance = _viewModel.glance;
    if (_viewModel.phase == FinancePhase.loading && glance == null) {
      return const Center(
        child: CupertinoActivityIndicator(
          key: Key('finance-loading-indicator'),
          radius: 14,
        ),
      );
    }
    if (_viewModel.phase == FinancePhase.error && glance == null) {
      return _FinanceMessage(
        key: const Key('finance-load-error'),
        icon: CupertinoIcons.wifi_exclamationmark,
        title: 'Couldn’t load Finance',
        message: _viewModel.errorMessage ?? 'Finance is unavailable.',
        onRetry: _viewModel.load,
      );
    }
    if (glance == null) return const SizedBox.shrink();
    return _FinanceContent(glance: glance, onRefresh: _viewModel.load);
  }
}

class _FinanceContent extends StatelessWidget {
  _FinanceContent({required this.glance, required this.onRefresh});

  final FinanceGlance glance;
  final Future<void> Function() onRefresh;
  final NumberFormat _money = NumberFormat.currency(
    locale: 'vi_VN',
    symbol: '₫',
    decimalDigits: 0,
  );

  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      key: const Key('finance-scroll-view'),
      slivers: [
        CupertinoSliverRefreshControl(onRefresh: onRefresh),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(
            NorthstarSpacing.md,
            NorthstarSpacing.lg,
            NorthstarSpacing.md,
            NorthstarSpacing.xxl,
          ),
          sliver: SliverToBoxAdapter(
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1040),
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final summary = _SummaryColumn(
                      summary: glance.summary,
                      money: _money,
                    );
                    final activity = _ActivityColumn(
                      transactions: glance.transactions,
                      money: _money,
                    );
                    if (constraints.maxWidth < 840) {
                      return Column(
                        key: const Key('finance-compact-layout'),
                        children: [summary, activity],
                      );
                    }
                    return Row(
                      key: const Key('finance-expanded-layout'),
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(child: summary),
                        const SizedBox(width: NorthstarSpacing.lg),
                        Expanded(child: activity),
                      ],
                    );
                  },
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _SummaryColumn extends StatelessWidget {
  const _SummaryColumn({required this.summary, required this.money});

  final FinanceMonthSummary summary;
  final NumberFormat money;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(summary.month, style: NorthstarTextStyles.sectionTitle(context)),
        const SizedBox(height: NorthstarSpacing.md),
        Wrap(
          spacing: NorthstarSpacing.sm,
          runSpacing: NorthstarSpacing.sm,
          children: [
            _MetricCard(
              label: 'Income',
              value: money.format(summary.incomeTotal),
              color: NorthstarColors.positive,
            ),
            _MetricCard(
              label: 'Spending',
              value: money.format(summary.expenseTotal),
              color: NorthstarColors.destructive,
            ),
            _MetricCard(
              label: 'Net',
              value: money.format(summary.net),
              color: summary.net >= 0
                  ? NorthstarColors.positive
                  : NorthstarColors.destructive,
            ),
          ],
        ),
        if (summary.exceptionalCount > 0) ...[
          const SizedBox(height: NorthstarSpacing.md),
          Container(
            key: const Key('finance-one-off-summary'),
            padding: const EdgeInsets.all(NorthstarSpacing.md),
            decoration: BoxDecoration(
              color: CupertinoColors.systemOrange.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(NorthstarRadii.md),
            ),
            child: Text(
              '${summary.exceptionalCount} one-off expenses · '
              '${money.format(summary.exceptionalTotal)}',
            ),
          ),
        ],
        CupertinoListSection.insetGrouped(
          header: const Text('SPENDING BY CATEGORY'),
          children: summary.categories.isEmpty
              ? const [
                  CupertinoListTile(
                    key: Key('finance-categories-empty'),
                    title: Text('No spending yet this month'),
                  ),
                ]
              : [
                  for (final category in summary.categories.take(5))
                    CupertinoListTile(
                      title: Text(category.name),
                      subtitle: category.hasExceptional
                          ? const Text('Includes a one-off expense')
                          : null,
                      additionalInfo: Text(money.format(category.total)),
                    ),
                ],
        ),
      ],
    );
  }
}

class _ActivityColumn extends StatelessWidget {
  const _ActivityColumn({required this.transactions, required this.money});

  final List<FinanceTransaction> transactions;
  final NumberFormat money;

  @override
  Widget build(BuildContext context) {
    return CupertinoListSection.insetGrouped(
      header: const Text('RECENT ACTIVITY'),
      footer: const Text(
        'Add transactions through Assistant or Capture, then review before saving.',
      ),
      children: transactions.isEmpty
          ? const [
              CupertinoListTile(
                key: Key('finance-transactions-empty'),
                leading: Icon(CupertinoIcons.money_dollar_circle),
                title: Text('No transactions this month'),
              ),
            ]
          : [
              for (final transaction in transactions.take(7))
                CupertinoListTile(
                  key: Key('finance-transaction-${transaction.id}'),
                  leading: Icon(
                    transaction.type == FinanceTransactionType.income
                        ? CupertinoIcons.arrow_down_left
                        : CupertinoIcons.arrow_up_right,
                    color: transaction.type == FinanceTransactionType.income
                        ? NorthstarColors.positive.resolveFrom(context)
                        : NorthstarColors.destructive.resolveFrom(context),
                  ),
                  title: Text(transaction.description),
                  subtitle: Text(
                    '${transaction.occurredOn} · ${transaction.category}',
                  ),
                  additionalInfo: Text(
                    '${transaction.type == FinanceTransactionType.income ? '+' : '−'}'
                    '${money.format(transaction.amount)}',
                  ),
                ),
            ],
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({
    required this.label,
    required this.value,
    required this.color,
  });

  final String label;
  final String value;
  final CupertinoDynamicColor color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 180,
      padding: const EdgeInsets.all(NorthstarSpacing.md),
      decoration: BoxDecoration(
        color: NorthstarColors.elevatedSurface.resolveFrom(context),
        borderRadius: BorderRadius.circular(NorthstarRadii.md),
        border: Border.all(
          color: NorthstarColors.separator.resolveFrom(context),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: NorthstarTextStyles.caption(context)),
          const SizedBox(height: NorthstarSpacing.xxs),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: NorthstarTextStyles.sectionTitle(
              context,
            ).copyWith(color: color.resolveFrom(context)),
          ),
        ],
      ),
    );
  }
}

class _FinanceMessage extends StatelessWidget {
  const _FinanceMessage({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    required this.onRetry,
  });

  final IconData icon;
  final String title;
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
            Icon(icon, size: 40),
            const SizedBox(height: NorthstarSpacing.md),
            Text(title, style: NorthstarTextStyles.sectionTitle(context)),
            const SizedBox(height: NorthstarSpacing.xs),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: NorthstarSpacing.lg),
            CupertinoButton.filled(
              key: const Key('finance-load-retry'),
              onPressed: onRetry,
              child: const Text('Try again'),
            ),
          ],
        ),
      ),
    );
  }
}
