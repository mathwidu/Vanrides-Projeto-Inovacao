import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/experience/experience_launch_screen.dart';

void main() {
  testWidgets('mostra selecao de experiencias quando for web', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: ExperienceLaunchScreen(isWebOverride: true)),
    );

    expect(find.text('Gestao'), findsOneWidget);
    expect(find.text('Operacao'), findsOneWidget);
    expect(find.text('Abrir gestao'), findsOneWidget);
    expect(find.text('Entrar no app'), findsOneWidget);
  });
}
