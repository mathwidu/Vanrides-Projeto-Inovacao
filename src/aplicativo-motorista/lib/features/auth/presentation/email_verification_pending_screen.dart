import 'dart:async';

import 'package:flutter/material.dart';

import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/onboarding_flow_components.dart';
import 'package:vanrides_frontend_dev/services/public_auth_support.dart';

part 'email_verification_pending_feedback_banner.dart';

enum EmailVerificationPendingVariant {
  passengerSignup,
  driverSignup,
  loginBlocked,
}

class EmailVerificationPendingScreen extends StatefulWidget {
  const EmailVerificationPendingScreen({
    super.key,
    required this.email,
    required this.variant,
    this.initialResendCooldown,
  });

  static const screenKey = Key('auth.emailVerificationPending.screen');
  static const resendButtonKey = Key('auth.emailVerificationPending.resend');
  static const secondaryButtonKey = Key(
    'auth.emailVerificationPending.secondary',
  );
  static const tertiaryButtonKey = Key(
    'auth.emailVerificationPending.tertiary',
  );
  static const successBannerKey = Key('auth.emailVerificationPending.success');

  final String email;
  final EmailVerificationPendingVariant variant;
  final Duration? initialResendCooldown;

  @override
  State<EmailVerificationPendingScreen> createState() =>
      _EmailVerificationPendingScreenState();
}

