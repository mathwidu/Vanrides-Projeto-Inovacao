import 'package:flutter/material.dart';

import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/cadastro_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/presentation/cadastro_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/onboarding_flow_components.dart';

class EscolhaCadastroScreen extends StatelessWidget {
  const EscolhaCadastroScreen({super.key, this.initialDriverInviteToken});

  static const screenKey = Key('cadastro.escolha.screen');
  static const souPassageiroButtonKey = Key('cadastro.escolha.passageiro');
  static const souMotoristaButtonKey = Key('cadastro.escolha.motorista');

  final String? initialDriverInviteToken;

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      key: screenKey,
      header: OnboardingHeader(
        onBack: () => Navigator.maybePop(context),
        brandColor: OnboardingPalette.textStrong,
      ),
      topPadding: 40,
      blurDots: const [
        OnboardingBlurDot(
          color: OnboardingPalette.passenger,
          size: 256,
          right: -48,
          top: -80,
        ),
        OnboardingBlurDot(
          color: OnboardingPalette.driver,
          size: 288,
          left: -64,
          bottom: 180,
        ),
      ],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const OnboardingSectionHeader(
            title: 'Escolha seu perfil',
            subtitle:
                'Selecione a experiência que combina com a sua jornada dentro do VanRides.',
          ),
          const SizedBox(height: 48),
          OnboardingRoleCard(
            cardKey: souPassageiroButtonKey,
            title: 'Sou passageiro',
            subtitle: 'Acompanhe sua viagem, rota e horário com clareza.',
            icon: Icons.person_outline_rounded,
            iconColor: OnboardingPalette.passenger,
            iconBackground: OnboardingPalette.passengerSoft,
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const CadastroPassageiroScreen(),
                ),
              );
            },
          ),
          const SizedBox(height: 16),
          OnboardingRoleCard(
            cardKey: souMotoristaButtonKey,
            title: 'Sou motorista',
            subtitle:
                'Organize a operação do dia e conduza a rota com suporte.',
            icon: Icons.directions_bus_outlined,
            iconColor: OnboardingPalette.driver,
            iconBackground: OnboardingPalette.driverSoft,
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder:
                      (context) => CadastroMotoristaScreen(
                        initialInviteToken: initialDriverInviteToken,
                      ),
                ),
              );
            },
          ),
          const SizedBox(height: 220),
          OnboardingFooterPrompt(
            prefix: 'Já tem uma conta?',
            actionLabel: 'Entrar',
            onTap: () => Navigator.maybePop(context),
          ),
        ],
      ),
    );
  }
}
