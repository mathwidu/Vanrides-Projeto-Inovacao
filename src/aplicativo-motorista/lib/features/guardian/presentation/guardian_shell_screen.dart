import 'dart:async';

import 'package:flutter/material.dart';

import '../../../app/theme/vanrides_motion.dart';
import '../../../app/session/app_session_store.dart';
import '../../account/data/account_profile_service.dart';
import '../../account/domain/account_profile.dart';
import '../../account/presentation/account_profile_screen.dart';
import '../../../l10n/app_strings.dart';
import '../../service_presence/data/service_presence_service.dart';
import '../../support/data/support_ticket_service.dart';
import '../../support/presentation/support_ticket_account_section.dart';
import '../../tracking/data/trip_route_event_service.dart';
import '../../tracking/data/trip_tracking_service.dart';
import '../../tracking/presentation/map_first_v2_shell.dart';
import '../../tracking/presentation/widgets/vanrides_map_tokens.dart';
import '../data/guardian_passageiro_service.dart';
import '../data/guardian_presenca_service.dart';
import '../data/guardian_localizacao_service.dart';
import '../domain/guardian_linked_passageiro_record.dart';
import '../domain/guardian_session.dart';
import '../../../services/websocket_service.dart';
import 'guardian_home_screen.dart';
import 'guardian_presencas_screen.dart';
import 'guardian_localizacao_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';

class GuardianShellScreen extends StatefulWidget {
  const GuardianShellScreen({
    super.key,
    required this.session,
    this.homeGateway,
    this.presencaGateway,
    this.servicePresenceGateway,
    this.localizacaoGateway,
    this.trackingGateway,
    this.tripRouteEventGateway,
    this.webSocketService,
    this.accountProfileGateway,
  });

  final GuardianSession session;
  final GuardianPassageiroGateway? homeGateway;
  final GuardianPresencaGateway? presencaGateway;
  final ServicePresenceGateway? servicePresenceGateway;
  final GuardianLocalizacaoGateway? localizacaoGateway;
  final TripTrackingGateway? trackingGateway;
  final TripRouteEventGateway? tripRouteEventGateway;
  final WebSocketServiceBase? webSocketService;
  final AccountProfileGateway? accountProfileGateway;
  static const logoutButtonKey = Key('guardian.shell.logout');

  @override
  State<GuardianShellScreen> createState() => _GuardianShellScreenState();
}

class _GuardianShellScreenState extends State<GuardianShellScreen> {
  int _currentIndex = 0;

  @override
  Widget build(BuildContext context) {
    final motion = VanRidesMotionPolicy.of(context);
    final pages = [
      SafeArea(
        child: GuardianHomeScreen(
          session: widget.session,
          gateway: widget.homeGateway,
          presencaGateway: widget.presencaGateway,
          localizacaoGateway: widget.localizacaoGateway,
        ),
      ),
      SafeArea(
        child: GuardianPresencasScreen(
          session: widget.session,
          passageiroGateway: widget.homeGateway,
          presencaGateway: widget.presencaGateway,
          servicePresenceGateway: widget.servicePresenceGateway,
        ),
      ),
      GuardianLocalizacaoScreen(
        session: widget.session,
        passageiroGateway: widget.homeGateway,
        localizacaoGateway: widget.localizacaoGateway,
        trackingGateway: widget.trackingGateway,
        tripRouteEventGateway: widget.tripRouteEventGateway,
        webSocketService: widget.webSocketService,
        selectedNavIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() => _currentIndex = index);
        },
      ),
      _GuardianAccountView(
        session: widget.session,
        enabled: _currentIndex == 3,
        passageiroGateway: widget.homeGateway,
        accountProfileGateway: widget.accountProfileGateway,
      ),
    ];

    return Scaffold(
      backgroundColor: VanRidesMapTokens.backgroundDeep,
      body: AnimatedSwitcher(
        duration: motion.duration(VanRidesMotionTokens.navigationSelection),
        switchInCurve: motion.curve(VanRidesMotionTokens.easeOutQuart),
        switchOutCurve: motion.curve(VanRidesMotionTokens.easeOutQuart),
        child: pages[_currentIndex],
      ),
      bottomNavigationBar:
          _currentIndex == 2
              ? null
              : MapFirstBottomNavBar(
                selectedIndex: _currentIndex,
                onDestinationSelected: (index) {
                  setState(() => _currentIndex = index);
                },
                destinations: _guardianNavDestinations,
              ),
    );
  }
}

List<MapFirstNavDestination> get _guardianNavDestinations => [
  MapFirstNavDestination(
    icon: Icons.home_outlined,
    selectedIcon: Icons.home,
    label: AppStrings.guardian.inicio,
  ),
  MapFirstNavDestination(
    icon: Icons.fact_check_outlined,
    selectedIcon: Icons.fact_check,
    label: AppStrings.guardian.presencas,
  ),
  MapFirstNavDestination(
    icon: Icons.location_on_outlined,
    selectedIcon: Icons.location_on,
    label: AppStrings.guardian.localizacao,
  ),
  MapFirstNavDestination(
    icon: Icons.person_outline,
    selectedIcon: Icons.person,
    label: AppStrings.guardian.conta,
  ),
];

