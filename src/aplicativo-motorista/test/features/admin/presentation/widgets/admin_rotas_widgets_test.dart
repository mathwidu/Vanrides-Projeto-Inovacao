import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_rotas_widgets.dart';

void main() {
  testWidgets('Admin rotas widgets render route summary and open callback', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    var opened = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: Column(
              children: [
                const AdminRotasLegacyNotice(),
                AdminRotasStatCard(
                  eyebrow: 'ROTAS LEGADAS ATIVAS',
                  child: Text(_rota.quantidadeParadas.toString()),
                ),
                AdminRotaListCard(rota: _rota, onOpen: () => opened++),
                const AdminRotasDetailInfoTile(
                  label: 'Motorista legado da rota-base',
                  value: 'Motorista Portal',
                  helper: '51999999999',
                ),
                const AdminRotasDarkMetricRow(
                  leftLabel: 'Status da rota',
                  leftValue: 'ATIVA',
                  rightLabel: 'Auto-geração',
                  rightValue: 'ATIVO',
                ),
                const AdminRotasPaginationFooter(
                  summary: 'Exibindo 1-1 de 1 rotas-base legadas',
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('Fluxo legado de rota-base'), findsOneWidget);
    expect(find.text('ROTAS LEGADAS ATIVAS'), findsOneWidget);
    expect(find.text('Rota Escolar Zona Sul'), findsOneWidget);
    expect(find.text('Motorista Portal'), findsWidgets);
    expect(find.text('Escola Central'), findsOneWidget);
    expect(find.text('AUTO-GERAÇÃO'), findsOneWidget);
    expect(find.text('Exibindo 1-1 de 1 rotas-base legadas'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.chevron_right_rounded));
    await tester.pumpAndSettle();

    expect(opened, 1);
  });

  testWidgets('AdminRotasErrorState dispatches retry callback', (tester) async {
    var retryCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: AdminRotasErrorState(
          message: 'Backend indisponível',
          onRetry: () async => retryCount++,
        ),
      ),
    );

    expect(find.text('Falha ao carregar rotas'), findsOneWidget);
    expect(find.text('Backend indisponível'), findsOneWidget);

    await tester.tap(find.text('Tentar novamente'));
    await tester.pumpAndSettle();

    expect(retryCount, 1);
  });
}

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
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
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  destinoFinalEndereco: 'Rua Principal, 100',
  destinoFinalLatitude: -30.01,
  destinoFinalLongitude: -51.22,
);
