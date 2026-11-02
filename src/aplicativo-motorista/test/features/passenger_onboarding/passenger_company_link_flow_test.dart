import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/data/passenger_onboarding_service.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/domain/passenger_onboarding_models.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/presentation/passenger_company_link_flow.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

const _session = AppSessionUser(
  userId: 42,
  displayName: 'Ana Passageira',
  email: 'ana@test.dev',
  password: 'senha123',
  role: AppUserRole.passenger,
  backendRoleLabel: 'PASSENGER',
  accessToken: 'token.test',
);

class _FakePassengerOnboardingGateway implements PassengerOnboardingGateway {
  _FakePassengerOnboardingGateway({
    this.onSearchCompanies,
    this.onRedeemInviteCode,
  });

  final Future<List<PassengerOnboardingCompanyRecord>> Function(
    AppSessionUser session,
    String? query,
  )?
  onSearchCompanies;
  final Future<PassengerOnboardingStatusRecord> Function(
    AppSessionUser session,
    String code,
  )?
  onRedeemInviteCode;

  @override
  Future<PassengerOnboardingStatusRecord> fetchStatus(
    AppSessionUser session,
  ) async {
    return const PassengerOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.noCompany,
    );
  }

  @override
  Future<List<PassengerOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  }) async {
    if (onSearchCompanies != null) {
      return onSearchCompanies!(session, query);
    }

    return const [];
  }

  @override
  Future<PassengerOnboardingStatusRecord> createRequest(
    AppSessionUser session, {
    required int companyId,
  }) async {
    return const PassengerOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
    );
  }

  @override
  Future<PassengerOnboardingStatusRecord> redeemInviteCode(
    AppSessionUser session, {
    required String code,
  }) async {
    if (onRedeemInviteCode != null) {
      return onRedeemInviteCode!(session, code);
    }

    throw const PassengerOnboardingException('Código inválido');
  }

  @override
  Future<AppSessionUser> upgradeSession(
    AppSessionUser session, {
    int? motoristaId,
    String? companyName,
  }) async {
    return session;
  }
}

Future<void> _pumpScreen(WidgetTester tester, Widget screen) async {
  await tester.binding.setSurfaceSize(const Size(390, 844));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  await tester.pumpWidget(MaterialApp(home: screen));
  await tester.pumpAndSettle();
}

