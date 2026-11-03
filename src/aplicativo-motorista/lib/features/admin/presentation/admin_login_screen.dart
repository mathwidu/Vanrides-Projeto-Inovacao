import 'dart:async';

import 'package:flutter/material.dart';

import '../../../services/public_auth_support.dart';
import '../data/admin_portal_auth_service.dart';
import '../domain/admin_portal_exception.dart';
import 'admin_profile_shell_route.dart';
import 'widgets/admin_login_form_fields.dart';
import 'widgets/admin_login_bootstrap_dialog.dart';
import 'widgets/admin_login_hero.dart';
import 'widgets/admin_pending_verification_widgets.dart';
import 'widgets/admin_portal_layout.dart';

class AdminLoginScreen extends StatefulWidget {
  const AdminLoginScreen({
    super.key,
    this.gateway,
    this.accountProfileGatewayBuilder,
  });

  static const emailFieldKey = Key('admin.login.email');
  static const passwordFieldKey = Key('admin.login.password');
  static const enterButtonKey = Key('admin.login.enter');
  static const bootstrapButtonKey = Key('admin.login.bootstrap');

  final AdminPortalAuthGateway? gateway;
  final AdminProfileGatewayBuilder? accountProfileGatewayBuilder;

  @override
  State<AdminLoginScreen> createState() => _AdminLoginScreenState();
}

