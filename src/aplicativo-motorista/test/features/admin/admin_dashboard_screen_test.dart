import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_dashboard_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_dashboard_summary_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_dashboard_screen.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

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

  testWidgets('exibe métricas reais quando o backend responde com dados', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminDashboardScreen(
          session: session,
          gateway: _FakeGateway(_summaryComDados),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('5'), findsWidgets);
    expect(find.text('8'), findsOneWidget);
    expect(find.text('2'), findsWidgets);
    expect(find.text('1 EM ANDAMENTO'), findsOneWidget);
    expect(find.text('1 concluídas · 1 planejadas'), findsOneWidget);
    expect(find.text('Operação de hoje'), findsOneWidget);
    expect(find.text('Hoje operacional'), findsOneWidget);
    expect(find.text('Dia em execução'), findsOneWidget);
    expect(
      find.text('1 viagem em andamento • 1 concluída • 1 planejada'),
      findsOneWidget,
    );
    expect(find.text('Serviços recorrentes'), findsOneWidget);
    expect(find.text('5 ativos • 4 automáticos'), findsOneWidget);
    expect(find.text('1 exigem escala diária'), findsOneWidget);
    expect(find.text('ESCALA PENDENTE'), findsOneWidget);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Defina motorista para serviços'), findsOneWidget);
    expect(find.text('Revisar serviços'), findsOneWidget);
    expect(find.text('8 confirmadas • 2 ausências'), findsOneWidget);
    expect(find.byIcon(Icons.more_vert), findsNothing);
    expect(find.text('Add Motorista'), findsNothing);
    expect(find.text('Relatórios'), findsNothing);
  });

  testWidgets('atalhos rapidos usam rotulos de navegacao reais', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    var abriuMotoristas = false;

    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboardScreen(
          session: session,
          gateway: const _FakeGateway(_summaryComDados),
          onOpenServicos: () {},
          onOpenViagens: () {},
          onOpenMotoristas: () => abriuMotoristas = true,
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Abrir serviços'), findsWidgets);
    expect(find.text('Abrir viagens'), findsWidgets);
    expect(find.text('Motoristas'), findsOneWidget);
    expect(find.text('Novo Serviço'), findsNothing);
    expect(find.text('Montar dia'), findsNothing);
    expect(find.text('Relatórios'), findsNothing);

    await tester.tap(find.text('Motoristas'));
    expect(abriuMotoristas, isTrue);
  });

  testWidgets('proxima acao abre viagens quando o dia ainda nao foi montado', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    var abriuViagens = false;

    const summary = AdminDashboardSummaryRecord(
      motoristasAtivos: 2,
      rotasAtivas: 1,
      passageirosComPresencaHoje: 3,
      servicosRecorrentes: AdminServicosRecorrentesSummaryRecord(
        ativos: 2,
        automaticos: 2,
        semMotorista: 0,
      ),
      presencasServicoHoje: AdminPresencasServicoSummaryRecord(
        confirmadas: 0,
        ausentes: 0,
        canceladas: 0,
        totalDecisoes: 0,
      ),
      viagensHoje: AdminViagensSummaryRecord(
        total: 0,
        emAndamento: 0,
        concluidas: 0,
        planejadas: 0,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboardScreen(
          session: session,
          gateway: const _FakeGateway(summary),
          onOpenViagens: () => abriuViagens = true,
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Monte as viagens do dia'), findsOneWidget);

    await tester.tap(find.text('Abrir viagens').first);
    expect(abriuViagens, isTrue);
  });

  testWidgets('lista ate tres revisoes para empresa em implantacao', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    var abriuPassageiros = false;

    const summary = AdminDashboardSummaryRecord(
      motoristasAtivos: 0,
      rotasAtivas: 1,
      passageirosComPresencaHoje: 0,
      servicosRecorrentes: AdminServicosRecorrentesSummaryRecord(
        ativos: 2,
        automaticos: 1,
        semMotorista: 2,
      ),
      presencasServicoHoje: AdminPresencasServicoSummaryRecord(
        confirmadas: 0,
        ausentes: 0,
        canceladas: 0,
        totalDecisoes: 0,
      ),
      viagensHoje: AdminViagensSummaryRecord(
        total: 0,
        emAndamento: 0,
        concluidas: 0,
        planejadas: 0,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboardScreen(
          session: session,
          gateway: const _FakeGateway(summary),
          onOpenServicos: () {},
          onOpenMotoristas: () {},
          onOpenPassageiros: () => abriuPassageiros = true,
          onOpenViagens: () {},
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Precisa de revisão'), findsOneWidget);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Cadastre motorista operacional'), findsOneWidget);
    expect(find.text('Defina motorista para serviços'), findsOneWidget);
    expect(find.text('Libere passageiros para os serviços'), findsOneWidget);
    expect(find.text('Monte as viagens do dia'), findsNothing);

    await tester.ensureVisible(find.text('Passageiros').first);
    await tester.tap(find.text('Passageiros').first);
    expect(abriuPassageiros, isTrue);
  });

  testWidgets('exibe zeros com labels contextuais para empresa nova', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboardScreen(
          session: session,
          gateway: _FakeGateway(AdminDashboardSummaryRecord.zeros()),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('SEM CADASTRO'), findsOneWidget);
    expect(find.text('SEM DECISÃO'), findsWidgets);
    expect(find.text('Nenhuma viagem registrada hoje'), findsOneWidget);
    expect(find.text('Dia ainda não montado'), findsOneWidget);
    expect(
      find.text('Serviços prontos: 0 • decisões de presença: 0'),
      findsOneWidget,
    );
    expect(find.text('SEM SERVIÇOS'), findsOneWidget);
    expect(find.text('CADASTRAR'), findsOneWidget);
    expect(find.text('NÃO MONTADO'), findsOneWidget);
  });

  testWidgets('exibe snackbar quando o backend falha e mantém zeros na tela', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminDashboardScreen(
            session: session,
            gateway: _FailingGateway(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(
      find.textContaining(AppStrings.admin.erroCarregarDashboard),
      findsOneWidget,
    );
    expect(find.text('Dashboard Operacional'), findsOneWidget);
  });

  testWidgets('exibe "—" enquanto os dados carregam', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    final completer = Completer<AdminDashboardSummaryRecord>();

    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboardScreen(
          session: session,
          gateway: _BlockingGateway(completer.future),
        ),
      ),
    );

    await tester.pump();
    expect(find.text('—'), findsWidgets);

    completer.complete(_summaryComDados);
    await tester.pumpAndSettle();
  });
}

