import 'dart:async';

import 'package:flutter/material.dart';

import '../../../app/session/app_session_store.dart';
import '../../account/data/account_profile_service.dart';
import '../../account/domain/account_profile.dart';
import '../../account/presentation/account_profile_screen.dart';
import '../domain/admin_portal_session.dart';
import 'admin_login_screen.dart';
import 'admin_motoristas_screen.dart';
import 'admin_passageiros_screen.dart';
import 'admin_responsaveis_screen.dart';
import 'admin_rotas_screen.dart';
import 'admin_servicos_recorrentes_screen.dart';
import 'admin_support_tickets_screen.dart';
import 'admin_vinculos_screen.dart';
import 'admin_viagens_screen.dart';
import 'admin_viagens_review_intent.dart';
import 'widgets/admin_dashboard_screen.dart';
import 'widgets/admin_portal_layout.dart';

class AdminShellScreen extends StatefulWidget {
  const AdminShellScreen({
    super.key,
    required this.session,
    this.accountProfileGateway,
  });

  final AdminPortalSession session;
  final AccountProfileGateway? accountProfileGateway;

  @override
  State<AdminShellScreen> createState() => _AdminShellScreenState();
}

class _AdminProfileSnapshot {
  const _AdminProfileSnapshot({this.profile});

  final AccountProfile? profile;
}

class _AdminShellScreenState extends State<AdminShellScreen> {
  AdminPortalDestination _selectedDestination =
      AdminPortalDestination.dashboard;
  AdminViagensReviewIntent? _viagensReviewIntent;
  late final ValueNotifier<_AdminProfileSnapshot> _profileSnapshot;
  int _profileLoadGeneration = 0;

  @override
  void initState() {
    super.initState();
    _profileSnapshot = ValueNotifier(const _AdminProfileSnapshot());
    _loadAdminProfile();
  }

  @override
  void didUpdateWidget(covariant AdminShellScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.accountProfileGateway != widget.accountProfileGateway ||
        oldWidget.session.accessToken != widget.session.accessToken ||
        oldWidget.session.email != widget.session.email) {
      _loadAdminProfile();
    }
  }

  @override
  void dispose() {
    _profileSnapshot.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<_AdminProfileSnapshot>(
      valueListenable: _profileSnapshot,
      builder: (context, snapshot, _) {
        final session = _sessionWithProfile(snapshot.profile);
        return AdminPortalShell(
          session: session,
          selectedDestination: _selectedDestination,
          onDestinationSelected: (destination) {
            _selectDestination(destination);
          },
          floatingAction:
              _selectedDestination == AdminPortalDestination.dashboard
                  ? AdminPortalFloatingAction(
                    icon: Icons.departure_board_outlined,
                    onPressed: () {
                      _openViagensDestination();
                    },
                  )
                  : null,
          onLogout: _logout,
          contextHint: _contextHintFor(_selectedDestination),
          child: _buildSection(session),
        );
      },
    );
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
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const AdminLoginScreen()),
        (route) => false,
      ),
    );
  }

  Future<void> _loadAdminProfile() async {
    final gateway = widget.accountProfileGateway;
    final generation = ++_profileLoadGeneration;
    if (gateway == null) {
      _profileSnapshot.value = const _AdminProfileSnapshot();
      return;
    }

    _profileSnapshot.value = _AdminProfileSnapshot(
      profile: _profileSnapshot.value.profile,
    );

    try {
      final profile = await gateway.fetchMe();
      if (!mounted || generation != _profileLoadGeneration) {
        return;
      }
      _profileSnapshot.value = _AdminProfileSnapshot(profile: profile);
    } catch (_) {
      if (!mounted || generation != _profileLoadGeneration) {
        return;
      }
      _profileSnapshot.value = _AdminProfileSnapshot(
        profile: _profileSnapshot.value.profile,
      );
    }
  }

  AdminPortalSession _sessionWithProfile(AccountProfile? profile) {
    if (profile == null) {
      return widget.session;
    }

    final company = profile.empresa;
    return widget.session.copyWith(
      adminId: profile.companyAdminId ?? widget.session.adminId,
      adminName: profile.nome,
      companyId: company?.id ?? widget.session.companyId,
      companyName: company?.nome ?? widget.session.companyName,
      companySlug: company?.slug ?? widget.session.companySlug,
      email: profile.email,
      backendRoleLabel:
          profile.tipoUsuario.trim().isNotEmpty
              ? profile.tipoUsuario
              : widget.session.backendRoleLabel,
    );
  }

  Widget _buildSection(AdminPortalSession session) {
    switch (_selectedDestination) {
      case AdminPortalDestination.dashboard:
        return AdminDashboardScreen(
          session: session,
          onOpenServicos:
              () => _selectDestination(AdminPortalDestination.servicos),
          onOpenViagens: _openViagensDestination,
          onOpenMotoristas:
              () => _selectDestination(AdminPortalDestination.motoristas),
          onOpenPassageiros:
              () => _selectDestination(AdminPortalDestination.passageiros),
        );
      case AdminPortalDestination.motoristas:
        return AdminMotoristasScreen(session: session);
      case AdminPortalDestination.passageiros:
        return AdminPassageirosScreen(
          session: session,
          onOpenViagens: _openViagensDestination,
          onOpenViagensReview: _openViagensReview,
        );
      case AdminPortalDestination.responsaveis:
        return AdminResponsaveisScreen(session: session);
      case AdminPortalDestination.vinculos:
        return AdminVinculosScreen(session: session);
      case AdminPortalDestination.servicos:
        return AdminServicosRecorrentesScreen(
          session: session,
          onOpenViagens: _openViagensDestination,
          onOpenViagensReview: _openViagensReview,
        );
      case AdminPortalDestination.rotas:
        return AdminRotasScreen(session: session);
      case AdminPortalDestination.viagens:
        return AdminViagensScreen(
          session: session,
          reviewIntent: _viagensReviewIntent,
        );
      case AdminPortalDestination.suporte:
        return AdminSupportTicketsScreen(session: session);
      case AdminPortalDestination.conta:
        return _AdminAccountView(
          session: session,
          accountProfileGateway: widget.accountProfileGateway,
          onLogout: _logout,
        );
    }
  }

  void _openViagensDestination() {
    _selectDestination(AdminPortalDestination.viagens);
  }

  void _openViagensReview(AdminViagensReviewIntent intent) {
    _selectDestination(AdminPortalDestination.viagens, reviewIntent: intent);
  }

  void _selectDestination(
    AdminPortalDestination destination, {
    AdminViagensReviewIntent? reviewIntent,
  }) {
    setState(() {
      _selectedDestination = destination;
      _viagensReviewIntent =
          destination == AdminPortalDestination.viagens ? reviewIntent : null;
    });
  }

  String _contextHintFor(AdminPortalDestination destination) {
    return switch (destination) {
      AdminPortalDestination.dashboard =>
        'Central do dia, pendências e próximas ações',
      AdminPortalDestination.motoristas =>
        'Convites, solicitações e motoristas ativos',
      AdminPortalDestination.passageiros =>
        'Cadastro, parada, responsáveis e serviços',
      AdminPortalDestination.responsaveis =>
        'Responsáveis, menores e permissões',
      AdminPortalDestination.vinculos =>
        'Convites, solicitações e vínculos familiares',
      AdminPortalDestination.servicos =>
        'Linhas recorrentes, agenda, passageiros e motorista padrão',
      AdminPortalDestination.rotas => 'Rotas-base legadas e compatibilidade',
      AdminPortalDestination.viagens =>
        'Montagem, manutenção e exceções da data',
      AdminPortalDestination.suporte =>
        'Fila de mensagens, respostas e resolução',
      AdminPortalDestination.conta => 'Perfil, empresa e segurança',
    };
  }
}

