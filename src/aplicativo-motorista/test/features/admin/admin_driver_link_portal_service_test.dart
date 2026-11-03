import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_driver_link_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_exception.dart';
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

  test('lista convites de motorista sem expor token bruto', () async {
    final service = AdminDriverLinkPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/company-admins/driver-invites');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode([
            {
              'id': 501,
              'companyId': 1,
              'companyName': 'Portal Empresa',
              'token': null,
              'shareUrl': null,
              'status': 'ACTIVE',
              'expiresAt': '2026-07-01T12:00:00Z',
              'createdAt': '2026-06-16T20:30:00Z',
              'acceptedAt': null,
              'acceptedByMotoristaId': null,
              'acceptedByMotoristaName': null,
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchDriverInvites();

    expect(result, hasLength(1));
    expect(result.first.id, 501);
    expect(result.first.companyName, 'Portal Empresa');
    expect(result.first.token, isNull);
    expect(result.first.shareUrl, isNull);
    expect(result.first.status, 'ACTIVE');
    expect(result.first.expiresAt, DateTime.parse('2026-07-01T12:00:00Z'));
  });

  test('gera convite de motorista com validade opcional', () async {
    final service = AdminDriverLinkPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/driver-invites');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['expiresAt'], '2026-07-01T12:00:00.000Z');
        return http.Response(
          jsonEncode({
            'id': 502,
            'companyId': 1,
            'companyName': 'Portal Empresa',
            'token': 'driver-token-raw',
            'shareUrl': 'vanrides://driver-invite/driver-token-raw',
            'status': 'ACTIVE',
            'expiresAt': '2026-07-01T12:00:00Z',
            'createdAt': '2026-06-16T20:31:00Z',
            'acceptedAt': null,
            'acceptedByMotoristaId': null,
            'acceptedByMotoristaName': null,
          }),
          201,
        );
      }),
    );

    final result = await service.createDriverInvite(
      expiresAt: DateTime.parse('2026-07-01T09:00:00-03:00'),
    );

    expect(result.id, 502);
    expect(result.token, 'driver-token-raw');
    expect(result.shareUrl, 'vanrides://driver-invite/driver-token-raw');
  });

  test('cancela convite de motorista', () async {
    final service = AdminDriverLinkPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/driver-invites/502/cancel');
        return http.Response(
          jsonEncode({
            'id': 502,
            'companyId': 1,
            'companyName': 'Portal Empresa',
            'token': null,
            'shareUrl': null,
            'status': 'CANCELLED',
            'expiresAt': '2026-07-01T12:00:00Z',
            'createdAt': '2026-06-16T20:31:00Z',
            'acceptedAt': null,
            'acceptedByMotoristaId': null,
            'acceptedByMotoristaName': null,
          }),
          200,
        );
      }),
    );

    final result = await service.cancelDriverInvite(502);

    expect(result.status, 'CANCELLED');
    expect(result.token, isNull);
  });

  test('lista solicitacoes de vinculo de motorista', () async {
    final service = AdminDriverLinkPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/company-admins/driver-link-requests');
        return http.Response(
          jsonEncode([
            {
              'id': 701,
              'motoristaId': 44,
              'motoristaNome': 'Motorista App',
              'motoristaEmail': 'driver@vanrides.dev',
              'motoristaTelefone': '51999990044',
              'companyId': 1,
              'companyName': 'Portal Empresa',
              'status': 'PENDING',
              'requestedAt': '2026-06-16T21:10:00Z',
              'reviewedAt': null,
              'emailVerified': true,
              'reviewNotes': null,
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchDriverLinkRequests();

    expect(result, hasLength(1));
    expect(result.first.motoristaId, 44);
    expect(result.first.motoristaNome, 'Motorista App');
    expect(result.first.emailVerified, isTrue);
    expect(result.first.status, 'PENDING');
  });

  test('aprova solicitacao de motorista com observacao', () async {
    final service = AdminDriverLinkPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/company-admins/driver-link-requests/701/approve',
        );
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['reason'], 'Documentacao conferida');
        return http.Response(
          jsonEncode({
            'id': 701,
            'motoristaId': 44,
            'motoristaNome': 'Motorista App',
            'motoristaEmail': 'driver@vanrides.dev',
            'motoristaTelefone': '51999990044',
            'companyId': 1,
            'companyName': 'Portal Empresa',
            'status': 'APPROVED',
            'requestedAt': '2026-06-16T21:10:00Z',
            'reviewedAt': '2026-06-16T21:15:00Z',
            'emailVerified': true,
            'reviewNotes': 'Documentacao conferida',
          }),
          200,
        );
      }),
    );

    final result = await service.approveDriverLinkRequest(
      requestId: 701,
      reason: ' Documentacao conferida ',
    );

    expect(result.status, 'APPROVED');
    expect(result.reviewNotes, 'Documentacao conferida');
    expect(result.reviewedAt, DateTime.parse('2026-06-16T21:15:00Z'));
  });

  test('rejeita solicitacao de motorista com observacao vazia', () async {
    final service = AdminDriverLinkPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/company-admins/driver-link-requests/701/reject',
        );
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['reason'], isNull);
        return http.Response(
          jsonEncode({
            'id': 701,
            'motoristaId': 44,
            'motoristaNome': 'Motorista App',
            'motoristaEmail': 'driver@vanrides.dev',
            'motoristaTelefone': '51999990044',
            'companyId': 1,
            'companyName': 'Portal Empresa',
            'status': 'REJECTED',
            'requestedAt': '2026-06-16T21:10:00Z',
            'reviewedAt': '2026-06-16T21:16:00Z',
            'emailVerified': true,
            'reviewNotes': null,
          }),
          200,
        );
      }),
    );

    final result = await service.rejectDriverLinkRequest(
      requestId: 701,
      reason: ' ',
    );

    expect(result.status, 'REJECTED');
    expect(result.reviewNotes, isNull);
  });

  test('propaga erro do backend ao listar convites de motorista', () async {
    final service = AdminDriverLinkPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/driver-invites');
        return http.Response(
          jsonEncode({'message': 'Empresa nao encontrada.'}),
          404,
        );
      }),
    );

    expect(
      service.fetchDriverInvites,
      throwsA(
        isA<AdminPortalException>().having(
          (error) => error.message,
          'message',
          'Empresa nao encontrada.',
        ),
      ),
    );
  });
}