const _summaryComDados = AdminDashboardSummaryRecord(
  motoristasAtivos: 5,
  rotasAtivas: 3,
  passageirosComPresencaHoje: 12,
  servicosRecorrentes: AdminServicosRecorrentesSummaryRecord(
    ativos: 5,
    automaticos: 4,
    semMotorista: 1,
  ),
  presencasServicoHoje: AdminPresencasServicoSummaryRecord(
    confirmadas: 8,
    ausentes: 2,
    canceladas: 1,
    totalDecisoes: 11,
  ),
  viagensHoje: AdminViagensSummaryRecord(
    total: 2,
    emAndamento: 1,
    concluidas: 1,
    planejadas: 1,
  ),
);

class _FakeGateway implements AdminDashboardGateway {
  const _FakeGateway(this._summary);
  final AdminDashboardSummaryRecord _summary;

  @override
  Future<AdminDashboardSummaryRecord> fetchSummary() async => _summary;
}

class _FailingGateway implements AdminDashboardGateway {
  @override
  Future<AdminDashboardSummaryRecord> fetchSummary() async {
    throw Exception('backend indisponivel');
  }
}

class _BlockingGateway implements AdminDashboardGateway {
  const _BlockingGateway(this._future);
  final Future<AdminDashboardSummaryRecord> _future;

  @override
  Future<AdminDashboardSummaryRecord> fetchSummary() => _future;
}
