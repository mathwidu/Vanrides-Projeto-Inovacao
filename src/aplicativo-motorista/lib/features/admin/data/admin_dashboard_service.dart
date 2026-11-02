import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_dashboard_summary_record.dart';
import '../domain/admin_portal_session.dart';
import 'admin_portal_http_support.dart';

abstract class AdminDashboardGateway {
  Future<AdminDashboardSummaryRecord> fetchSummary();
}

class AdminDashboardPortalService implements AdminDashboardGateway {
  AdminDashboardPortalService({
    required AdminPortalSession session,
    http.Client? client,
  })  : _session = session,
        _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

  @override
  Future<AdminDashboardSummaryRecord> fetchSummary() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/dashboard/summary'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar o resumo do dashboard.',
      );
    }

    return AdminDashboardSummaryRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }
}
