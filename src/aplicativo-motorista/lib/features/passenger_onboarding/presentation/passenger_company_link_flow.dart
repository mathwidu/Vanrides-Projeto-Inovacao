import 'dart:async';

import 'package:flutter/material.dart';

import '../../../app/experience/experience_home_router.dart';
import '../../../app/session/app_company_link_status.dart';
import '../../../app/session/app_session_user.dart';
import '../../../l10n/app_strings.dart';
import '../../../onboarding_flow_components.dart';
import '../data/passenger_onboarding_service.dart';
import '../domain/passenger_onboarding_models.dart';
import 'passenger_company_invite_step.dart';
import 'passenger_company_search_step.dart';
import 'widgets/passenger_company_link_widgets.dart';

export 'passenger_company_invite_step.dart';
export 'passenger_company_request_step.dart';
export 'passenger_company_search_step.dart';

class PassengerCompanyGateScreen extends StatelessWidget {
  const PassengerCompanyGateScreen({
    super.key,
    required this.sessionUser,
    this.gateway,
  });

  final AppSessionUser sessionUser;
  final PassengerOnboardingGateway? gateway;

  @override
  Widget build(BuildContext context) {
    final service = gateway ?? PassengerOnboardingService();
    return OnboardingScaffold(
      header: OnboardingHeader(
        onBack: () => Navigator.of(context).popUntil((route) => route.isFirst),
        badgeText: AppStrings.passageiro.contaAtivaBadge,
      ),
      blurDots: const [
        OnboardingBlurDot(
          color: OnboardingPalette.passenger,
          size: 220,
          top: 56,
          right: -96,
        ),
        OnboardingBlurDot(
          color: Color(0x33A0F399),
          size: 180,
          bottom: 120,
          left: -72,
        ),
      ],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          OnboardingSectionHeader(
            title: AppStrings.passageiro.entrarEmUmaEmpresaTitulo,
            subtitle: AppStrings.passageiro.entrarEmUmaEmpresaSubtitulo,
          ),
          const SizedBox(height: 18),
          const PassengerOnboardingProgressSteps(
            currentStep: PassengerOnboardingStep.company,
          ),
          const SizedBox(height: 28),
          PassengerOnboardingHighlightCard(
            title: AppStrings.passageiro.oQueAconteceAgoraTitulo,
            body: AppStrings.passageiro.oQueAconteceAgoraDescricao,
            accentColor: OnboardingPalette.passenger,
            icon: Icons.apartment_rounded,
          ),
          const SizedBox(height: 24),
          OnboardingRoleCard(
            title: AppStrings.passageiro.vincularEmpresaTitulo,
            subtitle: AppStrings.passageiro.vincularEmpresaDescricao,
            icon: Icons.search_rounded,
            iconColor: OnboardingPalette.passenger,
            iconBackground: OnboardingPalette.passengerSoft,
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder:
                      (_) => PassengerCompanySearchScreen(
                        sessionUser: sessionUser,
                        gateway: service,
                        onStatusNavigation:
                            handlePassengerCompanyStatusNavigation,
                      ),
                ),
              );
            },
          ),
          const SizedBox(height: 16),
          OnboardingRoleCard(
            title: AppStrings.passageiro.jaTenhoCodigoTitulo,
            subtitle: AppStrings.passageiro.jaTenhoCodigoDescricao,
            icon: Icons.qr_code_rounded,
            iconColor: OnboardingPalette.driver,
            iconBackground: OnboardingPalette.driverSoft,
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder:
                      (_) => PassengerInviteCodeScreen(
                        sessionUser: sessionUser,
                        gateway: service,
                        onStatusNavigation:
                            handlePassengerCompanyStatusNavigation,
                      ),
                ),
              );
            },
          ),
          const SizedBox(height: 24),
          OutlinedButton.icon(
            onPressed: () async {
              final status = await service.fetchStatus(sessionUser);
              if (!context.mounted) {
                return;
              }
              await handlePassengerCompanyStatusNavigation(
                context,
                sessionUser: sessionUser,
                gateway: service,
                status: status,
              );
            },
            icon: const Icon(Icons.refresh_rounded),
            label: Text(AppStrings.passageiro.atualizarMeuStatus),
          ),
        ],
      ),
    );
  }
}

class PassengerCompanyPendingScreen extends StatefulWidget {
  const PassengerCompanyPendingScreen({
    super.key,
    required this.sessionUser,
    required this.status,
    this.gateway,
  });

  final AppSessionUser sessionUser;
  final PassengerOnboardingStatusRecord status;
  final PassengerOnboardingGateway? gateway;

  @override
  State<PassengerCompanyPendingScreen> createState() =>
      _PassengerCompanyPendingScreenState();
}