class _EmailVerificationPendingScreenState
    extends State<EmailVerificationPendingScreen> {
  Timer? _cooldownTimer;
  bool _isResending = false;
  String? _feedbackMessage;
  Duration _remainingCooldown = Duration.zero;

  bool get _isResendBlocked =>
      _isResending || _remainingCooldown > Duration.zero;

  @override
  void initState() {
    super.initState();
    _startCooldown(widget.initialResendCooldown);
  }

  @override
  void dispose() {
    _cooldownTimer?.cancel();
    super.dispose();
  }

  _EmailVerificationPendingContent get _content {
    switch (widget.variant) {
      case EmailVerificationPendingVariant.passengerSignup:
        return _EmailVerificationPendingContent(
          title: AppStrings.passageiro.conferirSeuEmail,
          description: AppStrings.passageiro.contaCriadaComSucesso,
          accentColor: OnboardingPalette.passenger,
          softAccentColor: OnboardingPalette.passengerSoft,
          primaryGradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF005DAC), Color(0xFF1976D2)],
          ),
          secondaryBorderColor: const Color(0x66005DAC),
          emailLabel: AppStrings.passageiro.emailConfirmacaoEnviadoPara,
          secondaryLabel: AppStrings.passageiro.irParaLogin,
          tertiaryLabel: AppStrings.passageiro.editarEmailDepois,
          note: AppStrings.passageiro.depoisDeConfirmarVolteAoApp,
          heroIcon: Icons.mark_email_read_rounded,
        );
      case EmailVerificationPendingVariant.driverSignup:
        return _EmailVerificationPendingContent(
          title: AppStrings.passageiro.conferirSeuEmail,
          description: AppStrings.passageiro.motoristaCadastroIniciado,
          accentColor: OnboardingPalette.driver,
          softAccentColor: OnboardingPalette.driverSoft,
          primaryGradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF1B6D24), Color(0xFFA0F399)],
          ),
          secondaryBorderColor: const Color(0x661B6D24),
          emailLabel: AppStrings.passageiro.emailConfirmacaoEnviadoPara,
          secondaryLabel: AppStrings.passageiro.irParaLogin,
          tertiaryLabel: AppStrings.passageiro.editarEmailDepois,
          note: AppStrings.passageiro.depoisDeConfirmarVolteAoApp,
          heroIcon: Icons.mark_email_read_rounded,
        );
      case EmailVerificationPendingVariant.loginBlocked:
        return _EmailVerificationPendingContent(
          title: AppStrings.passageiro.seuEmailAindaNaoFoiConfirmado,
          description: AppStrings.passageiro.acessoTemporariamenteProtegido,
          accentColor: OnboardingPalette.passenger,
          softAccentColor: const Color(0xFFEAF1FF),
          primaryGradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF005DAC), Color(0xFF1976D2)],
          ),
          secondaryBorderColor: const Color(0x66005DAC),
          emailLabel: AppStrings.passageiro.enderecoRegistrado,
          secondaryLabel: AppStrings.passageiro.jaConfirmeiTentarNovamente,
          tertiaryLabel: AppStrings.passageiro.voltar,
          note: null,
          heroIcon: Icons.verified_user_rounded,
          showSecurityBadge: true,
          showSupportCard: true,
        );
    }
  }

  Future<void> _handleResend() async {
    if (_isResendBlocked) {
      return;
    }

    setState(() => _isResending = true);

    try {
      final result = await resendEmailVerification(email: widget.email);
      if (!mounted) {
        return;
      }

      setState(() {
        _feedbackMessage = result.message;
        _isResending = false;
      });
      _startCooldown(emailVerificationResendCooldown);
    } catch (error) {
      if (!mounted) {
        return;
      }

      setState(() => _isResending = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  void _startCooldown(Duration? duration) {
    _cooldownTimer?.cancel();
    final normalized = _normalizeCooldown(duration);
    if (normalized == Duration.zero) {
      if (mounted) {
        setState(() => _remainingCooldown = Duration.zero);
      } else {
        _remainingCooldown = Duration.zero;
      }
      return;
    }

    if (mounted) {
      setState(() => _remainingCooldown = normalized);
    } else {
      _remainingCooldown = normalized;
    }

    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }

      if (_remainingCooldown <= const Duration(seconds: 1)) {
        timer.cancel();
        setState(() => _remainingCooldown = Duration.zero);
        return;
      }

      setState(() => _remainingCooldown -= const Duration(seconds: 1));
    });
  }

  Duration _normalizeCooldown(Duration? duration) {
    if (duration == null || duration <= Duration.zero) {
      return Duration.zero;
    }
    return Duration(seconds: duration.inSeconds);
  }

  String _formatCooldown(Duration cooldown) {
    final minutes = cooldown.inMinutes;
    final seconds = cooldown.inSeconds.remainder(60);
    final paddedSeconds = seconds.toString().padLeft(2, '0');
    return '$minutes:$paddedSeconds';
  }

  void _handleSecondaryAction() {
    if (widget.variant == EmailVerificationPendingVariant.loginBlocked) {
      Navigator.of(context).pop();
      return;
    }
    Navigator.of(context).popUntil((route) => route.isFirst);
  }

  void _handleTertiaryAction() {
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final content = _content;

    return OnboardingScaffold(
      key: EmailVerificationPendingScreen.screenKey,
      header: OnboardingHeader(
        onBack: _handleTertiaryAction,
        brandColor: OnboardingPalette.passenger,
      ),
      topPadding:
          widget.variant == EmailVerificationPendingVariant.loginBlocked
              ? 24
              : 42,
      bottomPadding: content.showSupportCard ? 36 : 48,
      blurDots: [
        OnboardingBlurDot(
          color: content.accentColor,
          size: 220,
          top: 48,
          right: -96,
        ),
      ],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            height:
                widget.variant == EmailVerificationPendingVariant.loginBlocked
                    ? 8
                    : 20,
          ),
          Center(
            child: _VerificationHero(
              accentColor: content.accentColor,
              icon: content.heroIcon,
              showSecurityBadge: content.showSecurityBadge,
            ),
          ),
          const SizedBox(height: 40),
          Text(
            content.title,
            textAlign: TextAlign.center,
            style: theme.textTheme.displaySmall?.copyWith(
              color: OnboardingPalette.textStrong,
              fontWeight: FontWeight.w800,
              fontSize:
                  widget.variant == EmailVerificationPendingVariant.loginBlocked
                      ? 44
                      : 30,
              height:
                  widget.variant == EmailVerificationPendingVariant.loginBlocked
                      ? 1.1
                      : 1.2,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            content.description,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyLarge?.copyWith(
              color: OnboardingPalette.textMuted,
              fontWeight: FontWeight.w500,
              height: 1.6,
            ),
          ),
          const SizedBox(height: 28),
          _EmailHighlightCard(
            label: content.emailLabel,
            email: widget.email,
            accentColor: content.accentColor,
            borderColor: content.secondaryBorderColor,
          ),
          if (_feedbackMessage != null) ...[
            const SizedBox(height: 20),
            _FeedbackBanner(
              key: EmailVerificationPendingScreen.successBannerKey,
              message: _feedbackMessage!,
            ),
          ],
          const SizedBox(height: 28),
          _VerificationActionButton(
            buttonKey: EmailVerificationPendingScreen.resendButtonKey,
            label:
                _isResending
                    ? AppStrings.passageiro.reenviando
                    : _remainingCooldown > Duration.zero
                    ? AppStrings.passageiro.reenviarEm(
                      _formatCooldown(_remainingCooldown),
                    )
                    : AppStrings.passageiro.reenviarEmail,
            onPressed: _isResendBlocked ? null : _handleResend,
            gradient: content.primaryGradient,
            foregroundColor: Colors.white,
            showArrow: true,
            radius:
                widget.variant == EmailVerificationPendingVariant.loginBlocked
                    ? 24
                    : 12,
          ),
          if (_remainingCooldown > Duration.zero) ...[
            const SizedBox(height: 12),
            Text(
              AppStrings.passageiro.novoEnvioEm(
                _formatCooldown(_remainingCooldown),
              ),
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: OnboardingPalette.textMuted,
                fontWeight: FontWeight.w500,
                height: 1.4,
              ),
            ),
          ],
          const SizedBox(height: 16),
          _VerificationActionButton(
            buttonKey: EmailVerificationPendingScreen.secondaryButtonKey,
            label: content.secondaryLabel,
            onPressed: _handleSecondaryAction,
            backgroundColor: OnboardingPalette.surfaceRaised,
            borderColor: content.secondaryBorderColor,
            foregroundColor: content.accentColor,
            radius:
                widget.variant == EmailVerificationPendingVariant.loginBlocked
                    ? 24
                    : 12,
          ),
          const SizedBox(height: 16),
          Center(
            child: TextButton(
              key: EmailVerificationPendingScreen.tertiaryButtonKey,
              onPressed: _handleTertiaryAction,
              style: TextButton.styleFrom(
                foregroundColor: OnboardingPalette.textMuted,
                textStyle: theme.textTheme.bodyMedium?.copyWith(
                  fontWeight: FontWeight.w500,
                ),
              ),
              child: Text(content.tertiaryLabel),
            ),
          ),
          if (content.note != null) ...[
            const SizedBox(height: 28),
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 360),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: OnboardingPalette.surfaceRaised.withValues(
                      alpha: 0.74,
                    ),
                    borderRadius: BorderRadius.circular(999),
                    border: Border.all(
                      color: OnboardingPalette.border.withValues(alpha: 0.36),
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.info_outline_rounded,
                        size: 16,
                        color: OnboardingPalette.textMuted,
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          content.note!,
                          textAlign: TextAlign.center,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: OnboardingPalette.textMuted,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
          if (content.showSupportCard) ...[
            const SizedBox(height: 96),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: OnboardingPalette.surface,
                borderRadius: BorderRadius.circular(24),
                border: Border.all(
                  color: OnboardingPalette.border.withValues(alpha: 0.45),
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: const BoxDecoration(
                      color: OnboardingPalette.surfaceRaised,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.support_agent_rounded,
                      color: OnboardingPalette.passenger,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          AppStrings.passageiro.ajudaPrecisa,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: OnboardingPalette.textMuted,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 0.8,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          AppStrings.passageiro.suporteVanRides,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: OnboardingPalette.textStrong,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const Icon(
                    Icons.chevron_right_rounded,
                    color: OnboardingPalette.textMuted,
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _EmailVerificationPendingContent {
  const _EmailVerificationPendingContent({
    required this.title,
    required this.description,
    required this.accentColor,
    required this.softAccentColor,
    required this.primaryGradient,
    required this.secondaryBorderColor,
    required this.emailLabel,
    required this.secondaryLabel,
    required this.tertiaryLabel,
    required this.heroIcon,
    this.note,
    this.showSecurityBadge = false,
    this.showSupportCard = false,
  });

  final String title;
  final String description;
  final Color accentColor;
  final Color softAccentColor;
  final Gradient primaryGradient;
  final Color secondaryBorderColor;
  final String emailLabel;
  final String secondaryLabel;
  final String tertiaryLabel;
  final String? note;
  final IconData heroIcon;
  final bool showSecurityBadge;
  final bool showSupportCard;
}

class _VerificationHero extends StatelessWidget {
  const _VerificationHero({
    required this.accentColor,
    required this.icon,
    required this.showSecurityBadge,
  });

  final Color accentColor;
  final IconData icon;
  final bool showSecurityBadge;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 152,
      height: 152,
      child: Stack(
        clipBehavior: Clip.none,
        alignment: Alignment.center,
        children: [
          Container(
            width: 128,
            height: 128,
            decoration: BoxDecoration(
              color: accentColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(999),
              boxShadow: [
                BoxShadow(
                  color: accentColor.withValues(alpha: 0.06),
                  blurRadius: 32,
                  spreadRadius: 8,
                ),
              ],
            ),
          ),
          Container(
            width: 128,
            height: 128,
            decoration: BoxDecoration(
              color: OnboardingPalette.surface,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(
                color: OnboardingPalette.border.withValues(alpha: 0.45),
              ),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x3310161F),
                  blurRadius: 32,
                  offset: Offset(0, 12),
                ),
              ],
            ),
            child: Icon(icon, size: 50, color: accentColor),
          ),
          if (showSecurityBadge)
            Positioned(
              right: 8,
              top: 0,
              child: Container(
                width: 40,
                height: 40,
                decoration: const BoxDecoration(
                  color: Color(0xFFD92E6B),
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: Color(0x33191C1E),
                      blurRadius: 16,
                      offset: Offset(0, 8),
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.lock_outline_rounded,
                  color: Colors.white,
                  size: 18,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _EmailHighlightCard extends StatelessWidget {
  const _EmailHighlightCard({
    required this.label,
    required this.email,
    required this.accentColor,
    required this.borderColor,
  });

  final String label;
  final String email;
  final Color accentColor;
  final Color borderColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 21),
      decoration: BoxDecoration(
        color: OnboardingPalette.surface,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: borderColor.withValues(alpha: 0.28)),
      ),
      child: Column(
        children: [
          Text(
            label,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: OnboardingPalette.textMuted,
              fontWeight: FontWeight.w700,
              letterSpacing: 1,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            email,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: accentColor,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _VerificationActionButton extends StatelessWidget {
  const _VerificationActionButton({
    this.buttonKey,
    required this.label,
    required this.onPressed,
    required this.foregroundColor,
    this.gradient,
    this.backgroundColor,
    this.borderColor,
    this.showArrow = false,
    this.radius = 16,
  });

  final Key? buttonKey;
  final String label;
  final VoidCallback? onPressed;
  final Color foregroundColor;
  final Gradient? gradient;
  final Color? backgroundColor;
  final Color? borderColor;
  final bool showArrow;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final isDisabled = onPressed == null;
    final hasGradient = gradient != null && !isDisabled;
    final shadowColor =
        hasGradient ? const Color(0x33191C1E) : Colors.transparent;
    final effectiveBackgroundColor =
        isDisabled ? OnboardingPalette.surfaceRaised : backgroundColor;
    final effectiveBorderColor =
        isDisabled
            ? OnboardingPalette.border.withValues(alpha: 0.36)
            : borderColor;
    final effectiveForegroundColor =
        isDisabled ? OnboardingPalette.textMuted : foregroundColor;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        key: buttonKey,
        onTap: onPressed,
        borderRadius: BorderRadius.circular(radius),
        child: Ink(
          height: 56,
          decoration: BoxDecoration(
            color: effectiveBackgroundColor,
            gradient: hasGradient ? gradient : null,
            borderRadius: BorderRadius.circular(radius),
            border:
                effectiveBorderColor == null
                    ? null
                    : Border.all(color: effectiveBorderColor),
            boxShadow: [
              BoxShadow(
                color: shadowColor,
                blurRadius: 15,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Wrap(
                alignment: WrapAlignment.center,
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                children: [
                  Text(
                    label,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: effectiveForegroundColor,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  if (showArrow)
                    Icon(
                      Icons.send_outlined,
                      size: 18,
                      color: effectiveForegroundColor,
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
