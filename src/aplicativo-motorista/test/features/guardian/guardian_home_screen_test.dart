import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_localizacao_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_passageiro_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_presenca_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_linked_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_localizacao_snapshot.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_presenca_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_home_screen.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class _FakeGuardianGateway implements GuardianPassageiroGateway {
  _FakeGuardianGateway(this.records);

  final List<GuardianLinkedPassageiroRecord> records;

  @override
  Future<List<GuardianLinkedPassageiroRecord>> fetchLinkedPassageiros() async {
    await Future<void>.delayed(Duration.zero);
    return records;
  }
}

class _FakeGuardianPresencaGateway implements GuardianPresencaGateway {
  const _FakeGuardianPresencaGateway(this.recordsByPassageiroId);

  final Map<int, List<GuardianPresencaRecord>> recordsByPassageiroId;

  @override
  Future<void> confirmarPresenca({
    required int passageiroId,
    required DateTime dataPresenca,
  }) async {}

  @override
  Future<List<GuardianPresencaRecord>> fetchPresencasByPassageiro(
    int passageiroId,
  ) async {
    await Future<void>.delayed(Duration.zero);
    return recordsByPassageiroId[passageiroId] ?? const [];
  }

  @override
  Future<void> removerPresenca(int presencaId) async {}
}

class _FakeGuardianLocalizacaoGateway implements GuardianLocalizacaoGateway {
  const _FakeGuardianLocalizacaoGateway(this.recordsByMotoristaId);

  final Map<int, List<GuardianLocalizacaoSnapshot>> recordsByMotoristaId;

  @override
  Future<List<GuardianLocalizacaoSnapshot>> fetchHistoricoPorMotorista(
    int motoristaId,
  ) async {
    await Future<void>.delayed(Duration.zero);
    return recordsByMotoristaId[motoristaId] ?? const [];
  }
}

