import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_linked_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_passageiro_link_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_guardian_widgets.dart';

void main() {
  testWidgets('painel renderiza estado vazio e aciona criacao', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var createCalled = false;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosResponsavelPassageiroPanel(
          links: const [],
          onCreateLink: () async {
            createCalled = true;
          },
          onUnlink: (_) async {},
        ),
      ),
    );

    expect(find.text('Custódia responsável-passageiro'), findsOneWidget);
    expect(find.text('Nenhum vínculo responsável cadastrado'), findsOneWidget);

    await tester.tap(find.text('Ajustar'));
    await tester.pump();
    expect(createCalled, isTrue);
  });

  testWidgets('painel renderiza vinculo e aciona remocao', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    AdminResponsavelPassageiroLinkRecord? removedLink;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosResponsavelPassageiroPanel(
          links: const [_link],
          onCreateLink: () async {},
          onUnlink: (link) async {
            removedLink = link;
          },
        ),
      ),
    );

    expect(find.text('Responsavel Portal'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsOneWidget);
    expect(find.text('RESPONSÁVEL PRINCIPAL'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remover vínculo'));
    await tester.pumpAndSettle();

    expect(removedLink?.responsavel.id, 31);
    expect(removedLink?.passageiro.id, 21);
  });

  testWidgets('dialogo retorna ids selecionados', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    AdminVinculosResponsavelPassageiroSelection? selected;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            return Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () async {
                    selected = await showDialog<
                      AdminVinculosResponsavelPassageiroSelection
                    >(
                      context: context,
                      builder:
                          (context) =>
                              const AdminVinculosResponsavelPassageiroDialog(
                                responsaveis: [_responsavel, _responsavel2],
                                passageiros: [_passageiro, _passageiro2],
                              ),
                    );
                  },
                  child: const Text('Abrir vínculo'),
                ),
              ),
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('Abrir vínculo'));
    await tester.pumpAndSettle();

    expect(find.text('Novo vínculo responsável-passageiro'), findsOneWidget);

    await tester.tap(find.byType(DropdownButtonFormField<int>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Responsavel Alternativo').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byType(DropdownButtonFormField<int>).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Aluno Alternativo').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Criar vínculo'));
    await tester.pumpAndSettle();

    expect(selected?.responsavelId, 32);
    expect(selected?.passageiroId, 22);
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 900, child: child)),
    ),
  );
}

const _responsavel = AdminResponsavelRecord(
  id: 31,
  nome: 'Responsavel Portal',
  email: 'responsavel@vanrides.dev',
  telefone: '51999999999',
);

const _responsavel2 = AdminResponsavelRecord(
  id: 32,
  nome: 'Responsavel Alternativo',
  email: 'alt@vanrides.dev',
  telefone: '51888888888',
);

const _passageiro = AdminPassageiroRecord(
  id: 21,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51977777777',
  menorDeIdade: true,
);

const _passageiro2 = AdminPassageiroRecord(
  id: 22,
  nome: 'Aluno Alternativo',
  email: 'aluno.alt@vanrides.dev',
  telefone: '51966666666',
  menorDeIdade: false,
);

const _link = AdminResponsavelPassageiroLinkRecord(
  responsavel: _responsavel,
  passageiro: AdminPassageiroLinkedRecord(
    id: 21,
    nome: 'Aluno Portal',
    email: 'aluno@vanrides.dev',
    menorDeIdade: true,
  ),
);
