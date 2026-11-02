import 'package:flutter/material.dart';

import '../../../app/session/app_session_user.dart';
import '../../../l10n/app_strings.dart';
import '../../../onboarding_flow_components.dart';
import '../data/passenger_onboarding_service.dart';
import '../domain/passenger_onboarding_models.dart';
import 'passenger_company_flow_navigation.dart';
import 'widgets/passenger_company_link_widgets.dart';

class PassengerInviteCodeScreen extends StatefulWidget {
  const PassengerInviteCodeScreen({
    super.key,
    required this.sessionUser,
    this.gateway,
    this.initialCode,
    this.onStatusNavigation = noopPassengerOnboardingStatusNavigation,
  });

  final AppSessionUser sessionUser;
  final PassengerOnboardingGateway? gateway;
  final String? initialCode;
  final PassengerOnboardingStatusNavigation onStatusNavigation;

  @override
  State<PassengerInviteCodeScreen> createState() =>
      _PassengerInviteCodeScreenState();
}

class _PassengerInviteCodeScreenState extends State<PassengerInviteCodeScreen> {
  final _codeController = TextEditingController();
  late final PassengerOnboardingGateway _gateway;
  bool _isSubmitting = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _gateway = widget.gateway ?? PassengerOnboardingService();
    if (widget.initialCode != null) {
      _codeController.text = widget.initialCode!;
    }
  }

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final status = await _gateway.redeemInviteCode(
        widget.sessionUser,
        code: _codeController.text,
      );
      if (!mounted) {
        return;
      }
      await widget.onStatusNavigation(
        context,
        sessionUser: widget.sessionUser,
        gateway: _gateway,
        status: status,
      );
    } on PassengerOnboardingException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _isSubmitting = false;
        _errorMessage = error.message;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      header: OnboardingHeader(
        onBack: () => Navigator.of(context).pop(),
        badgeText: AppStrings.passageiro.codigoCorporativoBadge,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          OnboardingSectionHeader(
            title: AppStrings.passageiro.inserirCodigoTitulo,
            subtitle: AppStrings.passageiro.inserirCodigoDescricao,
          ),
          const SizedBox(height: 18),
          const PassengerOnboardingProgressSteps(
            currentStep: PassengerOnboardingStep.company,
          ),
          const SizedBox(height: 28),
          TextField(
            controller: _codeController,
            textCapitalization: TextCapitalization.characters,
            decoration: onboardingInputDecoration(
              hintText: AppStrings.passageiro.codigoCorporativoHint,
              prefixIcon: const Icon(Icons.qr_code_rounded),
            ),
          ),
          const SizedBox(height: 20),
          if (_errorMessage != null) ...[
            PassengerOnboardingHighlightCard(
              title: AppStrings.passageiro.codigoInvalidoOuExpirado,
              body: _errorMessage!,
              accentColor: const Color(0xFFBA1A1A),
              icon: Icons.report_gmailerrorred_rounded,
            ),
            const SizedBox(height: 16),
          ],
          FilledButton.icon(
            onPressed: _isSubmitting ? null : _submit,
            icon:
                _isSubmitting
                    ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                    : const Icon(Icons.verified_rounded),
            label: Text(
              _isSubmitting
                  ? AppStrings.passageiro.validando
                  : AppStrings.passageiro.validarCodigo,
            ),
          ),
        ],
      ),
    );
  }
}
