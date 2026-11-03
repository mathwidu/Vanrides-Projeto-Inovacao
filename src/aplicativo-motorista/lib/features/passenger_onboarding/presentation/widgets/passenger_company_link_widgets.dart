import 'package:flutter/material.dart';

import '../../../../l10n/app_strings.dart';
import '../../../../onboarding_flow_components.dart';
import '../../domain/passenger_onboarding_models.dart';

enum PassengerOnboardingStep {
  account('Conta', Icons.person_outline_rounded),
  email('E-mail', Icons.mark_email_read_outlined),
  company('Empresa', Icons.apartment_rounded),
  services('Serviços', Icons.route_rounded),
  ready('Pronto', Icons.check_rounded);

  const PassengerOnboardingStep(this.label, this.icon);

  final String label;
  final IconData icon;
}

class PassengerOnboardingProgressSteps extends StatelessWidget {
  const PassengerOnboardingProgressSteps({
    super.key,
    required this.currentStep,
  });

  final PassengerOnboardingStep currentStep;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: PassengerOnboardingStep.values
          .map((step) {
            final completed = step.index < currentStep.index;
            final current = step == currentStep;
            final background =
                current
                    ? OnboardingPalette.passenger
                    : completed
                    ? const Color(0xFFA0F399).withValues(alpha: 0.2)
                    : OnboardingPalette.surface;
            final foreground =
                current
                    ? Colors.white
                    : completed
                    ? const Color(0xFFA0F399)
                    : OnboardingPalette.textMuted;
            final border =
                current
                    ? OnboardingPalette.passenger.withValues(alpha: 0.64)
                    : completed
                    ? const Color(0xFFA0F399).withValues(alpha: 0.38)
                    : OnboardingPalette.border.withValues(alpha: 0.42);
            final stateName =
                current
                    ? 'current'
                    : completed
                    ? 'completed'
                    : 'pending';

            return Semantics(
              label: 'Etapa ${step.label}: $stateName',
              child: Container(
                key: ValueKey(
                  'passenger.onboarding.step.${step.label}.$stateName',
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 9,
                ),
                decoration: BoxDecoration(
                  color: background,
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: border),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      completed ? Icons.check_rounded : step.icon,
                      size: 16,
                      color: foreground,
                    ),
                    const SizedBox(width: 7),
                    Text(
                      step.label,
                      style: TextStyle(
                        color: foreground,
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
            );
          })
          .toList(growable: false),
    );
  }
}

class PassengerCompanySearchCard extends StatelessWidget {
  const PassengerCompanySearchCard({
    super.key,
    required this.company,
    required this.onTap,
  });

  final PassengerOnboardingCompanyRecord company;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(24),
      child: Ink(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: OnboardingPalette.surface,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: OnboardingPalette.border.withValues(alpha: 0.45),
          ),
          boxShadow: const [
            BoxShadow(
              color: Color(0x3310161F),
              blurRadius: 24,
              offset: Offset(0, 14),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: OnboardingPalette.passengerSoft,
                borderRadius: BorderRadius.circular(18),
              ),
              child: const Icon(
                Icons.business_rounded,
                color: OnboardingPalette.passenger,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    company.nome,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: OnboardingPalette.textStrong,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    company.slug,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: OnboardingPalette.textMuted,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(
              Icons.arrow_forward_rounded,
              color: OnboardingPalette.passenger,
            ),
          ],
        ),
      ),
    );
  }
}

class PassengerOnboardingHighlightCard extends StatelessWidget {
  const PassengerOnboardingHighlightCard({
    super.key,
    required this.title,
    required this.body,
    required this.accentColor,
    required this.icon,
  });

  final String title;
  final String body;
  final Color accentColor;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: OnboardingPalette.surface,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: accentColor.withValues(alpha: 0.22)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: accentColor.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(icon, color: accentColor),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: OnboardingPalette.textStrong,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  body,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: OnboardingPalette.textMuted,
                    height: 1.5,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class PassengerCompanyReviewCard extends StatelessWidget {
  const PassengerCompanyReviewCard({super.key, required this.company});

  final PassengerOnboardingCompanyRecord company;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: OnboardingPalette.surface,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: OnboardingPalette.border.withValues(alpha: 0.45),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x3310161F),
            blurRadius: 28,
            offset: Offset(0, 16),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(
                  color: OnboardingPalette.passengerSoft,
                  borderRadius: BorderRadius.circular(18),
                ),
                child: const Icon(
                  Icons.apartment_rounded,
                  color: OnboardingPalette.passenger,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      company.nome,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: OnboardingPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      company.slug,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: OnboardingPalette.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFFA0F399),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  AppStrings.passageiro.empresaVerificada,
                  style: const TextStyle(
                    color: Color(0xFF217128),
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 1,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Divider(color: OnboardingPalette.border.withValues(alpha: 0.45)),
          const SizedBox(height: 18),
          PassengerOnboardingHighlightCard(
            title: AppStrings.passageiro.processoDeRevisaoTitulo,
            body: AppStrings.passageiro.processoDeRevisaoDescricao,
            accentColor: OnboardingPalette.passenger,
            icon: Icons.assignment_turned_in_outlined,
          ),
        ],
      ),
    );
  }
}
