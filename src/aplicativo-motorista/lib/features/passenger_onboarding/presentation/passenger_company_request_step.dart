import 'package:flutter/material.dart';

import '../../../app/session/app_session_user.dart';
import '../../../l10n/app_strings.dart';
import '../../../onboarding_flow_components.dart';
import '../data/passenger_onboarding_service.dart';
import '../domain/passenger_onboarding_models.dart';
import 'passenger_company_flow_navigation.dart';
import 'widgets/passenger_company_link_widgets.dart';

class PassengerCompanyRequestScreen extends StatefulWidget {
  const PassengerCompanyRequestScreen({
    super.key,
    required this.sessionUser,
    required this.gateway,
    required this.company,
    this.onStatusNavigation = noopPassengerOnboardingStatusNavigation,
  });

  final AppSessionUser sessionUser;
  final PassengerOnboardingGateway gateway;
  final PassengerOnboardingCompanyRecord company;
  final PassengerOnboardingStatusNavigation onStatusNavigation;

  @override
  State<PassengerCompanyRequestScreen> createState() =>
      _PassengerCompanyRequestScreenState();
}

class _PassengerCompanyRequestScreenState
    extends State<PassengerCompanyRequestScreen> {
  bool _isSubmitting = false;

  Future<void> _submit() async {
    setState(() => _isSubmitting = true);
    try {
      final status = await widget.gateway.createRequest(
        widget.sessionUser,
        companyId: widget.company.id,
      );
      if (!mounted) {
        return;
      }
      await widget.onStatusNavigation(
        context,
        sessionUser: widget.sessionUser,
        gateway: widget.gateway,
        status: status,
      );
    } on PassengerOnboardingException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() => _isSubmitting = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final company = widget.company;
    return OnboardingScaffold(
      header: OnboardingHeader(
        onBack: () => Navigator.of(context).pop(),
        badgeText: AppStrings.passageiro.acessoCorporativoBadge,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          OnboardingSectionHeader(
            title: AppStrings.passageiro.solicitarAcessoTitulo,
            subtitle: AppStrings.passageiro.solicitarAcessoDescricao,
          ),
          const SizedBox(height: 18),
          const PassengerOnboardingProgressSteps(
            currentStep: PassengerOnboardingStep.company,
          ),
          const SizedBox(height: 28),
          PassengerCompanyReviewCard(company: company),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _isSubmitting ? null : _submit,
            icon:
                _isSubmitting
                    ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                    : const Icon(Icons.send_rounded),
            label: Text(
              _isSubmitting
                  ? AppStrings.passageiro.enviando
                  : AppStrings.passageiro.enviarSolicitacao,
            ),
          ),
        ],
      ),
    );
  }
}
