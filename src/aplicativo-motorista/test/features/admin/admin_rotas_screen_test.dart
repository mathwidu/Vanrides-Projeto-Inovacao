import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_rotas_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_rotas_screen.dart';

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

  testWidgets('renderiza rotas-base como area legada de compatibilidade', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminRotasScreen(
          session: session,
          gateway: _FakeAdminRotasGateway(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Rotas-base legadas'), findsOneWidget);
    expect(find.text('Criar rota legada'), findsOneWidget);
    expect(find.text('Fluxo legado de rota-base'), findsOneWidget);
    expect(find.textContaining('Serviços recorrentes'), findsWidgets);
    expect(
      find.textContaining('Rotas-base ficam para compatibilidade'),
      findsOneWidget,
    );
    expect(find.text('Rota Escolar Zona Sul'), findsOneWidget);
    expect(find.text('ROTAS LEGADAS ATIVAS'), findsOneWidget);
    expect(find.text('AUTO-GERAÇÃO LEGADA'), findsOneWidget);
    expect(find.text('Escola Central'), findsOneWidget);
    expect(
      find.textContaining('Exibindo 1-1 de 1 rotas-base legadas'),
      findsOneWidget,
    );
  });

  testWidgets('detalhe da rota explica destino final persistido no contrato', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminRotasScreen(
          session: session,
          gateway: _FakeAdminRotasGateway(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.chevron_right_rounded));
    await tester.pumpAndSettle();

    expect(find.text('Escola Central'), findsWidgets);
    expect(find.text('Rua Principal, 100'), findsWidgets);
    expect(find.textContaining('destino final já está salvo'), findsOneWidget);
    expect(
      find.textContaining('Destino final ainda não faz parte'),
      findsNothing,
    );
    expect(
      find.textContaining('Quando o backend passar a expor'),
      findsNothing,
    );
  });

  testWidgets(
    'explica quando a empresa ainda nao tem passageiros para criar rota',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1440, 1200));

      await tester.pumpWidget(
        const MaterialApp(
          home: AdminRotasScreen(
            session: session,
            gateway: _FakeAdminRotasGatewaySemPassageiros(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      await tester.tap(find.text('Criar primeira rota legada'));
      await tester.pumpAndSettle();

      expect(find.text('Criar rota-base'), findsOneWidget);
      expect(
        find.textContaining('campos já confirmados na operação'),
        findsOneWidget,
      );
      expect(
        find.textContaining(
          'Esta empresa ainda não possui passageiros cadastrados',
        ),
        findsOneWidget,
      );
    },
  );
}

class _FakeAdminRotasGateway implements AdminRotasGateway {
  const _FakeAdminRotasGateway();

  @override
  Future<AdminRotaBaseRecord> createRota(
    AdminRotaBaseCreateInput input,
  ) async => _rota;

  @override
  Future<AdminRotaBaseRecord> editRota(
    int rotaId,
    AdminRotaBaseCreateInput input,
  ) async => _rota;

  @override
  Future<void> deleteRota(int rotaId) async {}

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => const [
    _motorista,
  ];

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [
    _passageiro,
  ];

  @override
  Future<List<AdminRotaBaseRecord>> fetchRotas() async => const [_rota];
}

class _FakeAdminRotasGatewaySemPassageiros implements AdminRotasGateway {
  const _FakeAdminRotasGatewaySemPassageiros();

  @override
  Future<AdminRotaBaseRecord> createRota(
    AdminRotaBaseCreateInput input,
  ) async => _rota;

  @override
  Future<AdminRotaBaseRecord> editRota(
    int rotaId,
    AdminRotaBaseCreateInput input,
  ) async => _rota;

  @override
  Future<void> deleteRota(int rotaId) async {}

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => const [
    _motorista,
  ];

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [];

  @override
  Future<List<AdminRotaBaseRecord>> fetchRotas() async => const [];
}

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

const _passageiro = AdminPassageiroRecord(
  id: 11,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51999999998',
  menorDeIdade: true,
);

const _rota = AdminRotaBaseRecord(
  id: 21,
  nome: 'Rota Escolar Zona Sul',
  motorista: _motorista,
  quantidadeParadas: 1,
  passageiroIds: [11],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  diasOperacao: ['SEG', 'TER', 'QUA', 'QUI', 'SEX'],
  destinoFinalNome: 'Escola Central',
  destinoFinalEndereco: 'Rua Principal, 100',
  destinoFinalLatitude: -30.01,
  destinoFinalLongitude: -51.22,
);
