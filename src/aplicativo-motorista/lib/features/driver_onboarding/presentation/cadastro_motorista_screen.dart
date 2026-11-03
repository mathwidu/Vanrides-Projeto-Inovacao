import 'dart:async';
import 'package:flutter/material.dart';

import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/data/motorista_cadastro_gateway.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/onboarding_flow_components.dart';

class CadastroMotoristaScreen extends StatefulWidget {
  const CadastroMotoristaScreen({
    super.key,
    this.initialInviteToken,
    this.cadastroGateway,
  });

  static const screenKey = Key('motorista.cadastro.screen');
  static const nomeFieldKey = Key('motorista.cadastro.nome');
  static const emailFieldKey = Key('motorista.cadastro.email');
  static const telefoneFieldKey = Key('motorista.cadastro.telefone');
  static const dataNascimentoFieldKey = Key(
    'motorista.cadastro.dataNascimento',
  );
  static const senhaFieldKey = Key('motorista.cadastro.senha');
  static const confirmarSenhaFieldKey = Key(
    'motorista.cadastro.confirmarSenha',
  );
  static const cadastrarButtonKey = Key('motorista.cadastro.cadastrar');
  static const inviteContextKey = Key('motorista.cadastro.inviteContext');

  final String? initialInviteToken;
  final MotoristaCadastroGateway? cadastroGateway;

  @override
  State<CadastroMotoristaScreen> createState() =>
      _CadastroMotoristaScreenState();
}

