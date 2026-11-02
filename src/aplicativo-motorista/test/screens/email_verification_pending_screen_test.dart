import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';

void main() {
  testWidgets(
    'renderiza variante de login bloqueado com CTA de tentar novamente',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: EmailVerificationPendingScreen(
            email: 'qa@example.invalid',
            variant: EmailVerificationPendingVariant.loginBlocked,
          ),
        ),
      );

      expect(
        find.byKey(EmailVerificationPendingScreen.screenKey),
        findsOneWidget,
      );
      expect(find.text('Seu e-mail ainda não foi confirmado'), findsOneWidget);
      expect(find.text('qa@example.invalid'), findsOneWidget);
      expect(find.text('Já confirmei, tentar novamente'), findsOneWidget);
      expect(find.text('Suporte VanRides'), findsOneWidget);
    },
  );

  testWidgets('renderiza variante de cadastro de passageiro com CTA de login', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: EmailVerificationPendingScreen(
          email: 'passageiro@example.com',
          variant: EmailVerificationPendingVariant.passengerSignup,
        ),
      ),
    );

    expect(find.text('Confira seu e-mail'), findsOneWidget);
    expect(find.text('Ir para login'), findsOneWidget);
    expect(find.text('Editar e-mail depois'), findsOneWidget);
    expect(
      find.text('Depois de confirmar, volte ao app para entrar.'),
      findsOneWidget,
    );
  });

  testWidgets('inicia cooldown visual apos envio inicial do cadastro', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: EmailVerificationPendingScreen(
          email: 'passageiro@example.com',
          variant: EmailVerificationPendingVariant.passengerSignup,
          initialResendCooldown: Duration(minutes: 15),
        ),
      ),
    );

    expect(find.text('Reenviar em 15:00'), findsOneWidget);
    expect(
      find.text('Você poderá solicitar um novo envio em 15:00.'),
      findsOneWidget,
    );
  });
}
