enum FinanceTransactionType { expense, income }

class FinanceCategoryTotal {
  const FinanceCategoryTotal({
    required this.name,
    required this.total,
    required this.hasExceptional,
  });

  final String name;
  final int total;
  final bool hasExceptional;
}

class FinanceMonthSummary {
  const FinanceMonthSummary({
    required this.month,
    required this.expenseTotal,
    required this.incomeTotal,
    required this.net,
    required this.exceptionalTotal,
    required this.exceptionalCount,
    required this.previousMonthExpenseTotal,
    required this.categories,
  });

  final String month;
  final int expenseTotal;
  final int incomeTotal;
  final int net;
  final int exceptionalTotal;
  final int exceptionalCount;
  final int previousMonthExpenseTotal;
  final List<FinanceCategoryTotal> categories;
}

class FinanceTransaction {
  const FinanceTransaction({
    required this.id,
    required this.type,
    required this.amount,
    required this.occurredOn,
    required this.description,
    required this.category,
    required this.exceptional,
    required this.source,
  });

  final String id;
  final FinanceTransactionType type;
  final int amount;
  final String occurredOn;
  final String description;
  final String category;
  final bool exceptional;
  final String source;
}

class FinanceGlance {
  const FinanceGlance({required this.summary, required this.transactions});

  final FinanceMonthSummary summary;
  final List<FinanceTransaction> transactions;
}