class _CadastroMotoristaScreenState extends State<CadastroMotoristaScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nomeController = TextEditingController();
  final _emailController = TextEditingController();
  final _telefoneController = TextEditingController();
  final _dataNascimentoController = TextEditingController();
  final _senhaController = TextEditingController();
  final _confirmarSenhaController = TextEditingController();

  late final MotoristaCadastroGateway _cadastroGateway;
  bool _carregando = false;

  @override
  void initState() {
    super.initState();
    _cadastroGateway = widget.cadastroGateway ?? HttpMotoristaCadastroGateway();
    final token = widget.initialInviteToken?.trim();
    if (token != null && token.isNotEmpty) {
      unawaited(AppSessionStore().writePendingDriverInviteToken(token));
    }
  }

  bool senhaPossuiMaiuscula(String senha) => senha.contains(RegExp(r'[A-Z]'));
  bool senhaPossuiMinuscula(String senha) => senha.contains(RegExp(r'[a-z]'));
  bool senhaPossuiNumero(String senha) => senha.contains(RegExp(r'[0-9]'));
  bool senhaPossuiEspecial(String senha) =>
      senha.contains(RegExp(r'[!@#$%^&*(),.?":{}|<>]'));
  bool senhaPossuiMinCaracteres(String senha) => senha.length >= 8;

  bool senhaEhForte(String senha) {
    return senhaPossuiMaiuscula(senha) &&
        senhaPossuiMinuscula(senha) &&
        senhaPossuiNumero(senha) &&
        senhaPossuiEspecial(senha) &&
        senhaPossuiMinCaracteres(senha);
  }

  @override
  void dispose() {
    _nomeController.dispose();
    _emailController.dispose();
    _telefoneController.dispose();
    _dataNascimentoController.dispose();
    _senhaController.dispose();
    _confirmarSenhaController.dispose();
    super.dispose();
  }

  Future<void> _realizarCadastro() async {
    final senha = _senhaController.text;
    final confirmarSenha = _confirmarSenhaController.text;

    if (!_formKey.currentState!.validate()) {
      return;
    }

    if (!senhaEhForte(senha)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppStrings.passageiro.suaSenhaNaoAtendeRequisitos),
        ),
      );
      return;
    }

    if (senha != confirmarSenha) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.passageiro.asSenhasNaoCoincidem)),
      );
      return;
    }

    setState(() => _carregando = true);

    final payload = MotoristaCadastroPayload(
      nome: _nomeController.text.trim(),
      email: _emailController.text.trim().toLowerCase(),
      telefone: _telefoneController.text.trim(),
      dataNascimento: VanRidesBirthDate.toIsoString(
        _dataNascimentoController.text,
      ),
      senha: senha,
    );

    final result = await _cadastroGateway.cadastrar(payload);

    if (!mounted) {
      return;
    }

    setState(() => _carregando = false);

    if (result is MotoristaCadastroOk) {
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder:
              (_) => EmailVerificationPendingScreen(
                email: payload.email,
                variant: EmailVerificationPendingVariant.driverSignup,
                initialResendCooldown: result.initialResendCooldown,
              ),
        ),
      );
      return;
    }

    if (result is MotoristaCadastroValidationError) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(result.message)));
      return;
    }

    if (result is MotoristaCadastroNetworkError) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(result.message)));
    }
  }

  void _voltarParaLogin() {
    Navigator.of(context).popUntil((route) => route.isFirst);
  }

  @override
  Widget build(BuildContext context) {
    final senha = _senhaController.text;

    return OnboardingScaffold(
      key: CadastroMotoristaScreen.screenKey,
      header: OnboardingHeader(
        onBack: () => Navigator.maybePop(context),
        badgeText: AppStrings.motorista.cadastroMotoristaBadge,
        badgeColor: OnboardingPalette.driver,
      ),
      topPadding: 16,
      blurDots: const [
        OnboardingBlurDot(
          color: OnboardingPalette.passenger,
          size: 220,
          left: -80,
          top: -40,
        ),
        OnboardingBlurDot(
          color: OnboardingPalette.driver,
          size: 280,
          right: -100,
          bottom: 120,
        ),
      ],
      footer: _buildCadastroFooter(),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            OnboardingSectionHeader(
              title: AppStrings.motorista.entreParaAFrota,
              subtitle: AppStrings.motorista.perfilProfissionalDescricao,
            ),
            const SizedBox(height: 18),
            const OnboardingStepProgress(
              steps: [
                'Dados profissionais',
                'Acesso',
                'Empresa',
                'Verificação',
              ],
              accentColor: OnboardingPalette.driver,
            ),
            const SizedBox(height: 28),
            if (widget.initialInviteToken?.trim().isNotEmpty == true) ...[
              Container(
                key: CadastroMotoristaScreen.inviteContextKey,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: OnboardingPalette.driverSoft.withValues(alpha: 0.62),
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(
                    color: OnboardingPalette.driver.withValues(alpha: 0.28),
                  ),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.link_rounded,
                      color: OnboardingPalette.driver,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        'Convite da empresa detectado. Depois de confirmar seu e-mail, entre no app para concluir o vínculo automaticamente.',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: OnboardingPalette.textStrong,
                          fontWeight: FontWeight.w700,
                          height: 1.35,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),
            ],
            _buildField(
              label: AppStrings.motorista.nomeCompleto,
              child: TextFormField(
                key: CadastroMotoristaScreen.nomeFieldKey,
                controller: _nomeController,
                textInputAction: TextInputAction.next,
                decoration: onboardingInputDecoration(
                  hintText: AppStrings.motorista.exemploNomeMotorista,
                ),
                validator:
                    (value) =>
                        value!.trim().isEmpty
                            ? AppStrings.passageiro.campoObrigatorio
                            : null,
              ),
            ),
            const SizedBox(height: 16),
            _buildField(
              label: AppStrings.passageiro.email,
              child: TextFormField(
                key: CadastroMotoristaScreen.emailFieldKey,
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                decoration: onboardingInputDecoration(
                  hintText: AppStrings.passageiro.joaoSilvaExemplo,
                ),
                validator:
                    (value) =>
                        value != null && value.contains('@')
                            ? null
                            : AppStrings.passageiro.emailInvalido,
              ),
            ),
            const SizedBox(height: 16),
            _buildField(
              label: AppStrings.passageiro.telefone,
              child: TextFormField(
                key: CadastroMotoristaScreen.telefoneFieldKey,
                controller: _telefoneController,
                keyboardType: TextInputType.phone,
                textInputAction: TextInputAction.next,
                decoration: onboardingInputDecoration(
                  hintText: '+55 (11) 99999-9999',
                ),
                validator:
                    (value) =>
                        value!.trim().isEmpty
                            ? AppStrings.passageiro.campoObrigatorio
                            : null,
              ),
            ),
            const SizedBox(height: 16),
            _buildField(
              label: 'Data de nascimento',
              child: TextFormField(
                key: CadastroMotoristaScreen.dataNascimentoFieldKey,
                controller: _dataNascimentoController,
                keyboardType: TextInputType.datetime,
                textInputAction: TextInputAction.next,
                decoration: onboardingInputDecoration(
                  hintText: 'dd/mm/aaaa',
                  suffixIcon: const Icon(
                    Icons.cake_outlined,
                    color: OnboardingPalette.textMuted,
                  ),
                ),
                validator: VanRidesBirthDate.validate,
              ),
            ),
            const SizedBox(height: 16),
            _buildField(
              label: AppStrings.passageiro.senha,
              child: TextFormField(
                key: CadastroMotoristaScreen.senhaFieldKey,
                controller: _senhaController,
                obscureText: true,
                textInputAction: TextInputAction.next,
                decoration: onboardingInputDecoration(hintText: '••••••••'),
                onChanged: (_) => setState(() {}),
                validator:
                    (value) =>
                        value!.trim().isEmpty
                            ? AppStrings.passageiro.campoObrigatorio
                            : null,
              ),
            ),
            const SizedBox(height: 16),
            _buildField(
              label: AppStrings.passageiro.confirmarSenha,
              child: TextFormField(
                key: CadastroMotoristaScreen.confirmarSenhaFieldKey,
                controller: _confirmarSenhaController,
                obscureText: true,
                textInputAction: TextInputAction.done,
                onFieldSubmitted: (_) => _realizarCadastro(),
                decoration: onboardingInputDecoration(hintText: '••••••••'),
                validator:
                    (value) =>
                        value!.trim().isEmpty
                            ? AppStrings.passageiro.campoObrigatorio
                            : null,
              ),
            ),
            if (senha.isNotEmpty) ...[
              const SizedBox(height: 16),
              _PasswordRequirementsColumn(password: senha),
            ],
            const SizedBox(height: 24),
            Center(
              child: Text.rich(
                TextSpan(
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: OnboardingPalette.textMuted,
                    height: 1.5,
                  ),
                  children: [
                    TextSpan(text: AppStrings.motorista.termosMotoristaPrefix),
                    TextSpan(
                      text: AppStrings.motorista.termosParaMotoristas,
                      style: const TextStyle(
                        color: OnboardingPalette.driver,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    TextSpan(text: AppStrings.motorista.termosMotoristaMiddle),
                    TextSpan(
                      text: AppStrings.motorista.politicaDePrivacidade,
                      style: const TextStyle(
                        color: OnboardingPalette.driver,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const TextSpan(text: '.'),
                  ],
                ),
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 36),
            OnboardingFooterPrompt(
              prefix: AppStrings.motorista.jaTemUmaConta,
              actionLabel: AppStrings.passageiro.entrar,
              onTap: _voltarParaLogin,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildField({required String label, required Widget child}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [OnboardingFieldLabel(label), const SizedBox(height: 6), child],
    );
  }

  Widget _buildCadastroFooter() {
    if (_carregando) {
      return const Center(
        child: SizedBox.square(
          dimension: 28,
          child: CircularProgressIndicator(strokeWidth: 2.4),
        ),
      );
    }

    return OnboardingPrimaryButton(
      buttonKey: CadastroMotoristaScreen.cadastrarButtonKey,
      label: 'Criar conta e verificar e-mail',
      onPressed: _realizarCadastro,
      backgroundColor: OnboardingPalette.driver,
      showArrow: true,
    );
  }
}

class _PasswordRequirementsColumn extends StatelessWidget {
  const _PasswordRequirementsColumn({required this.password});

  final String password;

  bool get _hasUpper => password.contains(RegExp(r'[A-Z]'));
  bool get _hasLower => password.contains(RegExp(r'[a-z]'));
  bool get _hasNumber => password.contains(RegExp(r'[0-9]'));
  bool get _hasSpecial => password.contains(RegExp(r'[!@#$%^&*(),.?":{}|<>]'));
  bool get _hasLength => password.length >= 8;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: OnboardingPalette.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: OnboardingPalette.border.withValues(alpha: 0.75),
          ),
        ),
        child: Column(
          children: [
            OnboardingPasswordRequirement(
              label: AppStrings.passageiro.peloMenos8Caracteres,
              valid: _hasLength,
            ),
            const SizedBox(height: 8),
            OnboardingPasswordRequirement(
              label: AppStrings.passageiro.umaLetraMaiuscula,
              valid: _hasUpper,
            ),
            const SizedBox(height: 8),
            OnboardingPasswordRequirement(
              label: AppStrings.passageiro.umaLetraMinuscula,
              valid: _hasLower,
            ),
            const SizedBox(height: 8),
            OnboardingPasswordRequirement(
              label: AppStrings.passageiro.umNumero,
              valid: _hasNumber,
            ),
            const SizedBox(height: 8),
            OnboardingPasswordRequirement(
              label: AppStrings.passageiro.umCaractereEspecial,
              valid: _hasSpecial,
            ),
          ],
        ),
      ),
    );
  }
}