class _GuardianAccountView extends StatefulWidget {
  const _GuardianAccountView({
    required this.session,
    required this.enabled,
    this.passageiroGateway,
    this.accountProfileGateway,
  });

  final GuardianSession session;
  final bool enabled;
  final GuardianPassageiroGateway? passageiroGateway;
  final AccountProfileGateway? accountProfileGateway;

  @override
  State<_GuardianAccountView> createState() => _GuardianAccountViewState();
}

class _GuardianAccountViewState extends State<_GuardianAccountView> {
  late final GuardianPassageiroGateway _passageiroGateway;
  late final SupportTicketGateway _supportTicketGateway;
  var _loadingPassageiros = false;
  var _loadedPassageiros = false;
  String? _passageirosError;
  List<GuardianLinkedPassageiroRecord> _passageiros = const [];

  @override
  void initState() {
    super.initState();
    _passageiroGateway =
        widget.passageiroGateway ??
        GuardianPassageiroService(session: widget.session);
    _supportTicketGateway = UserSupportTicketService(
      email: widget.session.email,
      senha: '',
      accessToken: widget.session.accessToken,
    );
    if (widget.enabled) {
      _loadPassageiros();
    }
  }

  @override
  void didUpdateWidget(covariant _GuardianAccountView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.enabled && widget.enabled && !_loadedPassageiros) {
      _loadPassageiros();
    }
  }

  Future<void> _loadPassageiros() async {
    if (_loadingPassageiros) {
      return;
    }
    setState(() {
      _loadingPassageiros = true;
      _passageirosError = null;
    });

    try {
      final passageiros = await _passageiroGateway.fetchLinkedPassageiros();
      if (!mounted) {
        return;
      }
      setState(() {
        _passageiros = passageiros;
        _loadedPassageiros = true;
        _loadingPassageiros = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _passageirosError = error.toString();
        _loadedPassageiros = true;
        _loadingPassageiros = false;
      });
    }
  }

  void _logout() {
    unawaited(_clearSessionAndNavigateToLogin());
  }

  Future<void> _clearSessionAndNavigateToLogin() async {
    await AppSessionStore().clear();
    if (!mounted) {
      return;
    }
    unawaited(
      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
        (route) => false,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AccountProfileScreen(
      surfaceKey: const Key('guardian.account.darkSurface'),
      editButtonKey: const Key('guardian.account.editProfileButton'),
      logoutButtonKey: GuardianShellScreen.logoutButtonKey,
      fallbackName: widget.session.guardianName,
      fallbackEmail: widget.session.email,
      roleFallback: widget.session.backendRoleLabel,
      avatarFallback: 'R',
      roleIcon: Icons.family_restroom_rounded,
      enabled: widget.enabled,
      accountProfileGateway: widget.accountProfileGateway,
      onLogout: _logout,
      roleSectionsBuilder: (context, profile) {
        return [
          _GuardianAccountRoleSection(
            profile: profile,
            loading: _loadingPassageiros,
            error: _passageirosError,
            passageiros: _passageiros,
          ),
          SupportTicketAccountSection(
            gateway: _supportTicketGateway,
            enabled: widget.enabled,
          ),
        ];
      },
    );
  }
}

class _GuardianAccountRoleSection extends StatelessWidget {
  const _GuardianAccountRoleSection({
    required this.profile,
    required this.loading,
    required this.error,
    required this.passageiros,
  });

  final AccountProfile? profile;
  final bool loading;
  final String? error;
  final List<GuardianLinkedPassageiroRecord> passageiros;

  @override
  Widget build(BuildContext context) {
    final countLabel =
        loading
            ? 'Carregando vínculos...'
            : passageiros.isEmpty
            ? 'Nenhum passageiro vinculado'
            : '${passageiros.length} ${passageiros.length == 1 ? 'passageiro' : 'passageiros'}';
    final companyLabel = profile?.empresa?.nome ?? 'Empresa vinculada';

    return AccountProfileInfoCard(
      children: [
        AccountProfileInfoRow(
          icon: Icons.apartment_outlined,
          title: 'Empresa',
          value: companyLabel,
          helper: 'Seu acesso acompanha os passageiros desta empresa.',
        ),
        AccountProfileInfoRow(
          icon: Icons.family_restroom_outlined,
          title: 'Passageiros vinculados',
          value: error == null ? countLabel : 'Não foi possível carregar',
          helper:
              error ??
              'Você acompanha presença e localização destes passageiros.',
          actionRequired: error != null || (!loading && passageiros.isEmpty),
        ),
        for (final passageiro in passageiros.take(3))
          AccountProfileInfoRow(
            icon: Icons.person_pin_circle_outlined,
            title: passageiro.nome,
            value:
                passageiro.menorDeIdade
                    ? 'Menor de idade'
                    : 'Passageiro adulto',
            helper:
                (passageiro.motoristaNome ?? '').trim().isNotEmpty
                    ? 'Motorista: ${passageiro.motoristaNome}'
                    : 'Motorista ainda não definido.',
            actionRequired: (passageiro.motoristaNome ?? '').trim().isEmpty,
          ),
        if (error != null)
          const AccountProfileInfoRow(
            icon: Icons.refresh_rounded,
            title: 'Atualizar vínculos',
            value: 'Tentar novamente',
            helper:
                'Toque no botão Editar perfil para manter seus dados em dia.',
          ),
      ],
    );
  }
}
