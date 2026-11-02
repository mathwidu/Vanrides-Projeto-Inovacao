import 'dart:async';

import 'package:flutter/material.dart';

import '../../../app/experience/experience_home_router.dart';
import '../../../app/session/app_session_store.dart';
import '../../../app/session/app_session_user.dart';
import '../../../app/session/app_user_role.dart';
import '../../../app/theme/vanrides_design_tokens.dart';
import '../../../app/widgets/vanrides_liquid_surface.dart';
import '../../../app/widgets/vanrides_status_pill.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/cadastro_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import '../data/driver_onboarding_service.dart';
import '../domain/driver_onboarding_models.dart';

class DriverInviteScreen extends StatefulWidget {
  const DriverInviteScreen({
    super.key,
    required this.token,
    this.sessionUser,
    this.gateway,
    this.sessionStore,
  });

  static const screenKey = Key('driverInvite.screen');
  static const loginButtonKey = Key('driverInvite.login');
  static const signupButtonKey = Key('driverInvite.signup');
  static const redeemButtonKey = Key('driverInvite.redeem');

  final String token;
  final AppSessionUser? sessionUser;
  final DriverOnboardingGateway? gateway;
  final AppSessionStore? sessionStore;

  @override
  State<DriverInviteScreen> createState() => _DriverInviteScreenState();
}

class _DriverInviteScreenState extends State<DriverInviteScreen> {
  late final DriverOnboardingGateway _gateway;
  late final AppSessionStore _sessionStore;
  late Future<DriverInvitePreviewRecord> _previewFuture;
  bool _redeeming = false;

  @override
  void initState() {
    super.initState();
    _gateway = widget.gateway ?? DriverOnboardingService();
    _sessionStore = widget.sessionStore ?? AppSessionStore();
    _previewFuture = _gateway.previewInvite(widget.token);
    unawaited(_sessionStore.writePendingDriverInviteToken(widget.token));
  }

