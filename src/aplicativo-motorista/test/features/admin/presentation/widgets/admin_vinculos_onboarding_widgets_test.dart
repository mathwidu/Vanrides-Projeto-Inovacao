import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_link_flow_models.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_onboarding_widgets.dart';

void main() {
  testWidgets('renderiza solicitacoes e aciona callbacks', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    var refreshed = false;
    AdminAccessRequestRecord? openedRequest;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosAccessRequestsPanel(
          requests: [_accessRequest],
          onRefresh: () async {
            refreshed = true;
          },
          onOpenRequest: (request) async {
            openedRequest = request;
          },
        ),
      ),
    );

    expect(find.text('Solicitações de acesso'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsOneWidget);
    expect(find.text('aluno@vanrides.dev'), findsOneWidget);
    expect(find.text('Solicitação'), findsOneWidget);
    expect(find.text('E-mail verificado'), findsOneWidget);
    expect(find.text('15/04/2026 10:30'), findsOneWidget);

    await tester.tap(find.byTooltip('Atualizar solicitações'));
    await tester.pump();
    expect(refreshed, isTrue);

    await tester.tap(find.text('Aluno Portal'));
    await tester.pump();
    expect(openedRequest?.id, 41);
  });

  testWidgets('renderiza convites e aciona criacao e abertura', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    var created = false;
    AdminInviteCodeRecord? openedInvite;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosInviteCodesPanel(
          inviteCodes: [
            _activeInvite,
            const AdminInviteCodeRecord(
              id: 52,
              code: 'VAN-USED',
              status: 'USED_UP',
              useCount: 1,
              maxUses: 1,
            ),
          ],
          onCreateInvite: () async {
            created = true;
          },
          onOpenInvite: (invite) async {
            openedInvite = invite;
          },
        ),
      ),
    );

    expect(find.text('Códigos de convite'), findsOneWidget);
    expect(find.text('VAN-2026'), findsOneWidget);
    expect(find.text('Ativo'), findsOneWidget);
    expect(find.text('Expira em 16/04/2026 08:00'), findsOneWidget);
    expect(find.text('ATIVOS'), findsOneWidget);
    expect(find.text('A EXPIRAR'), findsOneWidget);
    expect(find.text('UTILIZADOS'), findsOneWidget);

    await tester.tap(find.text('Novo convite'));
    await tester.pump();
    expect(created, isTrue);

    await tester.tap(find.text('VAN-2026'));
    await tester.pump();
    expect(openedInvite?.id, 51);
  });

  testWidgets('dialogo de detalhe retorna acao de copiar codigo', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    AdminVinculosInviteDetailAction? selectedAction;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            return Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () async {
                    selectedAction =
                        await showDialog<AdminVinculosInviteDetailAction>(
                          context: context,
                          builder:
                              (context) => AdminVinculosInviteCodeDetailDialog(
                                detail: _inviteDetail,
                              ),
                        );
                  },
                  child: const Text('Abrir convite'),
                ),
              ),
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('Abrir convite'));
    await tester.pumpAndSettle();

    expect(find.text('Detalhes do convite'), findsOneWidget);
    expect(find.text('VAN-2026'), findsOneWidget);
    expect(find.text('1/3 usos'), findsOneWidget);
    expect(find.text('Aluno Portal • aluno@vanrides.dev'), findsOneWidget);

    await tester.tap(find.text('Copiar código'));
    await tester.pumpAndSettle();
    expect(selectedAction, AdminVinculosInviteDetailAction.copyCode);
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 1200, child: child)),
    ),
  );
}

final _requestedAt = DateTime(2026, 4, 15, 10, 30);
final _expiresAt = DateTime(2026, 4, 16, 8);
final _lastUsedAt = DateTime(2026, 4, 15, 9);

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

final _inviteDetail = AdminInviteCodeDetailRecord(
  id: 51,
  code: 'VAN-2026',
  status: 'ACTIVE',
  useCount: 1,
  maxUses: 3,
  expiresAt: _expiresAt,
  lastUsedAt: _lastUsedAt,
  usages: const [
    AdminInviteCodeUsageRecord(
      id: 91,
      passageiroId: 11,
      passageiroNome: 'Aluno Portal',
      passageiroEmail: 'aluno@vanrides.dev',
      emailStatus: 'VERIFIED',
    ),
  ],
);