class _AdminLoginScreenState extends State<AdminLoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  late final AdminPortalAuthGateway _gateway;
  bool _isSubmitting = false;
  bool _isPendingEmailAction = false;
  String? _errorMessage;
  String? _pendingVerificationEmail;
  String? _pendingVerificationPassword;
  String? _pendingVerificationFeedback;
  final ValueNotifier<Duration> _pendingVerificationCooldown = ValueNotifier(
    Duration.zero,
  );
  Timer? _pendingVerificationCooldownTimer;

  bool get _isPendingVerificationResendBlocked =>
      _isPendingEmailAction ||
      _pendingVerificationCooldown.value > Duration.zero;

  @override
  void initState() {
    super.initState();
    _gateway = widget.gateway ?? AdminPortalAuthService();
  }

  @override
  void dispose() {
    _pendingVerificationCooldownTimer?.cancel();
    _pendingVerificationCooldown.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final session = await _gateway.login(
        email: _emailController.text.trim(),
        senha: _passwordController.text,
      );

      if (!mounted) {
        return;
      }

      unawaited(
        Navigator.of(context).pushReplacement(
          buildAdminShellRoute(
            session: session,
            senha: _passwordController.text,
            accountProfileGatewayBuilder: widget.accountProfileGatewayBuilder,
          ),
        ),
      );
    } on AdminPortalException catch (error) {
      setState(() {
        if (error.isEmailNotVerified) {
          _pendingVerificationEmail =
              error.email ?? _emailController.text.trim().toLowerCase();
          _pendingVerificationPassword = _passwordController.text;
          _pendingVerificationFeedback = null;
          _pendingVerificationCooldown.value = Duration.zero;
        } else {
          _pendingVerificationEmail = null;
          _pendingVerificationPassword = null;
          _pendingVerificationFeedback = null;
        }
        _errorMessage = error.message;
        _isSubmitting = false;
      });
    } catch (_) {
      setState(() {
        _errorMessage = 'Nao foi possivel autenticar o portal agora.';
        _isSubmitting = false;
      });
    }
  }

  Future<void> _resendPendingVerification() async {
    final email = _pendingVerificationEmail;
    if (email == null || email.trim().isEmpty) {
      return;
    }
    if (_isPendingVerificationResendBlocked) {
      return;
    }

    setState(() => _isPendingEmailAction = true);
    try {
      final message = await _gateway.resendVerification(email: email);
      if (!mounted) {
        return;
      }
      setState(() {
        _pendingVerificationFeedback = message;
        _errorMessage = null;
        _isPendingEmailAction = false;
      });
      _startPendingVerificationCooldown(emailVerificationResendCooldown);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    } on AdminPortalException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _errorMessage = error.message;
        _isPendingEmailAction = false;
      });
    }
  }

  Future<void> _openChangeEmailDialog() async {
    final currentEmail = _pendingVerificationEmail;
    final currentPassword = _pendingVerificationPassword;
    if (currentEmail == null ||
        currentEmail.trim().isEmpty ||
        currentPassword == null ||
        currentPassword.isEmpty) {
      return;
    }

    final newEmail = await showDialog<String>(
      context: context,
      builder:
          (context) =>
              AdminChangePendingEmailDialog(currentEmail: currentEmail),
    );
    if (newEmail == null || newEmail.trim().isEmpty) {
      return;
    }

    setState(() => _isPendingEmailAction = true);
    try {
      final updatedEmail = await _gateway.changePendingEmail(
        currentEmail: currentEmail,
        senha: currentPassword,
        newEmail: newEmail,
      );
      if (!mounted) {
        return;
      }
      _emailController.text = updatedEmail;
      setState(() {
        _pendingVerificationEmail = updatedEmail;
        _pendingVerificationFeedback =
            'E-mail pendente atualizado. Reenviamos uma nova confirmação.';
        _errorMessage = null;
        _isPendingEmailAction = false;
      });
      _startPendingVerificationCooldown(emailVerificationResendCooldown);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('E-mail atualizado e nova verificação enviada.'),
        ),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _errorMessage = error.message;
        _isPendingEmailAction = false;
      });
    }
  }

  void _exitPendingVerificationState() {
    setState(() {
      _pendingVerificationEmail = null;
      _pendingVerificationPassword = null;
      _pendingVerificationFeedback = null;
      _errorMessage = null;
    });
    _startPendingVerificationCooldown(null);
  }

  void _startPendingVerificationCooldown(Duration? duration) {
    _pendingVerificationCooldownTimer?.cancel();
    final normalized =
        duration == null || duration <= Duration.zero
            ? Duration.zero
            : Duration(seconds: duration.inSeconds);
    _pendingVerificationCooldown.value = normalized;
    if (normalized == Duration.zero) {
      return;
    }

    _pendingVerificationCooldownTimer = Timer.periodic(
      const Duration(seconds: 1),
      (timer) {
        final current = _pendingVerificationCooldown.value;
        if (current <= const Duration(seconds: 1)) {
          timer.cancel();
          _pendingVerificationCooldown.value = Duration.zero;
          return;
        }
        _pendingVerificationCooldown.value =
            current - const Duration(seconds: 1);
      },
    );
  }

  Future<void> _openBootstrapDialog() async {
    final result = await showDialog<AdminLoginBootstrapResult>(
      context: context,
      builder: (context) => AdminLoginBootstrapDialog(gateway: _gateway),
    );

    if (result == null) {
      return;
    }

    _emailController.text = result.bootstrap.email;
    _passwordController.text = result.password;

    if (!mounted) {
      return;
    }

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Primeiro admin criado para ${result.bootstrap.companyDisplayName}. Agora é só entrar no portal.',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AdminPortalColors.canvas,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 1440),
            child: LayoutBuilder(
              builder: (context, constraints) {
                final stacked = constraints.maxWidth < 1140;
                final hero = const AdminLoginHero();
                final showPendingVerification =
                    _pendingVerificationEmail != null &&
                    _pendingVerificationPassword != null;
                final form =
                    showPendingVerification
                        ? AdminPendingVerificationForm(
                          email: _pendingVerificationEmail!,
                          isSubmitting: _isSubmitting || _isPendingEmailAction,
                          errorMessage: _errorMessage,
                          feedbackMessage: _pendingVerificationFeedback,
                          resendCooldownListenable:
                              _pendingVerificationCooldown,
                          onResendVerification: _resendPendingVerification,
                          onChangeEmail: _openChangeEmailDialog,
                          onBackToLogin: _exitPendingVerificationState,
                        )
                        : _AdminLoginForm(
                          formKey: _formKey,
                          emailController: _emailController,
                          passwordController: _passwordController,
                          isSubmitting: _isSubmitting,
                          errorMessage: _errorMessage,
                          onSubmit: _submit,
                          onBootstrap: _openBootstrapDialog,
                        );

                if (stacked) {
                  return SingleChildScrollView(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [hero, const SizedBox(height: 24), form],
                    ),
                  );
                }

                return Padding(
                  padding: const EdgeInsets.all(24),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Expanded(flex: 6, child: AdminLoginHero()),
                      const SizedBox(width: 24),
                      Expanded(flex: 5, child: form),
                    ],
                  ),
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}

