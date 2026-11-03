import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/domain/passenger_onboarding_models.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

void main() {
  Future<void> pumpWidget(WidgetTester tester, Widget child) async {
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: child)));
  }

  group('PassengerCompanySearchCard', () {
    testWidgets('renderiza empresa e dispara selecao', (tester) async {
      var selected = false;

      await pumpWidget(
        tester,
        PassengerCompanySearchCard(
          company: const PassengerOnboardingCompanyRecord(
            id: 7,
            nome: 'Empresa Horizonte',
            slug: 'empresa-horizonte',
          ),
          onTap: () => selected = true,
        ),
      );

      expect(find.text('Empresa Horizonte'), findsOneWidget);
      expect(find.text('empresa-horizonte'), findsOneWidget);
      expect(find.byIcon(Icons.business_rounded), findsOneWidget);

      await tester.tap(find.byType(PassengerCompanySearchCard));

      expect(selected, isTrue);
      expect(tester.takeException(), isNull);
    });
  });

  group('PassengerOnboardingHighlightCard', () {
    testWidgets('renderiza conteudo, icone e cor de destaque', (tester) async {
      const accentColor = Color(0xFF1B6D24);

      await pumpWidget(
        tester,
        const PassengerOnboardingHighlightCard(
          title: 'Próximo passo operacional',
          body: 'A empresa ainda precisa vincular um motorista à sua viagem.',
          accentColor: accentColor,
          icon: Icons.route_rounded,
        ),
      );

      final icon = tester.widget<Icon>(find.byIcon(Icons.route_rounded));

      expect(find.text('Próximo passo operacional'), findsOneWidget);
      expect(
        find.text(
          'A empresa ainda precisa vincular um motorista à sua viagem.',
        ),
        findsOneWidget,
      );
      expect(icon.color, accentColor);
      expect(tester.takeException(), isNull);
    });
  });

  group('PassengerCompanyReviewCard', () {
    testWidgets('renderiza empresa verificada e processo de revisao', (
      tester,
    ) async {
      await pumpWidget(
        tester,
        const PassengerCompanyReviewCard(
          company: PassengerOnboardingCompanyRecord(
            id: 9,
            nome: 'Empresa Aurora',
            slug: 'empresa-aurora',
          ),
        ),
      );

      expect(find.text('Empresa Aurora'), findsOneWidget);
      expect(find.text('empresa-aurora'), findsOneWidget);
      expect(
        find.text(AppStrings.passageiro.empresaVerificada),
        findsOneWidget,
      );
      expect(
        find.text(AppStrings.passageiro.processoDeRevisaoTitulo),
        findsOneWidget,
      );
      expect(
        find.text(AppStrings.passageiro.processoDeRevisaoDescricao),
        findsOneWidget,
      );
      expect(find.byIcon(Icons.apartment_rounded), findsOneWidget);
      expect(find.byIcon(Icons.assignment_turned_in_outlined), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });
}
