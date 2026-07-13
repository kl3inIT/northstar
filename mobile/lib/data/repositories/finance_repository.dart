import 'package:northstar/data/services/finance_api.dart';
import 'package:northstar/domain/models/finance_models.dart';

abstract interface class FinanceRepository {
  Future<FinanceGlance> glance({
    required String month,
    required String timezone,
  });
}

class RemoteFinanceRepository implements FinanceRepository {
  const RemoteFinanceRepository(this._api);

  final FinanceDataSource _api;

  @override
  Future<FinanceGlance> glance({
    required String month,
    required String timezone,
  }) async {
    late final FinanceMonthSummary summary;
    late final List<FinanceTransaction> transactions;
    await Future.wait<void>([
      _api.summary(month: month, timezone: timezone).then((value) {
        summary = value.toDomain();
      }),
      _api.transactions(month: month, timezone: timezone).then((value) {
        transactions = value
            .map((item) => item.toDomain())
            .toList(growable: false);
      }),
    ]);
    return FinanceGlance(
      summary: summary,
      transactions: List.unmodifiable(transactions),
    );
  }
}