class _PassengerCompanyPendingScreenState
    extends State<PassengerCompanyPendingScreen> {
  late final PassengerOnboardingGateway _gateway;
  bool _isRefreshing = false;

  @override
  void initState() {
    super.initState();
    _gateway = widget.gateway ?? PassengerOnboardingService();
  }

  Future<void> _refreshStatus() async {
    setState(() => _isRefreshing = true);
    try {
      final status = await _gateway.fetchStatus(widget.sessionUser);
      if (!mounted) {
        return;
      }
      setState(() => _isRefreshing = false);
      await handlePassengerCompanyStatusNavigation(
        context,
        sessionUser: widget.sessionUser,
        gateway: _gateway,
        status: status,
      );
    } on PassengerOnboardingException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() => _isRefreshing = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final companyName =
        widget.status.pendingRequest?.companyName ??
        widget.status.companyName ??
        AppStrings.passageiro.empresaFallback;

    return OnboardingScaffold(
      header: OnboardingHeader(
        onBack: () => Navigator.of(context).popUntil((route) => route.isFirst),
        badgeText: AppStrings.passageiro.aguardandoAprovacaoBadge,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          OnboardingSectionHeader(
            title: AppStrings.passageiro.solicitacaoEnviadaTitulo,
            subtitle: AppStrings.passageiro.solicitacaoEnviadaDescricao,
          ),
          const SizedBox(height: 18),
          const PassengerOnboardingProgressSteps(
            currentStep: PassengerOnboardingStep.company,
          ),
          const SizedBox(height: 28),
          PassengerOnboardingHighlightCard(
            title: companyName,
            body: AppStrings.passageiro.solicitacaoEmpresaDescricao,
            accentColor: OnboardingPalette.passenger,
            icon: Icons.mark_email_read_outlined,
          ),
          const SizedBox(height: 16),
          PassengerOnboardingHighlightCard(
            title: AppStrings.passageiro.prazoEsperadoTitulo,
            body: AppStrings.passageiro.prazoEsperadoDescricao,
            accentColor: const Color(0xFF1B6D24),
            icon: Icons.schedule_rounded,
          ),
          const SizedBox(height: 28),
          FilledButton.icon(
            onPressed: _isRefreshing ? null : _refreshStatus,
            icon:
                _isRefreshing
                    ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                    : const Icon(Icons.refresh_rounded),
            label: Text(
              _isRefreshing
                  ? AppStrings.passageiro.verificando
                  : AppStrings.passageiro.atualizarStatus,
            ),
          ),
        ],
      ),
    );
  }
}

class PassengerCompanyLinkSuccessScreen extends StatelessWidget {
  const PassengerCompanyLinkSuccessScreen({
    super.key,
    required this.sessionUser,
    required this.companyName,
  });

  final AppSessionUser sessionUser;
  final String companyName;

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      header: OnboardingHeader(
        onBack: () => Navigator.of(context).popUntil((route) => route.isFirst),
        badgeText: AppStrings.passageiro.vinculoConcluidoBadge,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 16),
          Center(
            child: Container(
              width: 172,
              height: 172,
              decoration: BoxDecoration(
                color: OnboardingPalette.surface,
                shape: BoxShape.circle,
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
              child: Center(
                child: Container(
                  width: 112,
                  height: 112,
                  decoration: const BoxDecoration(
                    color: OnboardingPalette.passenger,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.check_rounded,
                    color: Colors.white,
                    size: 52,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 32),
          Text(
            AppStrings.passageiro.tudoPronto,
            style: Theme.of(context).textTheme.displaySmall?.copyWith(
              color: OnboardingPalette.textStrong,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 18),
          const PassengerOnboardingProgressSteps(
            currentStep: PassengerOnboardingStep.ready,
          ),
          const SizedBox(height: 18),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: OnboardingPalette.surface,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: OnboardingPalette.passenger.withValues(alpha: 0.28),
              ),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.apartment_rounded,
                  size: 18,
                  color: OnboardingPalette.passenger,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    companyName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: OnboardingPalette.textStrong,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Text(
            AppStrings.passageiro.vinculoConcluidoComEmpresa(companyName),
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
              color: OnboardingPalette.textMuted,
              height: 1.6,
            ),
          ),
          const SizedBox(height: 24),
          PassengerOnboardingHighlightCard(
            title: AppStrings.passageiro.statusDoVinculo,
            body: AppStrings.passageiro.statusDoVinculoDescricao,
            accentColor: OnboardingPalette.passenger,
            icon: Icons.verified_user_rounded,
          ),
          const SizedBox(height: 28),
          FilledButton.icon(
            onPressed: () {
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(
                  builder:
                      (_) => ExperienceHomeRouter(sessionUser: sessionUser),
                ),
                (route) => false,
              );
            },
            icon: const Icon(Icons.arrow_forward_rounded),
            label: Text(AppStrings.passageiro.irParaOApp),
          ),
        ],
      ),
    );
  }
}

