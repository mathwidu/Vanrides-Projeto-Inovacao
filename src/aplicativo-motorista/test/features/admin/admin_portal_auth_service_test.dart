import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_portal_auth_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_admin_bootstrap_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_exception.dart';

void main() {
  test('faz login real de admin e extrai companyId do token', () async {
    final service = AdminPortalAuthService(
      client: MockClient((request) async {
        expect(request.url.path, '/login');
        return http.Response(
          jsonEncode({
            'mensagem': 'Bem-vindo, Admin Portal!',
            'tipoUsuario': 'Admin',
            'id': 3,
            'accessToken': _jwtWithCompanyId(9),
            'refreshToken': 'refresh',
          }),
          200,
        );
      }),
    );

    final session = await service.login(
      email: 'admin@vanrides.dev',
      senha: 'Portal!123',
    );

    expect(session.adminId, 3);
    expect(session.companyId, 9);
    expect(session.companyName, isEmpty);
    expect(session.adminName, 'Portal');
  });

  test('bootstrapa o primeiro admin da empresa', () async {
    final service = AdminPortalAuthService(
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/bootstrap');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body, {
          'companyName': 'VanRides Default',
          'nome': 'Admin Portal',
          'email': 'admin@vanrides.dev',
          'telefone': '51999999999',
          'senha': 'Portal!123',
        });
        expect(body.containsKey('companySlug'), isFalse);
        return http.Response(
          jsonEncode({
            'id': 1,
            'companyId': 9,
            'companySlug': 'vanrides-default',
            'nome': 'Admin Portal',
            'email': 'admin@vanrides.dev',
            'telefone': '51999999999',
          }),
          201,
        );
      }),
    );

    final result = await service.bootstrap(
      const AdminCompanyAdminBootstrapInput(
        companyName: 'VanRides Default',
        nome: 'Admin Portal',
        email: 'admin@vanrides.dev',
        telefone: '51999999999',
        senha: 'Portal!123',
      ),
    );

    expect(result.companySlug, 'vanrides-default');
  });

  test('rejeita resposta de bootstrap sem companySlug', () async {
    final service = AdminPortalAuthService(
      client: MockClient((request) async {
        return http.Response(
          jsonEncode({
            'id': 1,
            'companyId': 9,
            'nome': 'Admin Portal',
            'email': 'admin@vanrides.dev',
            'telefone': '51999999999',
          }),
          201,
        );
      }),
    );

    expect(
      () => service.bootstrap(
        const AdminCompanyAdminBootstrapInput(
          companyName: 'VanRides Default',
          nome: 'Admin Portal',
          email: 'admin@vanrides.dev',
          telefone: '51999999999',
          senha: 'Portal!123',
        ),
      ),
      throwsA(isA<FormatException>()),
    );
  });

  test('preserva acentos nas mensagens de erro do login admin', () async {
    final service = AdminPortalAuthService(
      client: MockClient((request) async {
        final bytes = utf8.encode(
          jsonEncode({
            'message': 'O e-mail informado ainda não foi verificado.',
          }),
        );
        return http.Response.bytes(
          bytes,
          403,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    expect(
      () => service.login(email: 'admin@vanrides.dev', senha: 'Portal!123'),
      throwsA(
        isA<AdminPortalException>().having(
          (error) => error.message,
          'message',
          contains('ainda não foi verificado'),
        ),
      ),
    );
  });

  test(
    'preserva code e email quando login bloqueia por e-mail pendente',
    () async {
      final service = AdminPortalAuthService(
        client: MockClient((request) async {
          return http.Response.bytes(
            utf8.encode(
              jsonEncode({
                'code': 'EMAIL_NOT_VERIFIED',
                'message': 'O e-mail informado ainda não foi verificado.',
                'email': 'admin@vanrides.dev',
              }),
            ),
            403,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      expect(
        () => service.login(email: 'admin@vanrides.dev', senha: 'Portal!123'),
        throwsA(
          isA<AdminPortalException>()
              .having((error) => error.code, 'code', 'EMAIL_NOT_VERIFIED')
              .having((error) => error.email, 'email', 'admin@vanrides.dev'),
        ),
      );
    },
  );

  test('preserva acentos nas mensagens de erro do bootstrap', () async {
    final service = AdminPortalAuthService(
      client: MockClient((request) async {
        final bytes = utf8.encode(
          jsonEncode({
            'message': 'O email informado já está em uso no sistema.',
          }),
        );
        return http.Response.bytes(
          bytes,
          409,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    expect(
      () => service.bootstrap(
        const AdminCompanyAdminBootstrapInput(
          companyName: 'VanRides Default',
          nome: 'Admin Portal',
          email: 'admin@vanrides.dev',
          telefone: '51999999999',
          senha: 'Portal!123',
        ),
      ),
      throwsA(
        isA<AdminPortalException>().having(
          (error) => error.message,
          'message',
          contains('já está em uso'),
        ),
      ),
    );
  });

  test('reenvia verificacao pendente pelo contrato publico', () async {
    final service = AdminPortalAuthService(
      client: MockClient((request) async {
        expect(request.url.path, '/auth/email-verifications/resend');
        return http.Response.bytes(
          utf8.encode(
            jsonEncode({
              'message':
                  'Se existir uma conta pendente para este e-mail, uma nova confirmação será enviada.',
            }),
          ),
          202,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final message = await service.resendVerification(
      email: 'admin@vanrides.dev',
    );

    expect(message, contains('conta pendente'));
  });

  test('altera email pendente e devolve email normalizado', () async {
    final service = AdminPortalAuthService(
      client: MockClient((request) async {
        expect(request.url.path, '/auth/email-verifications/change-email');
        return http.Response.bytes(
          utf8.encode(
            jsonEncode({
              'message':
                  'E-mail pendente atualizado com sucesso. Uma nova confirmação foi enviada.',
              'email': 'novo.admin@vanrides.dev',
            }),
          ),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final email = await service.changePendingEmail(
      currentEmail: 'admin@vanrides.dev',
      senha: 'Portal!123',
      newEmail: 'Novo.Admin@VanRides.dev',
    );

    expect(email, 'novo.admin@vanrides.dev');
  });
}

String _jwtWithCompanyId(int companyId) {
  final header = base64Url
      .encode(utf8.encode(jsonEncode({'alg': 'HS256', 'typ': 'JWT'})))
      .replaceAll('=', '');
  final payload = base64Url
      .encode(
        utf8.encode(
          jsonEncode({'sub': 'admin@vanrides.dev', 'companyId': companyId}),
        ),
      )
      .replaceAll('=', '');
  return '$header.$payload.signature';
}
