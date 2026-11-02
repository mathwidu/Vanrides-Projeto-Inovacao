import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_link_flow_models.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_linked_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_passageiro_link_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_task_queue_widgets.dart';

void main() {
  testWidgets('task queue prioriza proxima acao antes dos contadores', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1180, 900));
    var openedRequest = false;
    var createdInvite = false;
    var createdGuardianLink = false;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosTaskQueuePanel(
          accessRequests: [_accessRequest],
          inviteCodes: [_activeInvite],
          responsavelPassageiroLinks: const [_guardianLink],
          passageiros: const [_passageiro],
          responsaveis: const [_responsavel],
          onOpenFirstRequest: () async => openedRequest = true,
          onCreateInvite: () async => createdInvite = true,
          onCreateGuardianLink: () async => createdGuardianLink = true,
        ),
      ),
    );

    expect(find.text('Fila de onboarding'), findsOneWidget);
    expect(find.text('Próxima tarefa'), findsOneWidget);
    expect(find.text('Revisar 1 solicitação pendente'), findsOneWidget);
    expect(find.text('Criar convite assistido'), findsOneWidget);
    expect(find.text('1 vínculo familiar ativo'), findsOneWidget);
    expect(find.text('Contadores operacionais'), findsOneWidget);

    await tester.tap(find.text('Revisar solicitação'));
    await tester.pump();
    expect(openedRequest, isTrue);

    await tester.tap(find.text('Novo convite'));
    await tester.pump();
    expect(createdInvite, isTrue);

    await tester.tap(find.text('Vincular responsável'));
    await tester.pump();
    expect(createdGuardianLink, isTrue);
  });

  testWidgets('task queue vazia guia criacao do primeiro convite', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1180, 900));
    var createdInvite = false;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosTaskQueuePanel(
          accessRequests: const [],
          inviteCodes: const [],
          responsavelPassageiroLinks: const [],
          passageiros: const [],
          responsaveis: const [],
          onOpenFirstRequest: () async {},
          onCreateInvite: () async => createdInvite = true,
          onCreateGuardianLink: () async {},
        ),
      ),
    );

    expect(find.text('Onboarding em dia'), findsOneWidget);
    expect(find.text('Nenhuma solicitação pendente agora.'), findsOneWidget);
    expect(find.text('Criar primeiro convite'), findsOneWidget);
    expect(find.text('0 solicitações'), findsOneWidget);
    expect(find.text('0 convites ativos'), findsOneWidget);

    await tester.tap(find.text('Criar primeiro convite'));
    await tester.pump();
    expect(createdInvite, isTrue);
  });

  testWidgets('loading state explica que a fila esta sendo montada', (
    tester,
  ) async {
    await tester.pumpWidget(_wrap(const AdminVinculosTaskQueueLoadingState()));

    expect(find.text('Carregando fila de onboarding'), findsOneWidget);
    expect(
      find.text('Buscando solicitações, convites e vínculos da empresa.'),
      findsOneWidget,
    );
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 1180, child: child)),
    ),
  );
}

final _requestedAt = DateTime(2026, 4, 15, 10, 30);
final _expiresAt = DateTime(2026, 4, 16, 8);

final _accessRequest = AdminAccessRequestRecord(
  id: 41,
  passageiroId: 11,
  passageiroNome: 'Aluno Portal',
  passageiroEmail: 'aluno@vanrides.dev',
  companyName: 'Portal Empresa',
  source: 'REQUEST',
  emailVerified: true,
  requestedAt: _requestedAt,
);

final _activeInvite = AdminInviteCodeRecord(
  id: 51,
  code: 'VAN-2026',
  status: 'ACTIVE',
  useCount: 0,
  maxUses: 3,
  expiresAt: _expiresAt,
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
  nome: 'Responsável Portal',
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
