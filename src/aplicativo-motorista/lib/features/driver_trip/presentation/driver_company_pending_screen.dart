import 'dart:async';

import 'package:flutter/material.dart';

import '../../../app/experience/experience_home_router.dart';
import '../../../app/session/app_company_link_status.dart';
import '../../../app/session/app_session_store.dart';
import '../../../app/session/app_session_user.dart';
import '../../../app/theme/vanrides_design_tokens.dart';
import '../../../app/widgets/vanrides_liquid_surface.dart';
import '../../../app/widgets/vanrides_status_pill.dart';
import '../../auth/presentation/login_screen.dart';
import '../../../features/driver_onboarding/data/driver_onboarding_service.dart';
import '../../../features/driver_onboarding/domain/driver_onboarding_models.dart';
import '../../../features/driver_onboarding/presentation/driver_invite_screen.dart';
import 'driver_company_pending_widgets.dart';

class DriverCompanyPendingScreen extends StatefulWidget {
  const DriverCompanyPendingScreen({
    super.key,
    required this.sessionUser,
    this.gateway,
    this.sessionStore,
  });

  static const screenKey = Key('driver.companyPending.screen');
  static const searchFieldKey = driverCompanyPendingSearchFieldKey;
  static const requestButtonPrefix = driverCompanyPendingRequestButtonPrefix;
  static const openInviteButtonKey = driverCompanyPendingOpenInviteButtonKey;

  final AppSessionUser sessionUser;
  final DriverOnboardingGateway? gateway;
  final AppSessionStore? sessionStore;

  @override
  State<DriverCompanyPendingScreen> createState() =>
      _DriverCompanyPendingScreenState();
}

