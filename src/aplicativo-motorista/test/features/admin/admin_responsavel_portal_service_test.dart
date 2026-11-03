import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_responsavel_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_update_input.dart';

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

  test('lista responsaveis administrativos da empresa', () async {
    final service = AdminResponsavelPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/responsaveis');
        return http.Response(
          jsonEncode([
            {
              'id': 7,
              'nome': 'Responsavel Portal',
              'email': 'responsavel@vanrides.dev',
              'telefone': '51999999997',
              'dataNascimento': '1980-09-02',
              'fotoPerfilUrl': '/uploads/profiles/responsavel-7.jpg',
              'passageiroCount': 2,
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchResponsaveis();

    expect(result, hasLength(1));
    expect(result.first.passageiroCount, 2);
    expect(result.first.dataNascimento, '1980-09-02');
    expect(result.first.fotoPerfilUrl, '/uploads/profiles/responsavel-7.jpg');
  });

  test('cria responsavel com payload esperado', () async {
    final service = AdminResponsavelPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/responsaveis');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['nome'], 'Responsavel Portal');
        expect(body['dataNascimento'], '1980-09-02');
        expect(body['senha'], 'Responsavel@123');
        return http.Response(
          jsonEncode({
            'id': 7,
            'nome': 'Responsavel Portal',
            'email': 'responsavel@vanrides.dev',
            'telefone': '51999999997',
            'dataNascimento': '1980-09-02',
            'fotoPerfilUrl': '/uploads/profiles/responsavel-7.jpg',
            'passageiroCount': 0,
            'acessoConfigurado': true,
          }),
          201,
        );
      }),
    );

    final created = await service.createResponsavel(
      const AdminResponsavelCreateInput(
        nome: 'Responsavel Portal',
        email: 'responsavel@vanrides.dev',
        telefone: '51999999997',
        dataNascimento: '1980-09-02',
        senha: 'Responsavel@123',
      ),
    );

    expect(created.nome, 'Responsavel Portal');
    expect(created.acessoConfigurado, isTrue);
    expect(created.dataNascimento, '1980-09-02');
    expect(created.fotoPerfilUrl, '/uploads/profiles/responsavel-7.jpg');
  });

  test('atualiza responsavel administrativo', () async {
    final service = AdminResponsavelPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'PUT');
        expect(request.url.path, '/company-admins/responsaveis/7');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['telefone'], '51998887777');
        expect(body['senha'], 'NovaSenha@123');
        return http.Response(
          jsonEncode({
            'id': 7,
            'nome': 'Responsavel Atualizado',
            'email': 'responsavel@vanrides.dev',
            'telefone': '51998887777',
            'passageiroCount': 1,
            'acessoConfigurado': true,
          }),
          200,
        );
      }),
    );

    final updated = await service.updateResponsavel(
      7,
      const AdminResponsavelUpdateInput(
        nome: 'Responsavel Atualizado',
        email: 'responsavel@vanrides.dev',
        telefone: '51998887777',
        senha: 'NovaSenha@123',
      ),
    );

    expect(updated.nome, 'Responsavel Atualizado');
    expect(updated.telefone, '51998887777');
    expect(updated.acessoConfigurado, isTrue);
  });
}
