import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_link_flow_models.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_linked_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_passageiro_link_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_task_queue_widgets.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/widgets/driver_navigation_panel.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/service_presence_decision_list.dart';

void main() {
  testWidgets(
    'driver navigation exposes an accessible region under assistive settings',
    (tester) async {
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          _accessibleApp(
            DriverNavigationPanel(
              viewModel: _navigationViewModel(),
              onPrimaryAction: () {},
              onFallback: () {},
              onRepeatInstruction: () {},
              onToggleVoiceMute: () {},
            ),
          ),
        );

        expect(find.bySemanticsLabel('Navegacao do motorista'), findsOneWidget);
        expect(find.byTooltip('Repetir instrucao'), findsOneWidget);
        expect(find.byTooltip('Silenciar voz'), findsOneWidget);
        expect(find.text('Vire a direita na Av. Brasil'), findsOneWidget);
        expect(tester.takeException(), isNull);
      } finally {
        semantics.dispose();
      }
    },
  );

  testWidgets(
    'admin onboarding queue exposes a region and actions with large text',
    (tester) async {
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          _accessibleApp(
            AdminVinculosTaskQueuePanel(
              accessRequests: [_accessRequest],
              inviteCodes: [_activeInvite],
              responsavelPassageiroLinks: const [_guardianLink],
              passageiros: const [_passageiro],
              responsaveis: const [_responsavel],
              onOpenFirstRequest: () async {},
              onCreateInvite: () async {},
              onCreateGuardianLink: () async {},
            ),
            width: 360,
          ),
        );

        expect(
          find.bySemanticsLabel('Fila de onboarding administrativo'),
          findsOneWidget,
        );
        expect(find.text('Revisar solicitação'), findsOneWidget);
        expect(find.text('Novo convite'), findsOneWidget);
        expect(find.text('Vincular responsável'), findsOneWidget);
        expect(tester.takeException(), isNull);
      } finally {
        semantics.dispose();
      }
    },
  );

  testWidgets(
    'admin onboarding loading state explains progress to assistive tech',
    (tester) async {
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          _accessibleApp(
            const AdminVinculosTaskQueueLoadingState(),
            width: 360,
          ),
        );

        expect(
          find.bySemanticsLabel('Carregando fila de onboarding administrativo'),
          findsOneWidget,
        );
        expect(find.byType(CircularProgressIndicator), findsOneWidget);
        expect(tester.takeException(), isNull);
      } finally {
        semantics.dispose();
      }
    },
  );

  testWidgets(
    'guardian service decisions keep semantic labels with large text',
    (tester) async {
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          _accessibleApp(
            ServicePresenceDecisionList(
              services: [
                _service(
                  id: 15,
                  nome: 'Linha Manha',
                  tipo: 'IDA',
                  horario: '07:30',
                  status: 'UNKNOWN',
                ),
              ],
              processing: false,
              onConfirm: (_) {},
              onAbsent: (_) {},
              onClear: (_) {},
            ),
            width: 320,
          ),
        );

        expect(
          find.bySemanticsLabel(
            '07:30 Ida, Linha Manha, Pendente, Escola Central',
          ),
          findsOneWidget,
        );
        expect(find.text('Vou'), findsOneWidget);
        expect(find.text('Não vou'), findsOneWidget);
        expect(tester.takeException(), isNull);
      } finally {
        semantics.dispose();
      }
    },
  );
}

Widget _accessibleApp(Widget child, {double width = 390}) {
  return MaterialApp(
    home: MediaQuery(
      data: const MediaQueryData(
        textScaler: TextScaler.linear(1.6),
        accessibleNavigation: true,
        disableAnimations: true,
        highContrast: true,
      ),
      child: Scaffold(
        body: SingleChildScrollView(
          child: Align(
            alignment: Alignment.topCenter,
            child: SizedBox(width: width, child: child),
          ),
        ),
      ),
    ),
  );
}

DriverNavigationViewModel _navigationViewModel() {
  return const DriverNavigationViewModel(
    mode: DriverNavigationMode.internal,
    instructionText: 'Vire a direita na Av. Brasil',
    distanceLabel: 'Em 300 m',
    helperText: 'Siga com atencao ate a proxima parada.',
    nextStopLabel: 'Proxima parada',
    nextStopName: 'Ana Clara',
    nextStopSummary: 'Parada 1 - Linha Manha',
    primaryActionLabel: 'Cheguei',
    fallbackLabel: 'Fallback',
    statusLabel: 'Offline pronto',
    voicePrompt: 'Em 300 metros, vire a direita na Avenida Brasil',
    fallbackIsPrimary: false,
    canUseInternalNavigation: true,
  );
}

PassengerAvailableServiceRecord _service({
  required int id,
  required String nome,
  required String tipo,
  required String horario,
  required String status,
  int? presencaId,
}) {
  return PassengerAvailableServiceRecord(
    servicoId: id,
    nome: nome,
    tipoOperacao: tipo,
    dataOperacao: DateTime(2026, 5, 25),
    horarioPartida: horario,
    destinoFinalNome: 'Escola Central',
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    presencaStatus: status,
    presencaId: presencaId,
  );
}

final _accessRequestedAt = DateTime(2026, 4, 15, 10, 30);
final _inviteExpiresAt = DateTime(2026, 4, 16, 8);

final _accessRequest = AdminAccessRequestRecord(
  id: 41,
  passageiroId: 11,
  passageiroNome: 'Aluno Portal',
  passageiroEmail: 'aluno@vanrides.dev',
  companyName: 'Portal Empresa',
  source: 'REQUEST',
  emailVerified: true,
  requestedAt: _accessRequestedAt,
);

final _activeInvite = AdminInviteCodeRecord(
  id: 51,
  code: 'VAN-2026',
  status: 'ACTIVE',
  useCount: 0,
  maxUses: 3,
  expiresAt: _inviteExpiresAt,
);

const _passageiro = AdminPassageiroRecord(
  id: 11,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51999999998',
  menorDeIdade: true,
);

const _responsavel = AdminResponsavelRecord(
  id: 7,
  nome: 'Responsavel Portal',
  email: 'responsavel@vanrides.dev',
  telefone: '51999999997',
);

const _guardianLink = AdminResponsavelPassageiroLinkRecord(
  responsavel: _responsavel,
  passageiro: AdminPassageiroLinkedRecord(
    id: 11,
    nome: 'Aluno Portal',
    email: 'aluno@vanrides.dev',
    menorDeIdade: true,
  ),
);