class _AdminAccountView extends StatelessWidget {
  const _AdminAccountView({
    required this.session,
    required this.accountProfileGateway,
    required this.onLogout,
  });

  final AdminPortalSession session;
  final AccountProfileGateway? accountProfileGateway;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    return AccountProfileScreen(
      surfaceKey: const Key('admin.account.darkSurface'),
      editButtonKey: const Key('admin.account.editProfileButton'),
      logoutButtonKey: const Key('admin.account.logout.darkButton'),
      fallbackName: session.adminName,
      fallbackEmail: session.email,
      roleFallback: session.backendRoleLabel,
      avatarFallback: 'A',
      roleIcon: Icons.admin_panel_settings_outlined,
      enabled: accountProfileGateway != null,
      accountProfileGateway: accountProfileGateway,
      onLogout: onLogout,
      roleSectionsBuilder:
          (context, profile) => [
            _AdminAccountRoleSection(session: session, profile: profile),
          ],
    );
  }
}

class _AdminAccountRoleSection extends StatelessWidget {
  const _AdminAccountRoleSection({
    required this.session,
    required this.profile,
  });

  final AdminPortalSession session;
  final AccountProfile? profile;

  @override
  Widget build(BuildContext context) {
    final companyName =
        profile?.empresa?.nome ??
        (session.companyName.trim().isNotEmpty
            ? session.companyName.trim()
            : session.companyDisplayName);
    final emailVerified = profile?.emailVerified;

    return AccountProfileInfoCard(
      children: [
        AccountProfileInfoRow(
          icon: Icons.verified_user_outlined,
          title: 'Acesso administrativo',
          value:
              profile?.companyAdminId != null
                  ? 'Administrador ativo'
                  : 'Conta administrativa',
          helper: 'Gerencia usuários, vínculos, serviços e viagens da empresa.',
        ),
        AccountProfileInfoRow(
          icon: Icons.apartment_outlined,
          title: 'Empresa',
          value: companyName,
          helper: 'Todos os dados do portal ficam restritos a esta empresa.',
        ),
        AccountProfileInfoRow(
          icon: Icons.mark_email_read_outlined,
          title: 'Segurança',
          value:
              emailVerified == false
                  ? 'E-mail pendente de verificação'
                  : 'E-mail verificado',
          helper:
              emailVerified == false
                  ? 'Confirme o e-mail para manter acesso completo ao portal.'
                  : 'Use um e-mail corporativo atualizado para receber convites e alertas.',
          actionRequired: emailVerified == false,
        ),
      ],
    );
  }
}
