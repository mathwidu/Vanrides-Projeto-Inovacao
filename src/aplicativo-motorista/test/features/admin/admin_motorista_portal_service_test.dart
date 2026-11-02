import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_motorista_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_create_input.dart';
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

  test('busca motoristas da API', () async {
    final service = AdminMotoristaPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/company-admins/motoristas');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode([
            {
              'id': 1,
              'nome': 'Motorista 1',
              'email': 'm1@vanrides.dev',
              'telefone': '51999990001',
              'dataNascimento': '1992-03-14',
              'fotoPerfilUrl': '/uploads/profiles/motorista-1.jpg',
            },
          ]),
          200,
        );
      }),
    );

    final items = await service.fetchMotoristas();

    expect(items, hasLength(1));
    expect(items.first.nome, 'Motorista 1');
    expect(items.first.dataNascimento, '1992-03-14');
    expect(items.first.fotoPerfilUrl, '/uploads/profiles/motorista-1.jpg');
  });

  test('cria motorista usando contrato atual', () async {
    final service = AdminMotoristaPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/motoristas');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['nome'], 'Novo Motorista');
        expect(payload['dataNascimento'], '1992-03-14');
        expect(payload['senha'], 'Senha@123');
        return http.Response(
          jsonEncode({
            'id': 99,
            'nome': payload['nome'],
            'email': payload['email'],
            'telefone': payload['telefone'],
            'dataNascimento': payload['dataNascimento'],
            'fotoPerfilUrl': '/uploads/profiles/motorista-99.jpg',
          }),
          201,
        );
      }),
    );

    final created = await service.createMotorista(
      const AdminMotoristaCreateInput(
        nome: 'Novo Motorista',
        email: 'novo@vanrides.dev',
        telefone: '51999990099',
        dataNascimento: '1992-03-14',
        senha: 'Senha@123',
      ),
    );

    expect(created.id, 99);
    expect(created.email, 'novo@vanrides.dev');
    expect(created.dataNascimento, '1992-03-14');
    expect(created.fotoPerfilUrl, '/uploads/profiles/motorista-99.jpg');
  });

  test('vincula motorista existente sem enviar senha vazia', () async {
    final service = AdminMotoristaPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/motoristas');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['nome'], 'Motorista App');
        expect(payload['dataNascimento'], '1988-11-30');
        expect(payload.containsKey('senha'), isFalse);
        return http.Response(
          jsonEncode({
            'id': 100,
            'nome': payload['nome'],
            'email': payload['email'],
            'telefone': payload['telefone'],
          }),
          201,
        );
      }),
    );

    final linked = await service.createMotorista(
      const AdminMotoristaCreateInput(
        nome: 'Motorista App',
        email: 'motorista.app@vanrides.dev',
        telefone: '51999990100',
        dataNascimento: '1988-11-30',
      ),
    );

    expect(linked.id, 100);
    expect(linked.email, 'motorista.app@vanrides.dev');
  });
}