class _DriverCompanyPendingScreenState
    extends State<DriverCompanyPendingScreen> {
  late final DriverOnboardingGateway _gateway;
  late final AppSessionStore _sessionStore;
  final _searchController = TextEditingController();
  DriverOnboardingStatusRecord? _status;
  List<DriverOnboardingCompanyRecord> _companies = const [];
  String? _pendingInviteToken;
  bool _loadingStatus = true;
  bool _loadingCompanies = true;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _gateway = widget.gateway ?? DriverOnboardingService();
    _sessionStore = widget.sessionStore ?? AppSessionStore();
    _loadInitialState();
  }

  void _updateState(VoidCallback update) {
    if (!mounted) {
      return;
    }
    setState(update);
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadInitialState() async {
    final inviteTokenFuture = _sessionStore.readPendingDriverInviteToken();
    final statusFuture = _gateway.fetchStatus(widget.sessionUser);
    final companiesFuture = _gateway.searchCompanies(widget.sessionUser);

    try {
      final results = await Future.wait<Object?>([
        inviteTokenFuture,
        statusFuture,
        companiesFuture,
      ]);
      if (!mounted) {
        return;
      }
      _updateState(() {
        _pendingInviteToken = results[0] as String?;
        _status = results[1] as DriverOnboardingStatusRecord;
        _companies =
            results[2] as List<DriverOnboardingCompanyRecord>? ?? const [];
        _loadingStatus = false;
        _loadingCompanies = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _status = DriverOnboardingStatusRecord(
          companyLinkStatus: widget.sessionUser.companyLinkStatus,
        );
        _loadingStatus = false;
        _loadingCompanies = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Nao foi possivel carregar empresas agora.'),
        ),
      );
    }
  }

  Future<void> _search() async {
    _updateState(() => _loadingCompanies = true);
    try {
      final companies = await _gateway.searchCompanies(
        widget.sessionUser,
        query: _searchController.text,
      );
      if (!mounted) {
        return;
      }
      _updateState(() {
        _companies = companies;
        _loadingCompanies = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() => _loadingCompanies = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  Future<void> _requestCompany(DriverOnboardingCompanyRecord company) async {
    _updateState(() => _submitting = true);
    try {
      final status = await _gateway.createRequest(
        widget.sessionUser,
        companyId: company.id,
      );
      if (!mounted) {
        return;
      }
      _updateState(() {
        _status = status;
        _submitting = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Solicitacao enviada para ${company.nome}.')),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() => _submitting = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  Future<void> _upgradeLinkedSession() async {
    _updateState(() => _submitting = true);
    try {
      final upgraded = await _gateway.upgradeSession(widget.sessionUser);
      await _sessionStore.writeCurrent(upgraded);
      await _sessionStore.clearPendingDriverInviteToken();
      if (!mounted) {
        return;
      }
      unawaited(
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(
            builder: (_) => ExperienceHomeRouter(sessionUser: upgraded),
          ),
          (_) => false,
        ),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() => _submitting = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  void _openPendingInvite() {
    final token = _pendingInviteToken;
    if (token == null || token.isEmpty) {
      return;
    }
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (_) => DriverInviteScreen(
              token: token,
              sessionUser: widget.sessionUser,
              gateway: _gateway,
              sessionStore: _sessionStore,
            ),
      ),
    );
  }

  void _signOut() {
    unawaited(_clearSessionAndNavigateToLogin());
  }

  Future<void> _clearSessionAndNavigateToLogin() async {
    await _sessionStore.clear();
    if (!mounted) {
      return;
    }
    unawaited(
      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
        (_) => false,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final status =
        _status?.companyLinkStatus ?? widget.sessionUser.companyLinkStatus;
    return Scaffold(
      key: DriverCompanyPendingScreen.screenKey,
      backgroundColor: VanRidesDesignTokens.backgroundDeep,
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              VanRidesDesignTokens.mapBase,
              VanRidesDesignTokens.backgroundDeep,
            ],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 560),
                child: VanRidesLiquidSurface.card(
                  semanticLabel: 'Vinculo do motorista',
                  borderRadius: 32,
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      VanRidesStatusPill(
                        label:
                            status == AppCompanyLinkStatus.pendingApproval
                                ? 'Solicitacao enviada'
                                : 'Motorista sem empresa',
                        icon: Icons.directions_bus_filled_rounded,
                        tone:
                            status == AppCompanyLinkStatus.pendingApproval
                                ? VanRidesStatusTone.warning
                                : VanRidesStatusTone.tracking,
                      ),
                      const SizedBox(height: 22),
                      Text(
                        status == AppCompanyLinkStatus.pendingApproval
                            ? 'Aguardando aprovação da empresa'
                            : 'Conecte sua conta a uma empresa',
                        style: Theme.of(
                          context,
                        ).textTheme.headlineSmall?.copyWith(
                          color: VanRidesDesignTokens.textPrimary,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Text(
                        status == AppCompanyLinkStatus.pendingApproval
                            ? 'Quando a empresa aprovar sua solicitação, você entra direto na operação dela.'
                            : 'Use o link recebido pela empresa ou busque a empresa para solicitar acesso como motorista.',
                        style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: VanRidesDesignTokens.textSecondary,
                          height: 1.45,
                        ),
                      ),
                      const SizedBox(height: 20),
                      DriverPendingAccountEmailCard(
                        email: widget.sessionUser.email,
                      ),
                      const SizedBox(height: 20),
                      if (_loadingStatus)
                        const Center(child: CircularProgressIndicator())
                      else if (status == AppCompanyLinkStatus.companyLinked)
                        DriverLinkedSessionAction(
                          loading: _submitting,
                          onPressed: _upgradeLinkedSession,
                        )
                      else if (status == AppCompanyLinkStatus.pendingApproval)
                        DriverPendingRequestCard(status: _status)
                      else ...[
                        if (_pendingInviteToken != null) ...[
                          DriverPendingInviteAction(
                            onPressed: _openPendingInvite,
                          ),
                          const SizedBox(height: 18),
                        ],
                        DriverCompanySearch(
                          controller: _searchController,
                          loading: _loadingCompanies,
                          submitting: _submitting,
                          companies: _companies,
                          onSearch: _search,
                          onRequest: _requestCompany,
                        ),
                      ],
                      const SizedBox(height: 24),
                      SizedBox(
                        width: double.infinity,
                        child: TextButton.icon(
                          onPressed: _signOut,
                          icon: const Icon(Icons.logout_rounded),
                          label: const Text('Sair e usar outra conta'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
