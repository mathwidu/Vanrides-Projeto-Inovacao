import 'package:flutter/material.dart';

import '../../../app/session/app_session_user.dart';
import '../../../l10n/app_strings.dart';
import '../../../onboarding_flow_components.dart';
import '../data/passenger_onboarding_service.dart';
import '../domain/passenger_onboarding_models.dart';
import 'passenger_company_flow_navigation.dart';
import 'passenger_company_request_step.dart';
import 'widgets/passenger_company_link_widgets.dart';

class PassengerCompanySearchScreen extends StatefulWidget {
  const PassengerCompanySearchScreen({
    super.key,
    required this.sessionUser,
    this.gateway,
    this.onStatusNavigation = noopPassengerOnboardingStatusNavigation,
  });

  final AppSessionUser sessionUser;
  final PassengerOnboardingGateway? gateway;
  final PassengerOnboardingStatusNavigation onStatusNavigation;

  @override
  State<PassengerCompanySearchScreen> createState() =>
      _PassengerCompanySearchScreenState();
}

class _PassengerCompanySearchScreenState
    extends State<PassengerCompanySearchScreen> {
  final _searchController = TextEditingController();
  late final PassengerOnboardingGateway _gateway;
  List<PassengerOnboardingCompanyRecord> _companies = const [];
  bool _isLoading = true;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _gateway = widget.gateway ?? PassengerOnboardingService();
    _loadCompanies();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadCompanies([String? query]) async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final companies = await _gateway.searchCompanies(
        widget.sessionUser,
        query: query,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _companies = companies;
        _isLoading = false;
      });
    } on PassengerOnboardingException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _errorMessage = error.message;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      header: OnboardingHeader(
        onBack: () => Navigator.of(context).pop(),
        badgeText: AppStrings.passageiro.buscaCorporativaBadge,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          OnboardingSectionHeader(
            title: AppStrings.passageiro.buscarEmpresaTitulo,
            subtitle: AppStrings.passageiro.buscarEmpresaDescricao,
          ),
          const SizedBox(height: 18),
          const PassengerOnboardingProgressSteps(
            currentStep: PassengerOnboardingStep.company,
          ),
          const SizedBox(height: 28),
          TextField(
            controller: _searchController,
            decoration: onboardingInputDecoration(
              hintText: AppStrings.passageiro.pesquisarEmpresaHint,
              prefixIcon: const Icon(Icons.search_rounded),
            ),
            onSubmitted: _loadCompanies,
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.tonalIcon(
              onPressed: () => _loadCompanies(_searchController.text),
              icon: const Icon(Icons.tune_rounded),
              label: Text(AppStrings.passageiro.buscar),
            ),
          ),
          const SizedBox(height: 24),
          if (_isLoading)
            const Center(child: CircularProgressIndicator())
          else if (_errorMessage != null)
            PassengerOnboardingHighlightCard(
              title: AppStrings.passageiro.naoFoiPossivelCarregar,
              body: _errorMessage!,
              accentColor: const Color(0xFFBA1A1A),
              icon: Icons.error_outline_rounded,
            )
          else if (_companies.isEmpty)
            PassengerOnboardingHighlightCard(
              title: AppStrings.passageiro.nenhumaEmpresaEncontrada,
              body: AppStrings.passageiro.nenhumaEmpresaEncontradaDescricao,
              accentColor: OnboardingPalette.passenger,
              icon: Icons.business_outlined,
            )
          else
            ..._companies.map(
              (company) => Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: PassengerCompanySearchCard(
                  company: company,
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder:
                            (_) => PassengerCompanyRequestScreen(
                              sessionUser: widget.sessionUser,
                              gateway: _gateway,
                              company: company,
                              onStatusNavigation: widget.onStatusNavigation,
                            ),
                      ),
                    );
                  },
                ),
              ),
            ),
        ],
      ),
    );
  }
}