void main() {
  final today = DateTime.now();

  const session = GuardianSession(
    guardianId: 7,
    guardianName: 'Mae da Ana',
    companyId: 1,
    email: 'mae@vanrides.dev',
    accessToken: 'token.guardian',
  );

  testWidgets('renderiza lista de passageiros vinculados', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianHomeScreen(
            session: session,
            gateway: _FakeGuardianGateway(const [
              GuardianLinkedPassageiroRecord(
                id: 10,
                nome: 'Ana',
                email: 'ana@vanrides.dev',
                telefone: '51999990000',
                endereco: 'Rua A',
                bairro: 'Centro',
                cidade: 'Porto Alegre',
                estado: 'RS',
                cep: '90000-000',
                menorDeIdade: true,
                motoristaId: 3,
                motoristaNome: 'Motorista Portal',
              ),
            ]),
            presencaGateway: _FakeGuardianPresencaGateway({
              10: [
                GuardianPresencaRecord(
                  id: 91,
                  passageiroId: 10,
                  dataPresenca: today,
                  confirmado: true,
                ),
              ],
            }),
            localizacaoGateway: _FakeGuardianLocalizacaoGateway({
              3: [
                GuardianLocalizacaoSnapshot(
                  latitude: -29.7,
                  longitude: -51.1,
                  dataHora: today.copyWith(hour: 7, minute: 24),
                ),
              ],
            }),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.textContaining('Bem-vindo, Mae da Ana'), findsOneWidget);
    expect(find.text(AppStrings.guardian.passageirosDaFamilia), findsOneWidget);
    expect(find.text('Passageiro foco'), findsOneWidget);
    expect(find.text('Ana'), findsAtLeastNWidgets(1));
    expect(find.text(AppStrings.guardian.acompanhamentoHoje), findsOneWidget);
    expect(
      find.text(AppStrings.guardian.presencaConfirmadaHoje),
      findsAtLeastNWidgets(1),
    );
    expect(find.textContaining('Última posição às'), findsAtLeastNWidgets(1));
    expect(
      find.text(AppStrings.guardian.acompanharLocalizacao),
      findsAtLeastNWidgets(1),
    );
    expect(
      find.byKey(const ValueKey('guardian.home.passageiro.10')),
      findsOneWidget,
    );
    expect(
      find.textContaining('Motorista cadastrado: Motorista Portal'),
      findsAtLeastNWidgets(1),
    );
  });

  testWidgets('explica estado sem passageiros vinculados', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianHomeScreen(
            session: session,
            gateway: _FakeGuardianGateway(const []),
            presencaGateway: const _FakeGuardianPresencaGateway({}),
            localizacaoGateway: const _FakeGuardianLocalizacaoGateway({}),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(
      find.text(AppStrings.guardian.nenhumPassageiroVinculado),
      findsOneWidget,
    );
    expect(
      find.text(AppStrings.guardian.nenhumPassageiroVinculadoDescricao),
      findsOneWidget,
    );
    expect(find.text('Criar passageiro'), findsNothing);
    expect(find.text('Novo vínculo'), findsNothing);
  });

  testWidgets('resume multiplos passageiros com presenca e localizacao', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianHomeScreen(
            session: session,
            gateway: _FakeGuardianGateway(const [
              GuardianLinkedPassageiroRecord(
                id: 10,
                nome: 'Ana',
                email: 'ana@vanrides.dev',
                telefone: '51999990000',
                endereco: 'Rua A',
                bairro: 'Centro',
                cidade: 'Porto Alegre',
                estado: 'RS',
                cep: '90000-000',
                menorDeIdade: true,
                motoristaId: 3,
                motoristaNome: 'Motorista Portal',
              ),
              GuardianLinkedPassageiroRecord(
                id: 11,
                nome: 'Bruno',
                email: 'bruno@vanrides.dev',
                telefone: '51999991111',
                endereco: 'Rua B',
                bairro: 'Moinhos',
                cidade: 'Porto Alegre',
                estado: 'RS',
                cep: '90010-000',
                menorDeIdade: false,
              ),
            ]),
            presencaGateway: _FakeGuardianPresencaGateway({
              10: [
                GuardianPresencaRecord(
                  id: 91,
                  passageiroId: 10,
                  dataPresenca: today,
                  confirmado: true,
                ),
              ],
              11: const [],
            }),
            localizacaoGateway: _FakeGuardianLocalizacaoGateway({
              3: [
                GuardianLocalizacaoSnapshot(
                  latitude: -29.7,
                  longitude: -51.1,
                  dataHora: today.copyWith(hour: 7, minute: 24),
                ),
              ],
            }),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Ana'), findsAtLeastNWidgets(1));
    await tester.drag(
      find.byKey(const ValueKey('guardian.home')),
      const Offset(0, -420),
    );
    await tester.pumpAndSettle();
    expect(find.text('Bruno'), findsAtLeastNWidgets(1));
    expect(
      find.text(AppStrings.guardian.presencaConfirmadaHoje),
      findsAtLeastNWidgets(1),
    );
    expect(
      find.text(AppStrings.guardian.presencaPendenteHoje),
      findsAtLeastNWidgets(1),
    );
    expect(
      find.text(AppStrings.guardian.viagemDefineMotorista),
      findsAtLeastNWidgets(1),
    );
  });

  testWidgets('abre detalhe do passageiro ao tocar no card', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianHomeScreen(
            session: session,
            gateway: _FakeGuardianGateway(const [
              GuardianLinkedPassageiroRecord(
                id: 10,
                nome: 'Ana',
                email: 'ana@vanrides.dev',
                telefone: '51999990000',
                endereco: 'Rua A',
                bairro: 'Centro',
                cidade: 'Porto Alegre',
                estado: 'RS',
                cep: '90000-000',
                menorDeIdade: true,
                motoristaId: 3,
                motoristaNome: 'Motorista Portal',
              ),
            ]),
            presencaGateway: const _FakeGuardianPresencaGateway({}),
            localizacaoGateway: const _FakeGuardianLocalizacaoGateway({}),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();
    final passengerCard = find.byKey(
      const ValueKey('guardian.home.passageiro.10'),
    );
    await tester.ensureVisible(passengerCard);
    await tester.pumpAndSettle();
    await tester.tap(passengerCard);
    await tester.pumpAndSettle();

    expect(find.text(AppStrings.guardian.passageiroVinculado), findsOneWidget);
    expect(find.text(AppStrings.guardian.dadosBasicos), findsOneWidget);
    expect(find.text('Ana'), findsAtLeastNWidgets(1));
  });

  testWidgets('mantem vinculo ativo para passageiro sem motorista fixo', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianHomeScreen(
            session: session,
            gateway: _FakeGuardianGateway(const [
              GuardianLinkedPassageiroRecord(
                id: 11,
                nome: 'Bruno',
                email: 'bruno@vanrides.dev',
                telefone: '51999991111',
                endereco: 'Rua B',
                bairro: 'Moinhos',
                cidade: 'Porto Alegre',
                estado: 'RS',
                cep: '90010-000',
                menorDeIdade: true,
              ),
            ]),
            presencaGateway: const _FakeGuardianPresencaGateway({}),
            localizacaoGateway: const _FakeGuardianLocalizacaoGateway({}),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Bruno'), findsAtLeastNWidgets(1));
    expect(
      find.text(AppStrings.guardian.vinculoAtivo),
      findsAtLeastNWidgets(1),
    );
    expect(find.text(AppStrings.guardian.semMotorista), findsNothing);

    final passengerCard = find.byKey(
      const ValueKey('guardian.home.passageiro.11'),
    );
    await tester.ensureVisible(passengerCard);
    await tester.pumpAndSettle();
    await tester.tap(passengerCard);
    await tester.pumpAndSettle();

    expect(
      find.text(AppStrings.guardian.vinculoAtivo),
      findsAtLeastNWidgets(1),
    );
    expect(find.text(AppStrings.guardian.semMotorista), findsNothing);
    await tester.dragUntilVisible(
      find.text(AppStrings.guardian.contextoDaFamilia),
      find.byType(ListView),
      const Offset(0, -250),
    );
    await tester.pumpAndSettle();

    expect(
      find.text(AppStrings.guardian.contextoDaFamiliaDescricao),
      findsOneWidget,
    );
  });
}
