import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/cadastro_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/presentation/cadastro_passageiro_screen.dart';

void main() {
  testWidgets('cadastro de passageiro orienta etapas e pede nascimento', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: CadastroPassageiroScreen()),
    );

    expect(
      find.byKey(CadastroPassageiroScreen.dataNascimentoFieldKey),
      findsOneWidget,
    );
    expect(find.text('Dados pessoais'), findsOneWidget);
    expect(find.text('Sua parada'), findsOneWidget);
    expect(find.textContaining('endereço da parada'), findsOneWidget);
    expect(find.textContaining('ponto no mapa'), findsWidgets);
    expect(find.textContaining('latitude'), findsNothing);
    expect(find.textContaining('longitude'), findsNothing);
    expect(find.textContaining('coordenada'), findsNothing);
    expect(
      find.byKey(CadastroPassageiroScreen.cadastrarButtonKey),
      findsOneWidget,
    );
  });

  testWidgets('cadastro de passageiro valida data de nascimento obrigatoria', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: CadastroPassageiroScreen()),
    );

    await tester.tap(find.byKey(CadastroPassageiroScreen.cadastrarButtonKey));
    await tester.pump();

    expect(find.text('Data de nascimento obrigatória'), findsOneWidget);
  });

  testWidgets('cadastro de motorista orienta etapas e pede nascimento', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: CadastroMotoristaScreen()));

    expect(
      find.byKey(CadastroMotoristaScreen.dataNascimentoFieldKey),
      findsOneWidget,
    );
    expect(find.text('Dados profissionais'), findsOneWidget);
    expect(find.text('Acesso'), findsOneWidget);
    expect(find.text('Empresa'), findsOneWidget);
    expect(find.text('Verificação'), findsOneWidget);
    expect(
      find.byKey(CadastroMotoristaScreen.cadastrarButtonKey),
      findsOneWidget,
    );
  });

  testWidgets('cadastro de motorista valida data de nascimento obrigatoria', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: CadastroMotoristaScreen()));

    await tester.tap(find.byKey(CadastroMotoristaScreen.cadastrarButtonKey));
    await tester.pump();

    expect(find.text('Data de nascimento obrigatória'), findsOneWidget);
  });
}
