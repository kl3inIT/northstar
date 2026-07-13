import 'package:northstar/domain/models/finance_models.dart';

class FinanceMonthSummaryDto {
  const FinanceMonthSummaryDto(this.json);

  final Map<String, Object?> json;

  FinanceMonthSummary toDomain() {
    final categories = _requiredList(json, 'categories')
        .map(_map)
        .map(
          (item) => FinanceCategoryTotal(
            name: _requiredString(item, 'name'),
            total: _requiredInt(item, 'total'),
            hasExceptional: _requiredBool(item, 'hasExceptional'),
          ),
        )
        .toList(growable: false);
    return FinanceMonthSummary(
      month: _requiredString(json, 'month'),
      expenseTotal: _requiredInt(json, 'expenseTotal'),
      incomeTotal: _requiredInt(json, 'incomeTotal'),
      net: _requiredInt(json, 'net'),
      exceptionalTotal: _requiredInt(json, 'exceptionalTotal'),
      exceptionalCount: _requiredInt(json, 'exceptionalCount'),
      previousMonthExpenseTotal: _requiredInt(
        json,
        'previousMonthExpenseTotal',
      ),
      categories: categories,
    );
  }
}

class FinanceTransactionDto {
  const FinanceTransactionDto(this.json);

  final Map<String, Object?> json;

  FinanceTransaction toDomain() {
    final rawType = _requiredString(json, 'type').toUpperCase();
    return FinanceTransaction(
      id: _requiredString(json, 'id'),
      type: switch (rawType) {
        'EXPENSE' => FinanceTransactionType.expense,
        'INCOME' => FinanceTransactionType.income,
        _ => throw FormatException('Unsupported transaction type: $rawType'),
      },
      amount: _requiredInt(json, 'amount'),
      occurredOn: _requiredString(json, 'occurredOn'),
      description: _requiredString(json, 'description'),
      category: _requiredString(json, 'category'),
      exceptional: _requiredBool(json, 'exceptional'),
      source: _requiredString(json, 'source'),
    );
  }
}

Map<String, Object?> _map(Object? value) {
  if (value is Map<String, Object?>) return value;
  if (value is Map) {
    return value.map((key, value) => MapEntry(key.toString(), value));
  }
  throw const FormatException('Expected a JSON object.');
}

List<Object?> _requiredList(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is List) return value;
  throw FormatException('Expected list field "$key".');
}

String _requiredString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is String && value.isNotEmpty) return value;
  throw FormatException('Expected string field "$key".');
}

int _requiredInt(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is int) return value;
  if (value is num) return value.toInt();
  throw FormatException('Expected integer field "$key".');
}

bool _requiredBool(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is bool) return value;
  throw FormatException('Expected boolean field "$key".');
}