void main() {
  group('PassengerCompanyGateScreen', () {
    testWidgets('exibe opcoes de vincular por busca e por codigo', (
      tester,
    ) async {
      await _pumpScreen(
        tester,
        PassengerCompanyGateScreen(
          sessionUser: _session,
          gateway: _FakePassengerOnboardingGateway(),
        ),
      );

      expect(find.text('Entrar em uma empresa'), findsOneWidget);
      expect(find.text('Vincular a uma empresa'), findsOneWidget);
      expect(find.text('Já tenho um código'), findsOneWidget);
      _expectPassengerStepper(
        tester,
        current: 'Empresa',
        completed: const ['Conta', 'E-mail'],
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('PassengerCompanySearchScreen', () {
    testWidgets('exibe resultados quando gateway retorna empresas', (
      tester,
    ) async {
      final gateway = _FakePassengerOnboardingGateway(
        onSearchCompanies: (session, query) async {
          if ((query ?? '').trim().isEmpty) {
            return const [];
          }

          return const [
            PassengerOnboardingCompanyRecord(
              id: 1,
              nome: 'Empresa Alpha',
              slug: 'empresa-alpha',
            ),
            PassengerOnboardingCompanyRecord(
              id: 2,
              nome: 'Empresa Beta',
              slug: 'empresa-beta',
            ),
          ];
        },
      );

      await _pumpScreen(
        tester,
        PassengerCompanySearchScreen(sessionUser: _session, gateway: gateway),
      );

      await tester.enterText(find.byType(TextField), 'Empresa');
      await tester.tap(find.text('Buscar'));
      await tester.pumpAndSettle();

      expect(find.text('Empresa Alpha'), findsOneWidget);
      expect(find.text('Empresa Beta'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('solicitacao de acesso usa callback de status do fluxo', (
      tester,
    ) async {
      PassengerOnboardingStatusRecord? statusCapturado;
      final gateway = _FakePassengerOnboardingGateway(
        onSearchCompanies: (session, query) async {
          return const [
            PassengerOnboardingCompanyRecord(
              id: 1,
              nome: 'Empresa Alpha',
              slug: 'empresa-alpha',
            ),
          ];
        },
      );

      await _pumpScreen(
        tester,
        PassengerCompanySearchScreen(
          sessionUser: _session,
          gateway: gateway,
          onStatusNavigation: (
            context, {
            required sessionUser,
            required gateway,
            required status,
          }) async {
            statusCapturado = status;
          },
        ),
      );

      await tester.tap(find.text('Empresa Alpha'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.text(AppStrings.passageiro.enviarSolicitacao),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text(AppStrings.passageiro.enviarSolicitacao));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(
        statusCapturado?.companyLinkStatus,
        AppCompanyLinkStatus.pendingApproval,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('exibe estado inicial sem resultados', (tester) async {
      await _pumpScreen(
        tester,
        PassengerCompanySearchScreen(
          sessionUser: _session,
          gateway: _FakePassengerOnboardingGateway(),
        ),
      );

      expect(find.text('Nenhuma empresa encontrada'), findsOneWidget);
      expect(find.text('Empresa Alpha'), findsNothing);
      _expectPassengerStepper(
        tester,
        current: 'Empresa',
        completed: const ['Conta', 'E-mail'],
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('PassengerInviteCodeScreen', () {
    testWidgets('exibe campo de codigo e botao de confirmar', (tester) async {
      await _pumpScreen(
        tester,
        PassengerInviteCodeScreen(
          sessionUser: _session,
          gateway: _FakePassengerOnboardingGateway(),
        ),
      );

      expect(find.byType(TextField), findsOneWidget);
      expect(find.text('Validar código'), findsOneWidget);
      _expectPassengerStepper(
        tester,
        current: 'Empresa',
        completed: const ['Conta', 'E-mail'],
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('codigo valido usa callback de status do fluxo', (
      tester,
    ) async {
      PassengerOnboardingStatusRecord? statusCapturado;
      final gateway = _FakePassengerOnboardingGateway(
        onRedeemInviteCode: (session, code) async {
          return const PassengerOnboardingStatusRecord(
            companyLinkStatus: AppCompanyLinkStatus.companyLinked,
            companyName: 'Empresa Alpha',
            motoristaId: 77,
          );
        },
      );

      await _pumpScreen(
        tester,
        PassengerInviteCodeScreen(
          sessionUser: _session,
          gateway: gateway,
          onStatusNavigation: (
            context, {
            required sessionUser,
            required gateway,
            required status,
          }) async {
            statusCapturado = status;
          },
        ),
      );

      await tester.enterText(find.byType(TextField), 'INV-2026-OK');
      await tester.tap(find.text('Validar código'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(
        statusCapturado?.companyLinkStatus,
        AppCompanyLinkStatus.companyLinked,
      );
      expect(statusCapturado?.motoristaId, 77);
      expect(tester.takeException(), isNull);
    });

    testWidgets('exibe erro quando codigo é invalido', (tester) async {
      final gateway = _FakePassengerOnboardingGateway(
        onRedeemInviteCode: (session, code) async {
          throw const PassengerOnboardingException('Código inválido');
        },
      );

      await _pumpScreen(
        tester,
        PassengerInviteCodeScreen(sessionUser: _session, gateway: gateway),
      );

      await tester.enterText(find.byType(TextField), 'INV-2026-ABCD');
      await tester.tap(find.text('Validar código'));
      await tester.pumpAndSettle();

      expect(
        find.text(AppStrings.passageiro.codigoInvalidoOuExpirado),
        findsOneWidget,
      );
      expect(find.text('Código inválido'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  group('PassengerCompanyPendingScreen', () {
    testWidgets('exibe texto e icones do estado pendente', (tester) async {
      const status = PassengerOnboardingStatusRecord(
        companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
        companyName: 'Empresa Horizonte',
        pendingRequest: PendingCompanyLinkRequestRecord(
          id: 99,
          companyId: 7,
          companyName: 'Empresa Horizonte',
          status: 'PENDING',
        ),
      );

      await _pumpScreen(
        tester,
        PassengerCompanyPendingScreen(
          sessionUser: _session,
          status: status,
          gateway: _FakePassengerOnboardingGateway(),
        ),
      );

      expect(find.text('Solicitação enviada'), findsOneWidget);
      expect(find.text('Empresa Horizonte'), findsOneWidget);
      expect(find.text('Atualizar status'), findsOneWidget);
      expect(find.byIcon(Icons.mark_email_read_outlined), findsOneWidget);
      expect(find.byIcon(Icons.schedule_rounded), findsOneWidget);
      _expectPassengerStepper(
        tester,
        current: 'Empresa',
        completed: const ['Conta', 'E-mail'],
      );
    });

    testWidgets('renderiza estado pendente sem lançar excecao', (tester) async {
      const status = PassengerOnboardingStatusRecord(
        companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
        companyName: 'Empresa Horizonte',
      );

      await _pumpScreen(
        tester,
        PassengerCompanyPendingScreen(
          sessionUser: _session,
          status: status,
          gateway: _FakePassengerOnboardingGateway(),
        ),
      );

      expect(
        find.text(AppStrings.passageiro.aguardandoAprovacaoBadge),
        findsOneWidget,
      );
      expect(find.textContaining('até 24 horas úteis'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  group('PassengerCompanyLinkSuccessScreen', () {
    testWidgets('mostra etapa pronta apos vinculo concluido', (tester) async {
      await _pumpScreen(
        tester,
        const PassengerCompanyLinkSuccessScreen(
          sessionUser: _session,
          companyName: 'Empresa Alpha',
        ),
      );

      expect(find.text('Empresa Alpha'), findsOneWidget);
      _expectPassengerStepper(
        tester,
        current: 'Pronto',
        completed: const ['Conta', 'E-mail', 'Empresa', 'Serviços'],
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('PassengerLinkedPendingDriverScreen', () {
    testWidgets(
      'exibe informacoes do estado vinculado em preparo operacional',
      (tester) async {
        await _pumpScreen(
          tester,
          PassengerLinkedPendingDriverScreen(
            sessionUser: _session,
            gateway: _FakePassengerOnboardingGateway(),
          ),
        );

        expect(find.text('Empresa vinculada'), findsOneWidget);
        expect(find.text('Próximo passo operacional'), findsOneWidget);
        expect(find.text('Atualizar operação agora'), findsOneWidget);
        expect(find.byIcon(Icons.route_rounded), findsAtLeastNWidgets(1));
        _expectPassengerStepper(
          tester,
          current: 'Serviços',
          completed: const ['Conta', 'E-mail', 'Empresa'],
        );
      },
    );

    testWidgets('renderiza tela vinculada sem lançar excecao', (tester) async {
      await _pumpScreen(
        tester,
        PassengerLinkedPendingDriverScreen(
          sessionUser: _session,
          gateway: _FakePassengerOnboardingGateway(),
        ),
      );

      expect(
        find.text(AppStrings.passageiro.operacaoEmPreparoBadge),
        findsOneWidget,
      );
      expect(find.textContaining('viagem operacional'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });
}

void _expectPassengerStepper(
  WidgetTester tester, {
  required String current,
  required List<String> completed,
}) {
  for (final label in ['Conta', 'E-mail', 'Empresa', 'Serviços', 'Pronto']) {
    expect(find.text(label), findsOneWidget);
  }
  expect(
    find.byKey(ValueKey('passenger.onboarding.step.$current.current')),
    findsOneWidget,
  );
  for (final label in completed) {
    expect(
      find.byKey(ValueKey('passenger.onboarding.step.$label.completed')),
      findsOneWidget,
    );
  }
}
