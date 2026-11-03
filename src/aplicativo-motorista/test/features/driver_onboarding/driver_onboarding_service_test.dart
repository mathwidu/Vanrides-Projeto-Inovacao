import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/data/driver_onboarding_service.dart';

const _driverSession = AppSessionUser(
  userId: 31,
  displayName: 'Joao Motorista',
  email: 'joao@vanrides.dev',
  password: 'Driver!123',
  role: AppUserRole.driver,
  backendRoleLabel: 'Motorista',
  accessToken: 'token.driver',
  companyLinkStatus: AppCompanyLinkStatus.noCompany,
);

void main() {
  test('previewInvite consome endpoint público de convite', () async {
    final service = DriverOnboardingService(
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(
          request.url.path,
          '/driver-onboarding/invites/TOKEN-123/preview',
        );
        return http.Response(
          jsonEncode({
            'companyId': 7,
            'companyName': 'Feevale Vans',
            'role': 'DRIVER',
            'status': 'ACTIVE',
          }),
          200,
        );
      }),
    );

    final preview = await service.previewInvite('TOKEN-123');

    expect(preview.companyId, 7);
    expect(preview.companyName, 'Feevale Vans');
    expect(preview.role, 'DRIVER');
  });

  test('redeemInvite retorna status vinculado', () async {
    final service = DriverOnboardingService(
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/driver-onboarding/invites/TOKEN-123/redeem');
        expect(request.headers['Authorization'], isNotEmpty);
        return http.Response(
          jsonEncode({
            'companyLinkStatus': 'COMPANY_LINKED',
            'companyId': 7,
            'companyName': 'Feevale Vans',
            'activeMembershipId': 99,
          }),
          200,
        );
      }),
    );

    final status = await service.redeemInvite(
      _driverSession,
      token: 'TOKEN-123',
    );

    expect(status.companyLinkStatus, AppCompanyLinkStatus.companyLinked);
    expect(status.companyId, 7);
    expect(status.activeMembershipId, 99);
  });

  test('searchCompanies envia query e parseia empresas', () async {
    final service = DriverOnboardingService(
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/driver-onboarding/companies');
        expect(request.url.queryParameters['query'], 'fee');
        return http.Response(
          jsonEncode([
            {'id': 7, 'nome': 'Feevale Vans', 'slug': 'feevale-vans'},
          ]),
          200,
        );
      }),
    );

    final companies = await service.searchCompanies(
      _driverSession,
      query: 'fee',
    );

    expect(companies, hasLength(1));
    expect(companies.single.nome, 'Feevale Vans');
  });

  test('createRequest retorna pending approval', () async {
    final service = DriverOnboardingService(
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/driver-onboarding/requests');
        expect(jsonDecode(request.body) as Map<String, dynamic>, {
          'companyId': 7,
        });
        return http.Response(
          jsonEncode({
            'companyLinkStatus': 'PENDING_APPROVAL',
            'pendingRequest': {
              'id': 55,
              'companyId': 7,
              'companyName': 'Feevale Vans',
              'status': 'PENDING_APPROVAL',
            },
          }),
          201,
        );
      }),
    );

    final status = await service.createRequest(_driverSession, companyId: 7);

    expect(status.companyLinkStatus, AppCompanyLinkStatus.pendingApproval);
    expect(status.pendingRequest?.companyName, 'Feevale Vans');
  });
}
