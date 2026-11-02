import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/app/experience/experience_home_router.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/cadastro_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/escolha_cadastro_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/driver_invite_screen.dart';
import 'package:vanrides_frontend_dev/features/notifications/data/push_notification_session_binder.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/login_screen_fields.dart';
import 'package:vanrides_frontend_dev/services/api_constants.dart';
import 'package:vanrides_frontend_dev/services/public_auth_support.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({
    super.key,
    this.client,
    this.initialInviteCode,
    this.initialDriverInviteToken,
  });

  static const emailFieldKey = Key('login.email');
  static const senhaFieldKey = Key('login.senha');
  static const lembrarCheckboxKey = Key('login.lembrar');
  static const entrarButtonKey = Key('login.entrar');
  static const criarContaButtonKey = Key('login.criarConta');

  final http.Client? client;
  final String? initialInviteCode;
  final String? initialDriverInviteToken;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _senhaController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _carregando = false;
  bool _lembrar = false;
  late final http.Client _client;
  late final AppSessionStore _sessionStore;

  @override
  void initState() {
    super.initState();
    _client = widget.client ?? http.Client();
    _sessionStore = AppSessionStore();
    _carregarDadosSalvos();
    if (widget.initialInviteCode != null &&
        widget.initialInviteCode!.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppStrings.passageiro.conviteAposLogin(widget.initialInviteCode!),
            ),
          ),
        );
      });
    }
    if (widget.initialDriverInviteToken != null &&
        widget.initialDriverInviteToken!.trim().isNotEmpty) {
      _sessionStore.writePendingDriverInviteToken(
        widget.initialDriverInviteToken!,
      );
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Entre ou crie sua conta de motorista para aceitar o convite.',
            ),
          ),
        );
      });
    }
  }

  void _updateState(VoidCallback update) {
    if (!mounted) {
      return;
    }
    setState(update);
  }

  Future<void> _carregarDadosSalvos() async {
    final rememberedLogin = await _sessionStore.readRememberedLogin();
    if (rememberedLogin == null) {
      return;
    }

    _updateState(() {
      _lembrar = true;
      _emailController.text = rememberedLogin.email;
      _senhaController.text = rememberedLogin.password;
    });
  }

  Future<void> _salvarDados() async {
    await _sessionStore.writeRememberedLogin(
      enabled: _lembrar,
      email: _emailController.text.trim(),
      password: _senhaController.text.trim(),
    );
  }

  Future<void> _realizarLogin() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    final email = _emailController.text.trim().toLowerCase();
    final senha = _senhaController.text.trim();
    final url = Uri.parse('$baseUrl/login');

    _updateState(() => _carregando = true);

    try {
      final response = await _client.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'email': email, 'senha': senha}),
      );
      final responseBody = decodePublicAuthResponseBody(response);

      if (!mounted) {
        return;
      }

      _updateState(() => _carregando = false);

      if (response.statusCode != 200) {
        final error = PublicAuthApiError.tryParse(responseBody);
        if (error?.isEmailNotVerified == true) {
          await Navigator.push(
            context,
            MaterialPageRoute(
              builder:
                  (_) => EmailVerificationPendingScreen(
                    email: error?.email ?? email,
                    variant: EmailVerificationPendingVariant.loginBlocked,
                  ),
            ),
          );
          return;
        }

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              error?.message ?? AppStrings.passageiro.naoFoiPossivelFazerLogin,
            ),
          ),
        );
        return;
      }

      final decoded = jsonDecode(responseBody);
      if (decoded is! Map<String, dynamic>) {
        throw const FormatException('Resposta de login fora do contrato.');
      }
      final data = decoded;
      final sessionUser = AppSessionUser.fromLoginResponse(
        data: data,
        email: email,
        password: senha,
      );

      await _salvarDados();
      await _sessionStore.writeCurrent(sessionUser);

      if (!mounted) {
        return;
      }

      final accessToken = sessionUser.accessToken;
      if (accessToken != null && accessToken.isNotEmpty) {
        unawaited(
          PushNotificationSessionBinder().bindSession(
            authorizationHeader: 'Bearer $accessToken',
          ),
        );
      }

      final pendingDriverInviteToken =
          _nonEmpty(widget.initialDriverInviteToken) ??
          await _sessionStore.readPendingDriverInviteToken();
      if (!mounted) {
        return;
      }
      if (sessionUser.role == AppUserRole.driver &&
          pendingDriverInviteToken != null) {
        unawaited(
          Navigator.pushReplacement(
            context,
            MaterialPageRoute(
              builder:
                  (_) => DriverInviteScreen(
                    token: pendingDriverInviteToken,
                    sessionUser: sessionUser,
                  ),
            ),
          ),
        );
        return;
      }

      unawaited(
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => ExperienceHomeRouter(sessionUser: sessionUser),
          ),
        ),
      );
    } on http.ClientException catch (error, stackTrace) {
      _logLoginException(error, stackTrace);
      _showLoginError(AppStrings.network.semConexao);
    } on FormatException catch (error, stackTrace) {
      _logLoginException(error, stackTrace);
      _showLoginError(AppStrings.passageiro.respostaInvalidaDoServidor);
    } on TypeError catch (error, stackTrace) {
      _logLoginException(error, stackTrace);
      _showLoginError(AppStrings.passageiro.respostaInvalidaDoServidor);
    } catch (error, stackTrace) {
      _logLoginException(error, stackTrace);
      _showLoginError(AppStrings.passageiro.naoFoiPossivelConcluirLogin);
    }
  }

  void _showLoginError(String message) {
    if (!mounted) {
      return;
    }
    _updateState(() => _carregando = false);
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  void _logLoginException(Object error, StackTrace stackTrace) {
    debugPrint('[login] falha inesperada ao concluir login: $error');
    debugPrintStack(stackTrace: stackTrace);
  }

  @override
  void dispose() {
    if (widget.client == null) {
      _client.close();
    }
    _emailController.dispose();
    _senhaController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: VanRidesDesignTokens.backgroundDeep,
      body: Container(
        key: const Key('login.darkSurface'),
        color: VanRidesDesignTokens.backgroundDeep,
        child: DecoratedBox(
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
                  constraints: const BoxConstraints(maxWidth: 420),
                  child: Column(
                    children: [
                      Container(
                        width: 84,
                        height: 84,
                        decoration: BoxDecoration(
                          gradient: const LinearGradient(
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                            colors: [
                              VanRidesDesignTokens.blueTracking,
                              VanRidesDesignTokens.greenAction,
                            ],
                          ),
                          borderRadius: BorderRadius.circular(28),
                          boxShadow: const [
                            BoxShadow(
                              color: Color(0x334EB7F2),
                              blurRadius: 26,
                              offset: Offset(0, 16),
                            ),
                          ],
                        ),
                        child: const Icon(
                          Icons.directions_bus_rounded,
                          size: 40,
                          color: VanRidesDesignTokens.backgroundDeep,
                        ),
                      ),
                      const SizedBox(height: 24),
                      Text(
                        AppStrings.passageiro.appName,
                        style: theme.textTheme.displaySmall?.copyWith(
                          color: VanRidesDesignTokens.textPrimary,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        AppStrings.passageiro.entrarParaAcompanharOperar,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.titleMedium?.copyWith(
                          color: VanRidesDesignTokens.textSecondary,
                          height: 1.4,
                        ),
                      ),
                      const SizedBox(height: 28),
                      Container(
                        key: const Key('login.form.darkSurface'),
                        padding: const EdgeInsets.all(24),
                        decoration: BoxDecoration(
                          color: VanRidesDesignTokens.surface,
                          borderRadius: BorderRadius.circular(30),
                          border: Border.all(
                            color: VanRidesDesignTokens.border.withValues(
                              alpha: 0.34,
                            ),
                          ),
                          boxShadow: const [
                            BoxShadow(
                              color: Color(0x66070C12),
                              blurRadius: 30,
                              offset: Offset(0, 18),
                            ),
                          ],
                        ),
                        child: Form(
                          key: _formKey,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                AppStrings.passageiro.acessarConta,
                                style: theme.textTheme.headlineSmall?.copyWith(
                                  color: VanRidesDesignTokens.textPrimary,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                AppStrings.passageiro.useMesmoLoginPublicado,
                                style: theme.textTheme.bodyLarge?.copyWith(
                                  color: VanRidesDesignTokens.textSecondary,
                                  height: 1.45,
                                ),
                              ),
                              const SizedBox(height: 20),
                              TextFormField(
                                key: LoginScreen.emailFieldKey,
                                controller: _emailController,
                                keyboardType: TextInputType.emailAddress,
                                textInputAction: TextInputAction.next,
                                cursorColor: VanRidesDesignTokens.blueTracking,
                                style: theme.textTheme.bodyLarge?.copyWith(
                                  color: VanRidesDesignTokens.textPrimary,
                                ),
                                decoration: loginInputDecoration(
                                  label: AppStrings.passageiro.email,
                                  icon: Icons.alternate_email_rounded,
                                ),
                                validator: (value) {
                                  return value != null && value.contains('@')
                                      ? null
                                      : AppStrings.passageiro.emailInvalido;
                                },
                              ),
                              const SizedBox(height: 16),
                              TextFormField(
                                key: LoginScreen.senhaFieldKey,
                                controller: _senhaController,
                                obscureText: true,
                                textInputAction: TextInputAction.done,
                                onFieldSubmitted: (_) => _realizarLogin(),
                                cursorColor: VanRidesDesignTokens.blueTracking,
                                style: theme.textTheme.bodyLarge?.copyWith(
                                  color: VanRidesDesignTokens.textPrimary,
                                ),
                                decoration: loginInputDecoration(
                                  label: AppStrings.passageiro.senha,
                                  icon: Icons.lock_outline_rounded,
                                ),
                                validator: (value) {
                                  return value != null && value.length >= 6
                                      ? null
                                      : AppStrings.passageiro.minimo6Caracteres;
                                },
                              ),
                              const SizedBox(height: 12),
                              InkWell(
                                onTap: () {
                                  _updateState(() => _lembrar = !_lembrar);
                                },
                                borderRadius: BorderRadius.circular(16),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 6,
                                  ),
                                  child: Row(
                                    children: [
                                      Checkbox(
                                        key: LoginScreen.lembrarCheckboxKey,
                                        value: _lembrar,
                                        onChanged: (value) {
                                          _updateState(() {
                                            _lembrar = value ?? false;
                                          });
                                        },
                                        activeColor:
                                            VanRidesDesignTokens.greenAction,
                                        checkColor:
                                            VanRidesDesignTokens.backgroundDeep,
                                        side: BorderSide(
                                          color: VanRidesDesignTokens.border
                                              .withValues(alpha: 0.7),
                                        ),
                                        shape: RoundedRectangleBorder(
                                          borderRadius: BorderRadius.circular(
                                            6,
                                          ),
                                        ),
                                      ),
                                      const SizedBox(width: 4),
                                      Expanded(
                                        child: Text(
                                          AppStrings
                                              .passageiro
                                              .lembrarDeMimNesteAparelho,
                                          style: theme.textTheme.bodyMedium
                                              ?.copyWith(
                                                color:
                                                    VanRidesDesignTokens
                                                        .textSecondary,
                                                fontWeight: FontWeight.w600,
                                              ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                              const SizedBox(height: 16),
                              SizedBox(
                                width: double.infinity,
                                child:
                                    _carregando
                                        ? const Center(
                                          child: Padding(
                                            padding: EdgeInsets.symmetric(
                                              vertical: 14,
                                            ),
                                            child: CircularProgressIndicator(),
                                          ),
                                        )
                                        : FilledButton.icon(
                                          key: LoginScreen.entrarButtonKey,
                                          onPressed: _realizarLogin,
                                          icon: const Icon(Icons.login_rounded),
                                          label: Text(
                                            AppStrings.passageiro.entrar,
                                          ),
                                          style: FilledButton.styleFrom(
                                            minimumSize: const Size.fromHeight(
                                              58,
                                            ),
                                            backgroundColor:
                                                VanRidesDesignTokens
                                                    .blueTracking,
                                            foregroundColor:
                                                VanRidesDesignTokens.onAccent,
                                            shape: RoundedRectangleBorder(
                                              borderRadius:
                                                  BorderRadius.circular(20),
                                            ),
                                            textStyle: theme
                                                .textTheme
                                                .titleMedium
                                                ?.copyWith(
                                                  fontWeight: FontWeight.w800,
                                                ),
                                          ),
                                        ),
                              ),
                              const SizedBox(height: 12),
                              SizedBox(
                                width: double.infinity,
                                child: TextButton(
                                  key: LoginScreen.criarContaButtonKey,
                                  style: TextButton.styleFrom(
                                    foregroundColor:
                                        VanRidesDesignTokens.blueTracking,
                                  ),
                                  onPressed: () {
                                    final driverInviteToken = _nonEmpty(
                                      widget.initialDriverInviteToken,
                                    );
                                    if (driverInviteToken != null) {
                                      Navigator.push(
                                        context,
                                        MaterialPageRoute(
                                          builder:
                                              (_) => CadastroMotoristaScreen(
                                                initialInviteToken:
                                                    driverInviteToken,
                                              ),
                                        ),
                                      );
                                      return;
                                    }
                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(
                                        builder:
                                            (_) => EscolhaCadastroScreen(
                                              initialDriverInviteToken:
                                                  widget
                                                      .initialDriverInviteToken,
                                            ),
                                      ),
                                    );
                                  },
                                  child: Text(AppStrings.passageiro.criarConta),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 18),
                      Text(
                        AppStrings.passageiro.loginFooterDescricao,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: VanRidesDesignTokens.textMuted,
                          height: 1.45,
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

  String? _nonEmpty(String? value) {
    final normalized = value?.trim();
    if (normalized == null || normalized.isEmpty) {
      return null;
    }
    return normalized;
  }
}