  Future<void> _redeemForCurrentSession() async {
    final session = widget.sessionUser;
    if (session == null || session.role != AppUserRole.driver) {
      return;
    }

    setState(() => _redeeming = true);
    try {
      final status = await _gateway.redeemInvite(session, token: widget.token);
      final upgraded = await _gateway.upgradeSession(
        session.copyWith(companyId: status.companyId),
        companyName: status.companyName,
      );
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
      setState(() => _redeeming = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  Future<void> _openLogin() async {
    await _sessionStore.writePendingDriverInviteToken(widget.token);
    if (!mounted) {
      return;
    }
    unawaited(
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => LoginScreen(initialDriverInviteToken: widget.token),
        ),
      ),
    );
  }

  Future<void> _openSignup() async {
    await _sessionStore.writePendingDriverInviteToken(widget.token);
    if (!mounted) {
      return;
    }
    unawaited(
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder:
              (_) => CadastroMotoristaScreen(initialInviteToken: widget.token),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: DriverInviteScreen.screenKey,
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
                constraints: const BoxConstraints(maxWidth: 520),
                child: FutureBuilder<DriverInvitePreviewRecord>(
                  future: _previewFuture,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState != ConnectionState.done) {
                      return const Center(child: CircularProgressIndicator());
                    }
                    if (snapshot.hasError || !snapshot.hasData) {
                      return _InviteErrorCard(
                        message:
                            snapshot.error?.toString() ??
                            'Convite indisponivel.',
                      );
                    }
                    return _InvitePreviewCard(
                      preview: snapshot.data!,
                      sessionUser: widget.sessionUser,
                      redeeming: _redeeming,
                      onLogin: _openLogin,
                      onSignup: _openSignup,
                      onRedeem: _redeemForCurrentSession,
                    );
                  },
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _InvitePreviewCard extends StatelessWidget {
  const _InvitePreviewCard({
    required this.preview,
    required this.sessionUser,
    required this.redeeming,
    required this.onLogin,
    required this.onSignup,
    required this.onRedeem,
  });

  final DriverInvitePreviewRecord preview;
  final AppSessionUser? sessionUser;
  final bool redeeming;
  final VoidCallback onLogin;
  final VoidCallback onSignup;
  final VoidCallback onRedeem;

  bool get _isDriverSession => sessionUser?.role == AppUserRole.driver;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return VanRidesLiquidSurface.card(
      semanticLabel: 'Convite de motorista',
      borderRadius: 32,
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          const VanRidesStatusPill(
            label: 'Convite de motorista',
            icon: Icons.directions_bus_filled_rounded,
            tone: VanRidesStatusTone.tracking,
          ),
          const SizedBox(height: 24),
          Text(
            preview.companyName,
            style: theme.textTheme.headlineMedium?.copyWith(
              color: VanRidesDesignTokens.textPrimary,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            'Entre como motorista para ficar vinculado automaticamente a esta empresa depois da confirmação do e-mail.',
            style: theme.textTheme.bodyLarge?.copyWith(
              color: VanRidesDesignTokens.textSecondary,
              height: 1.45,
            ),
          ),
          const SizedBox(height: 22),
          const _DriverInviteStepList(),
          const SizedBox(height: 22),
          VanRidesLiquidSurface.action(
            semanticLabel: 'Vinculo direto',
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                const Icon(
                  Icons.verified_user_rounded,
                  color: VanRidesDesignTokens.greenAction,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Sem fila de aprovação: o link ja confirma a empresa certa.',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: VanRidesDesignTokens.textPrimary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 26),
          if (sessionUser == null) ...[
            _PrimaryActionButton(
              key: DriverInviteScreen.loginButtonKey,
              label: 'Entrar com este convite',
              icon: Icons.login_rounded,
              onPressed: onLogin,
            ),
            const SizedBox(height: 12),
            _SecondaryActionButton(
              key: DriverInviteScreen.signupButtonKey,
              label: 'Criar conta de motorista',
              icon: Icons.person_add_alt_1_rounded,
              onPressed: onSignup,
            ),
          ] else if (_isDriverSession) ...[
            _PrimaryActionButton(
              key: DriverInviteScreen.redeemButtonKey,
              label: redeeming ? 'Vinculando...' : 'Vincular minha conta',
              icon: Icons.check_circle_rounded,
              onPressed: redeeming ? null : onRedeem,
            ),
          ] else ...[
            Text(
              'Este convite e exclusivo para perfil de motorista.',
              style: theme.textTheme.bodyLarge?.copyWith(
                color: VanRidesDesignTokens.amberWarning,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _DriverInviteStepList extends StatelessWidget {
  const _DriverInviteStepList();

  static const _steps = [
    _DriverInviteStep(
      label: 'Link recebido',
      description: 'Este convite identifica a empresa correta.',
      icon: Icons.link_rounded,
    ),
    _DriverInviteStep(
      label: 'Conta de motorista',
      description: 'Entre ou crie sua conta usando este fluxo.',
      icon: Icons.person_add_alt_1_rounded,
    ),
    _DriverInviteStep(
      label: 'E-mail confirmado',
      description: 'A verificação libera o vínculo com segurança.',
      icon: Icons.mark_email_read_outlined,
    ),
    _DriverInviteStep(
      label: 'Empresa vinculada',
      description: 'Depois disso, você já entra na operação dela.',
      icon: Icons.verified_user_rounded,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      children:
          _steps
              .map(
                (step) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: VanRidesLiquidSurface.action(
                    semanticLabel: 'Etapa ${step.label}',
                    padding: const EdgeInsets.all(14),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 38,
                          height: 38,
                          decoration: BoxDecoration(
                            color: VanRidesDesignTokens.blueTracking
                                .withValues(alpha: 0.16),
                            borderRadius: BorderRadius.circular(14),
                            border: Border.all(
                              color: VanRidesDesignTokens.blueTracking
                                  .withValues(alpha: 0.35),
                            ),
                          ),
                          child: Icon(
                            step.icon,
                            color: VanRidesDesignTokens.blueTracking,
                            size: 20,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                step.label,
                                style: Theme.of(
                                  context,
                                ).textTheme.titleSmall?.copyWith(
                                  color: VanRidesDesignTokens.textPrimary,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                step.description,
                                style: Theme.of(
                                  context,
                                ).textTheme.bodySmall?.copyWith(
                                  color: VanRidesDesignTokens.textSecondary,
                                  height: 1.35,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              )
              .toList(growable: false),
    );
  }
}

class _DriverInviteStep {
  const _DriverInviteStep({
    required this.label,
    required this.description,
    required this.icon,
  });

  final String label;
  final String description;
  final IconData icon;
}

class _InviteErrorCard extends StatelessWidget {
  const _InviteErrorCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.card(
      semanticLabel: 'Convite indisponivel',
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.link_off_rounded,
            size: 42,
            color: VanRidesDesignTokens.amberWarning,
          ),
          const SizedBox(height: 16),
          Text(
            'Convite indisponivel',
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: VanRidesDesignTokens.textPrimary,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            message.replaceFirst('Exception: ', ''),
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: VanRidesDesignTokens.textSecondary,
            ),
          ),
        ],
      ),
    );
  }
}

class _PrimaryActionButton extends StatelessWidget {
  const _PrimaryActionButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: FilledButton.icon(
        onPressed: onPressed,
        icon: Icon(icon),
        label: Text(label),
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(56),
          backgroundColor: VanRidesDesignTokens.blueTracking,
          foregroundColor: VanRidesDesignTokens.onAccent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          textStyle: Theme.of(
            context,
          ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w900),
        ),
      ),
    );
  }
}

class _SecondaryActionButton extends StatelessWidget {
  const _SecondaryActionButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        onPressed: onPressed,
        icon: Icon(icon),
        label: Text(label),
        style: OutlinedButton.styleFrom(
          minimumSize: const Size.fromHeight(56),
          foregroundColor: VanRidesDesignTokens.textPrimary,
          side: BorderSide(
            color: VanRidesDesignTokens.border.withValues(alpha: 0.65),
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          textStyle: Theme.of(
            context,
          ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
        ),
      ),
    );
  }
}
