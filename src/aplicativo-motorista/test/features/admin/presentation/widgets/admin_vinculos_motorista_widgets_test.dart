import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_motorista_link_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_motorista_widgets.dart';

void main() {
  testWidgets('painel vazio orienta uso legado e aciona refresh', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var refreshCalled = false;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosPassageiroMotoristaPanel(
          links: const [],
          onRefresh: () async {
            refreshCalled = true;
          },
          onUnlink: (_) async {},
        ),
      ),
    );

    expect(find.text('Atalho motorista'), findsOneWidget);
    expect(find.text('LEGADO / APOIO OPERACIONAL'), findsOneWidget);
    expect(find.text('Nenhum atalho cadastrado'), findsOneWidget);
    expect(
      find.textContaining('serviços liberados mesmo sem motorista fixo'),
      findsOneWidget,
    );

    await tester.tap(find.text('Ver todos'));
    await tester.pump();

    expect(refreshCalled, isTrue);
  });

  testWidgets('painel com vínculo mostra dados e aciona desvinculo', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    AdminPassageiroMotoristaLinkRecord? unlinked;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosPassageiroMotoristaPanel(
          links: const [_link],
          onRefresh: () async {},
          onUnlink: (link) async {
            unlinked = link;
          },
        ),
      ),
    );

    expect(find.text('Aluno Portal'), findsOneWidget);
    expect(find.text('Passageiro Escolar'), findsOneWidget);
    expect(find.text('Motorista Portal'), findsOneWidget);
    expect(
      find.text('A viagem do dia define o motorista real'),
      findsOneWidget,
    );

    await tester.tap(find.byIcon(Icons.link_off_outlined));
    await tester.pump();

    expect(unlinked?.passageiroId, 21);
    expect(unlinked?.motorista?.id, 7);
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 900, child: child)),
    ),
  );
}

const _link = AdminPassageiroMotoristaLinkRecord(
  passageiroId: 21,
  passageiroNome: 'Aluno Portal',
  menorDeIdade: true,
  motorista: AdminMotoristaRecord(
    id: 7,
    nome: 'Motorista Portal',
    email: 'motorista@vanrides.dev',
    telefone: '51999999999',
  ),
);