class _AdminLoginForm extends StatelessWidget {
  const _AdminLoginForm({
    required this.formKey,
    required this.emailController,
    required this.passwordController,
    required this.isSubmitting,
    required this.onSubmit,
    required this.onBootstrap,
    this.errorMessage,
  });

  final GlobalKey<FormState> formKey;
  final TextEditingController emailController;
  final TextEditingController passwordController;
  final bool isSubmitting;
  final String? errorMessage;
  final VoidCallback onSubmit;
  final Future<void> Function() onBootstrap;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 48,
      padding: const EdgeInsets.fromLTRB(36, 34, 36, 34),
      child: Form(
        key: formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Entrar no portal',
              style: TextStyle(
                color: AdminPortalColors.text,
                fontSize: 34,
                fontWeight: FontWeight.w800,
                height: 1.15,
              ),
            ),
            const SizedBox(height: 10),
            const Text(
              'Use um login administrativo real. Se esta empresa ainda não tiver um primeiro admin, faça o bootstrap logo abaixo.',
              style: TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 16,
                height: 1.6,
              ),
            ),
            const SizedBox(height: 28),
            const AdminLoginFieldLabel(label: 'E-mail administrativo'),
            const SizedBox(height: 8),
            TextFormField(
              key: AdminLoginScreen.emailFieldKey,
              controller: emailController,
              decoration: adminLoginInputDecoration(
                hintText: 'admin@empresa.com',
              ),
              validator: (value) {
                return value != null && value.contains('@')
                    ? null
                    : 'Informe um e-mail válido';
              },
            ),
            const SizedBox(height: 20),
            const AdminLoginFieldLabel(label: 'Senha'),
            const SizedBox(height: 8),
            TextFormField(
              key: AdminLoginScreen.passwordFieldKey,
              controller: passwordController,
              obscureText: true,
              decoration: adminLoginInputDecoration(hintText: '••••••••'),
              validator: (value) {
                return value != null && value.length >= 8
                    ? null
                    : 'Mínimo de 8 caracteres';
              },
            ),
            if (errorMessage != null) ...[
              const SizedBox(height: 16),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AdminPortalColors.redSoft,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: AdminPortalColors.red.withValues(alpha: 0.24),
                  ),
                ),
                child: Text(
                  errorMessage!,
                  style: const TextStyle(
                    color: AdminPortalColors.red,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
            const SizedBox(height: 28),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                key: AdminLoginScreen.enterButtonKey,
                onPressed: isSubmitting ? null : onSubmit,
                style: FilledButton.styleFrom(
                  backgroundColor: AdminPortalColors.blueBright,
                  foregroundColor: AdminPortalColors.canvas,
                  padding: const EdgeInsets.symmetric(vertical: 18),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(999),
                  ),
                  textStyle: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                child:
                    isSubmitting
                        ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                        : const Text('Entrar no portal'),
              ),
            ),
            const SizedBox(height: 16),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                key: AdminLoginScreen.bootstrapButtonKey,
                onPressed: isSubmitting ? null : onBootstrap,
                icon: const Icon(Icons.verified_user_outlined),
                label: const Text('Primeiro acesso? Criar admin da empresa'),
                style: TextButton.styleFrom(
                  foregroundColor: AdminPortalColors.blueBright,
                  textStyle: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 15,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
