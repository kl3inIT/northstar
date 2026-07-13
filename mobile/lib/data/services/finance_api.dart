import 'package:northstar/data/models/finance_dtos.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';

abstract interface class FinanceDataSource {
  Future<FinanceMonthSummaryDto> summary({
    required String month,
    required String timezone,
  });

  Future<List<FinanceTransactionDto>> transactions({
    required String month,
    required String timezone,
  });
}

class FinanceApi implements FinanceDataSource {
  const FinanceApi(this._client);

  final AuthenticatedJsonClient _client;

  @override
  Future<FinanceMonthSummaryDto> summary({
    required String month,
    required String timezone,
  }) async {
    return FinanceMonthSummaryDto(
      await _client.getObject(
        '/api/finance/summary',
        query: {'month': month},
        timezone: timezone,
      ),
    );
  }

  @override
  Future<List<FinanceTransactionDto>> transactions({
    required String month,
    required String timezone,
  }) async {
    final json = await _client.getList(
      '/api/finance',
      query: {'month': month},
      timezone: timezone,
    );
    return json.map(FinanceTransactionDto.new).toList(growable: false);
  }
}