class PassengerLinkedPendingDriverScreen extends StatefulWidget {
  const PassengerLinkedPendingDriverScreen({
    super.key,
    required this.sessionUser,
    this.gateway,
  });

  final AppSessionUser sessionUser;
  final PassengerOnboardingGateway? gateway;

  @override
  State<PassengerLinkedPendingDriverScreen> createState() =>
      _PassengerLinkedPendingDriverScreenState();
}

class _PassengerLinkedPendingDriverScreenState
    extends State<PassengerLinkedPendingDriverScreen> {
  late final PassengerOnboardingGateway _gateway;
  bool _isRefreshing = false;

  @override
  void initState() {
    super.initState();
    _gateway = widget.gateway ?? PassengerOnboardingService();
  }

  Future<void> _refresh() async {
    setState(() => _isRefreshing = true);
    try {
      final status = await _gateway.fetchStatus(widget.sessionUser);
      final updatedSession = await _gateway.upgradeSession(
        widget.sessionUser,
        motoristaId: status.motoristaId,
      );
      if (!mounted) {
        return;
      }
      setState(() => _isRefreshing = false);
      unawaited(
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(
            builder: (_) => ExperienceHomeRouter(sessionUser: updatedSession),
          ),
          (route) => false,
        ),
      );
    } on PassengerOnboardingException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() => _isRefreshing = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      header: OnboardingHeader(
        onBack: () => Navigator.of(context).popUntil((route) => route.isFirst),
        badgeText: AppStrings.passageiro.operacaoEmPreparoBadge,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          OnboardingSectionHeader(
            title: AppStrings.passageiro.empresaVinculadaTitulo,
            subtitle: AppStrings.passageiro.empresaVinculadaDescricao,
          ),
          const SizedBox(height: 18),
          const PassengerOnboardingProgressSteps(
            currentStep: PassengerOnboardingStep.services,
          ),
          const SizedBox(height: 28),
          PassengerOnboardingHighlightCard(
            title: AppStrings.passageiro.proximoPassoOperacionalTitulo,
            body: AppStrings.passageiro.proximoPassoOperacionalDescricao,
            accentColor: OnboardingPalette.passenger,
            icon: Icons.route_rounded,
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _isRefreshing ? null : _refresh,
            icon:
                _isRefreshing
                    ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                    : const Icon(Icons.refresh_rounded),
            label: Text(
              _isRefreshing
                  ? AppStrings.passageiro.atualizando
                  : AppStrings.passageiro.verificarMotoristaAgora,
            ),
          ),
        ],
      ),
    );
  }
}

Future<void> handlePassengerCompanyStatusNavigation(
  BuildContext context, {
  required AppSessionUser sessionUser,
  required PassengerOnboardingGateway gateway,
  required PassengerOnboardingStatusRecord status,
}) async {
  switch (status.companyLinkStatus) {
    case AppCompanyLinkStatus.noCompany:
      if (!context.mounted) {
        return;
      }
      unawaited(
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(
            builder:
                (_) => PassengerCompanyGateScreen(
                  sessionUser: sessionUser.copyWith(
                    companyLinkStatus: AppCompanyLinkStatus.noCompany,
                    clearCompanyId: true,
                    clearMotoristaId: true,
                  ),
                  gateway: gateway,
                ),
          ),
          (route) => route.isFirst,
        ),
      );
      return;
    case AppCompanyLinkStatus.pendingApproval:
      if (!context.mounted) {
        return;
      }
      unawaited(
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(
            builder:
                (_) => PassengerCompanyPendingScreen(
                  sessionUser: sessionUser.copyWith(
                    companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
                    clearCompanyId: true,
                  ),
                  status: status,
                  gateway: gateway,
                ),
          ),
          (route) => route.isFirst,
        ),
      );
      return;
    case AppCompanyLinkStatus.companyLinked:
      final upgraded = await gateway.upgradeSession(
        sessionUser,
        motoristaId: status.motoristaId,
      );
      if (!context.mounted) {
        return;
      }
      unawaited(
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(
            builder:
                (_) => PassengerCompanyLinkSuccessScreen(
                  sessionUser: upgraded,
                  companyName:
                      status.companyName ??
                      AppStrings.passageiro.empresaFallback,
                ),
          ),
          (route) => route.isFirst,
        ),
      );
      return;
  }
}
