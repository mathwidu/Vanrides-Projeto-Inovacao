import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_viagens_widgets.dart';

void main() {
  testWidgets(
    'AdminViagensOperationalListSection renders summary and opens trip detail',
    (tester) async {
      int? openedTripId;

      await tester.binding.setSurfaceSize(const Size(1440, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SizedBox(
              width: 1440,
              child: SingleChildScrollView(
                child: AdminViagensOperationalListSection(
                  viagens: _sampleTrips,
                  inProgressCount: 1,
                  completedCount: 1,
                  isGeneratingAutomatic: false,
                  onGenerateAutomatic: () {},
                  onCreateManual: () {},
                  onOpenDetail: (tripId) => openedTripId = tripId,
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Estado do dia'), findsOneWidget);
      expect(find.text('1 lista para revisar'), findsOneWidget);
      expect(find.text('2 montadas'), findsOneWidget);
      expect(find.text('1 em operação'), findsOneWidget);
      expect(find.text('1 concluída'), findsOneWidget);
      expect(find.text('VIAGENS MONTADAS'), findsNothing);
      expect(find.text('CONCLUÍDAS'), findsNothing);
      expect(find.text('Viagens do dia'), findsOneWidget);
      expect(find.text('Manutenção rápida'), findsOneWidget);
      expect(
        find.text(
          'Mudou passageiro, motorista ou presença? Atualize o dia e revise só as listas marcadas.',
        ),
        findsOneWidget,
      );
      expect(
        find.text(
          'Abra a viagem existente e use Sincronizar lista quando o serviço mudar. Exceção do dia é só para algo fora do plano recorrente.',
        ),
        findsOneWidget,
      );
      expect(find.text('Abrir viagem existente'), findsNothing);
      expect(find.text('PRÓXIMO PASSO'), findsOneWidget);
      expect(find.text('Revisar lista'), findsOneWidget);
      expect(find.text('Abrir'), findsOneWidget);
      expect(find.text('Lista pendente'), findsOneWidget);
      expect(find.text('1 para incluir / 1 para remover'), findsOneWidget);
      expect(find.text('+1 / -1'), findsNothing);
      expect(find.text('Linha Centro'), findsOneWidget);
      expect(find.text('Linha Norte'), findsOneWidget);
      expect(find.textContaining('Exibindo 1-2 de 2 viagens'), findsOneWidget);

      await tester.tap(find.byTooltip('Abrir viagem Linha Norte'));
      await tester.pump();

      expect(openedTripId, 32);
    },
  );

  testWidgets('AdminViagensOperationalListSection renders empty state actions', (
    tester,
  ) async {
    var generated = false;
    var created = false;

    await tester.binding.setSurfaceSize(const Size(1200, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: AdminViagensOperationalListSection(
              viagens: const [],
              inProgressCount: 0,
              completedCount: 0,
              isGeneratingAutomatic: false,
              onGenerateAutomatic: () => generated = true,
              onCreateManual: () => created = true,
              onOpenDetail: (_) {},
            ),
          ),
        ),
      ),
    );

    expect(
      find.text('Nenhuma viagem encontrada com o filtro atual.'),
      findsOneWidget,
    );
    expect(find.text('Dia ainda não montado'), findsOneWidget);
    expect(
      find.text(
        'Monte o dia para trazer as viagens recorrentes. Use Exceção do dia só para algo fora do plano.',
      ),
      findsOneWidget,
    );

    await tester.tap(find.text('Montar dia').first);
    await tester.pump();
    await tester.tap(find.text('Exceção do dia').first);
    await tester.pump();

    expect(generated, isTrue);
    expect(created, isTrue);
  });
}

const _primaryDriver = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Principal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

final _sampleTrips = [
  AdminViagemRecord(
    id: 31,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha Centro',
    dataOperacao: DateTime(2026, 4, 15),
    status: 'IN_PROGRESS',
    motorista: _primaryDriver,
    quantidadeParadas: 3,
    horarioPartida: '07:30',
    manifesto: const AdminViagemManifestRecord(
      status: 'PENDING',
      mudancas: 2,
      adicionados: 1,
      removidos: 1,
      mensagem: 'Sincronize o manifesto antes da saída.',
    ),
  ),
  AdminViagemRecord(
    id: 32,
    rotaNome: 'Rota Norte',
    servicoNome: 'Linha Norte',
    dataOperacao: DateTime(2026, 4, 15),
    status: 'COMPLETED',
    motorista: _primaryDriver,
    quantidadeParadas: 4,
    horarioPartida: '12:10',
  ),
];
