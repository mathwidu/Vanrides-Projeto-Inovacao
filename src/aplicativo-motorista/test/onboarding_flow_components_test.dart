import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/onboarding_flow_components.dart';

void main() {
  testWidgets('onboarding foundation usa dark system em card e input', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: OnboardingScaffold(
          header: const SizedBox.shrink(),
          child: Column(
            children: [
              OnboardingRoleCard(
                cardKey: const Key('onboarding.role.passenger'),
                title: 'Sou passageiro',
                subtitle: 'Acompanhe sua viagem.',
                icon: Icons.person_outline_rounded,
                iconColor: OnboardingPalette.passenger,
                iconBackground: OnboardingPalette.passengerSoft,
                onTap: () {},
              ),
              const SizedBox(height: 16),
              TextFormField(
                decoration: onboardingInputDecoration(hintText: 'E-mail'),
              ),
            ],
          ),
        ),
      ),
    );

    final scaffold = tester.widget<Scaffold>(find.byType(Scaffold));
    expect(scaffold.backgroundColor, VanRidesDesignTokens.backgroundDeep);

    final roleInk = tester.widget<Ink>(
      find
          .descendant(
            of: find.byKey(const Key('onboarding.role.passenger')),
            matching: find.byType(Ink),
          )
          .first,
    );
    final roleDecoration = roleInk.decoration! as BoxDecoration;
    expect(roleDecoration.color, VanRidesDesignTokens.surface);
    expect(roleDecoration.color, isNot(Colors.white));

    await tester.enterText(find.byType(TextFormField), 'ana@vanrides.dev');
    await tester.pump();

    final editableText = tester.widget<EditableText>(find.byType(EditableText));
    expect(editableText.style.color, VanRidesDesignTokens.textPrimary);
  });
}
