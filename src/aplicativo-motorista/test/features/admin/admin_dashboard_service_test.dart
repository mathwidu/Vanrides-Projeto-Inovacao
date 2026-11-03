import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_dashboard_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';

void main() {
  const session = AdminPortalSession(
    adminId: 1,
    adminName: 'Admin Portal',
    companyId: 1,
    companyName: 'Portal Empresa',
    companySlug: 'vanrides-default',
    email: 'admin@vanrides.dev',
    accessToken: 'token.admin',
  );

  test(
    'busca resumo operacional com servicos e presencas por servico',
    () async {
      final service = AdminDashboardPortalService(
        session: session,
        client: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/company-admins/dashboard/summary');
          expect(request.headers['Authorization'], 'Bearer token.admin');
          return http.Response(
            jsonEncode({
              'motoristasAtivos': 5,
              'rotasAtivas': 2,
              'passageirosComPresencaHoje': 7,
              'servicosRecorrentes': {
                'ativos': 6,
                'automaticos': 4,
                'semMotorista': 1,
              },
              'presencasServicoHoje': {
                'confirmadas': 8,
                'ausentes': 2,
                'canceladas': 1,
                'totalDecisoes': 11,
              },
              'viagensHoje': {
                'total': 3,
                'emAndamento': 1,
                'concluidas': 1,
                'planejadas': 1,
              },
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final summary = await service.fetchSummary();

      expect(summary.motoristasAtivos, 5);
      expect(summary.servicosRecorrentes.ativos, 6);
      expect(summary.servicosRecorrentes.automaticos, 4);
      expect(summary.servicosRecorrentes.semMotorista, 1);
      expect(summary.presencasServicoHoje.confirmadas, 8);
      expect(summary.presencasServicoHoje.ausentes, 2);
      expect(summary.presencasServicoHoje.canceladas, 1);
      expect(summary.presencasServicoHoje.totalDecisoes, 11);
      expect(summary.viagensHoje.total, 3);
    },
  );

  test(
    'mantem defaults quando backend antigo nao envia campos novos',
    () async {
      final service = AdminDashboardPortalService(
        session: session,
        client: MockClient((request) async {
          return http.Response(
            jsonEncode({
              'motoristasAtivos': 1,
              'rotasAtivas': 2,
              'passageirosComPresencaHoje': 3,
              'viagensHoje': {
                'total': 4,
                'emAndamento': 0,
                'concluidas': 1,
                'planejadas': 3,
              },
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final summary = await service.fetchSummary();

      expect(summary.servicosRecorrentes.ativos, 0);
      expect(summary.servicosRecorrentes.automaticos, 0);
      expect(summary.servicosRecorrentes.semMotorista, 0);
      expect(summary.presencasServicoHoje.totalDecisoes, 0);
      expect(summary.viagensHoje.planejadas, 3);
    },
  );
}
