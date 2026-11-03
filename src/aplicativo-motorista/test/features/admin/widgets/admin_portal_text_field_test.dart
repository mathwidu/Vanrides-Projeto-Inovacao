import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_portal_text_field.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  testWidgets('exibe asterisco no label quando required é true', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(const AdminPortalTextField(label: 'Nome', required: true)),
    );

    expect(find.text('Nome *'), findsOneWidget);
  });

  testWidgets('exibe label sem asterisco quando required é false', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(const AdminPortalTextField(label: 'Observação')),
    );

    expect(find.text('Observação'), findsOneWidget);
    expect(find.textContaining('*'), findsNothing);
  });

  testWidgets('exibe erro de validação ao interagir com o campo', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        Form(
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: AdminPortalTextField(
            label: 'Nome',
            required: true,
            validator:
                (value) =>
                    (value == null || value.isEmpty) ? 'Obrigatório' : null,
          ),
        ),
      ),
    );

    await tester.enterText(find.byType(TextFormField), 'abc');
    await tester.enterText(find.byType(TextFormField), '');
    await tester.pump();

    expect(find.text('Obrigatório'), findsOneWidget);
  });
}
