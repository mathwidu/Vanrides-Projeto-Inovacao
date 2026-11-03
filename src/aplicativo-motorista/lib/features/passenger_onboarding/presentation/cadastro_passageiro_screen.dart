import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/data/passageiro_cadastro_gateway.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/map_picker.dart';
import 'package:vanrides_frontend_dev/onboarding_flow_components.dart';

part 'cadastro_passageiro_widgets.dart';

class CadastroPassageiroScreen extends StatefulWidget {
  const CadastroPassageiroScreen({
    super.key,
    this.cadastroGateway,
    this.placesGateway,
    this.openMapAfterAddressSelection = true,
  });

  static const screenKey = Key('passageiro.cadastro.screen');
  static const nomeFieldKey = Key('passageiro.cadastro.nome');
  static const sobrenomeFieldKey = Key('passageiro.cadastro.sobrenome');
  static const emailFieldKey = Key('passageiro.cadastro.email');
  static const telefoneFieldKey = Key('passageiro.cadastro.telefone');
  static const dataNascimentoFieldKey = Key(
    'passageiro.cadastro.dataNascimento',
  );
  static const senhaFieldKey = Key('passageiro.cadastro.senha');
  static const confirmarSenhaFieldKey = Key(
    'passageiro.cadastro.confirmarSenha',
  );
  static const buscarEnderecoFieldKey = Key(
    'passageiro.cadastro.buscarEndereco',
  );
  static const ajustarMapaButtonKey = Key('passageiro.cadastro.ajustarMapa');
  static const cadastrarButtonKey = Key('passageiro.cadastro.cadastrar');
  static const e2ePreencherEnderecoButtonKey = Key(
    'passageiro.cadastro.e2e.preencherEndereco',
  );

  final PassageiroCadastroGateway? cadastroGateway;
  final PlacesAutocompleteGateway? placesGateway;
  final bool openMapAfterAddressSelection;

  @override
  State<CadastroPassageiroScreen> createState() =>
      _CadastroPassageiroScreenState();
}

class _CadastroPassageiroScreenState extends State<CadastroPassageiroScreen> {
  static const bool _e2eMode = bool.fromEnvironment(
    'VANRIDES_E2E_TEST_MODE',
    defaultValue: false,
  );

  final _formKey = GlobalKey<FormState>();
  final _nomeController = TextEditingController();
  final _sobrenomeController = TextEditingController();
  final _emailController = TextEditingController();
  final _telefoneController = TextEditingController();
  final _dataNascimentoController = TextEditingController();
  final _senhaController = TextEditingController();
  final _confirmarSenhaController = TextEditingController();

  bool _carregando = false;

  String endereco = '';
  String bairro = '';
  String cidade = '';
  String estado = '';
  String cep = '';
  double? latitude;
  double? longitude;

  late final PassageiroCadastroGateway _cadastroGateway;
  late final PlacesAutocompleteGateway? _placesGateway;
  List<PassageiroPlacePrediction> predictions = [];

  @override
  void initState() {
    super.initState();
    _cadastroGateway =
        widget.cadastroGateway ?? HttpPassageiroCadastroGateway();
    _placesGateway =
        _e2eMode
            ? null
            : widget.placesGateway ?? GooglePlacesAutocompleteGateway();
  }

  @override
  void dispose() {
    _nomeController.dispose();
    _sobrenomeController.dispose();
    _emailController.dispose();
    _telefoneController.dispose();
    _dataNascimentoController.dispose();
    _senhaController.dispose();
    _confirmarSenhaController.dispose();
    super.dispose();
  }

  void autoCompleteSearch(String value) async {
    if (_e2eMode) return;
    final gateway = _placesGateway;
    if (value.trim().isEmpty || gateway?.isAvailable != true) {
      if (mounted) {
        setState(() => predictions = []);
      }
      return;
    }

    try {
      final result = await gateway!.autocomplete(value);
      if (!mounted) return;
      setState(() => predictions = result);
    } on PlacesAutocompleteException catch (error) {
      if (!mounted) return;
      setState(() => predictions = []);
      _showSnack(error.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => predictions = []);
      _showSnack('Não foi possível buscar endereços agora.');
    }
  }

  void selecionarEndereco(PassageiroPlacePrediction prediction) async {
    final gateway = _placesGateway;
    if (_e2eMode || gateway?.isAvailable != true) return;

    try {
      final details = await gateway!.fetchDetails(prediction.placeId);
      if (details == null) {
        return;
      }

      latitude = details.latitude;
      longitude = details.longitude;
      endereco = details.endereco;
      bairro = details.bairro;
      cidade = details.cidade;
      estado = details.estado;
      cep = details.cep;

      setState(() {
        predictions = [];
      });

      if (widget.openMapAfterAddressSelection) {
        await abrirMapaParaAjuste();
      }
    } on PlacesAutocompleteException catch (error) {
      if (!mounted) return;
      _showSnack(error.message);
    } catch (_) {
      if (!mounted) return;
      _showSnack('Não foi possível carregar o endereço selecionado.');
    }
  }

  void _showSnack(String message) {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> abrirMapaParaAjuste() async {
    if (_e2eMode || latitude == null || longitude == null) {
      return;
    }

    final posicaoInicial = LatLng(latitude!, longitude!);
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => MapPickerScreen(
              initialPosition: posicaoInicial,
              onConfirm: (novaPosicao) async {
                latitude = novaPosicao.latitude;
                longitude = novaPosicao.longitude;
                setState(() {});
              },
            ),
      ),
    );
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

  String _nomeCompleto() {
    final primeiro = _nomeController.text.trim();
    final sobrenome = _sobrenomeController.text.trim();
    if (sobrenome.isEmpty) {
      return primeiro;
    }
    return '$primeiro $sobrenome';
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

    if (latitude == null ||
        longitude == null ||
        endereco.isEmpty ||
        bairro.isEmpty ||
        cidade.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            AppStrings.passageiro.preenchaCamposObrigatoriosCadastro,
          ),
        ),
      );
      return;
    }

    setState(() => _carregando = true);

    final payload = PassageiroCadastroPayload(
      nome: _nomeCompleto(),
      email: _emailController.text.trim().toLowerCase(),
      telefone: _telefoneController.text.trim(),
      dataNascimento: VanRidesBirthDate.toIsoString(
        _dataNascimentoController.text,
      ),
      senha: senha,
      endereco: endereco,
      bairro: bairro,
      cidade: cidade,
      estado: estado,
      cep: cep,
      latitude: latitude!,
      longitude: longitude!,
    );

    final result = await _cadastroGateway.cadastrar(payload);

    if (!mounted) {
      return;
    }

    setState(() => _carregando = false);

    if (result is PassageiroCadastroOk) {
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder:
              (_) => EmailVerificationPendingScreen(
                email: payload.email,
                variant: EmailVerificationPendingVariant.passengerSignup,
                initialResendCooldown: result.initialResendCooldown,
              ),
        ),
      );
      return;
    }

    if (result is PassageiroCadastroValidationError) {
      _showSnack(result.message);
      return;
    }

    if (result is PassageiroCadastroNetworkError) {
      _showSnack(result.message);
    }
  }

  void _voltarParaLogin() {
    Navigator.of(context).popUntil((route) => route.isFirst);
  }

  @override
  Widget build(BuildContext context) {
    final senha = _senhaController.text;

    return OnboardingScaffold(
      key: CadastroPassageiroScreen.screenKey,
      header: OnboardingHeader(onBack: () => Navigator.maybePop(context)),
      topPadding: 16,
      blurDots: const [
        OnboardingBlurDot(
          color: OnboardingPalette.passenger,
          size: 256,
          right: -72,
          top: -90,
        ),
      ],
      footer: _buildCadastroFooter(),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            OnboardingSectionHeader(
              title: AppStrings.passageiro.crieSuaConta,
              subtitle: AppStrings.passageiro.entreNaComunidadeVanRides,
              titleColor: OnboardingPalette.passenger,
            ),
            const SizedBox(height: 18),
            const OnboardingStepProgress(
              steps: ['Dados pessoais', 'Sua parada', 'Empresa', 'Verificação'],
              accentColor: OnboardingPalette.passenger,
            ),
            const SizedBox(height: 28),
            _buildAdaptiveRow(
              children: [
                _buildField(
                  label: AppStrings.passageiro.nome,
                  child: TextFormField(
                    key: CadastroPassageiroScreen.nomeFieldKey,
                    controller: _nomeController,
                    textInputAction: TextInputAction.next,
                    decoration: onboardingInputDecoration(
                      hintText: AppStrings.passageiro.joao,
                    ),
                    validator:
                        (value) =>
                            value!.trim().isEmpty
                                ? AppStrings.passageiro.campoObrigatorio
                                : null,
                  ),
                ),
                _buildField(
                  label: AppStrings.passageiro.sobrenome,
                  child: TextFormField(
                    key: CadastroPassageiroScreen.sobrenomeFieldKey,
                    controller: _sobrenomeController,
                    textInputAction: TextInputAction.next,
                    decoration: onboardingInputDecoration(
                      hintText: AppStrings.passageiro.silva,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            _buildField(
              label: AppStrings.passageiro.email,
              child: TextFormField(
                key: CadastroPassageiroScreen.emailFieldKey,
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
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 16,
                    ),
                    decoration: BoxDecoration(
                      color: OnboardingPalette.fieldFill,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          '+55',
                          style: TextStyle(
                            color: OnboardingPalette.textMuted,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        SizedBox(width: 4),
                        Icon(
                          Icons.keyboard_arrow_down_rounded,
                          color: OnboardingPalette.textMuted,
                          size: 16,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextFormField(
                      key: CadastroPassageiroScreen.telefoneFieldKey,
                      controller: _telefoneController,
                      keyboardType: TextInputType.phone,
                      textInputAction: TextInputAction.next,
                      decoration: onboardingInputDecoration(
                        hintText: '(11) 99999-9999',
                      ),
                      validator:
                          (value) =>
                              value!.trim().isEmpty
                                  ? AppStrings.passageiro.campoObrigatorio
                                  : null,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            _buildField(
              label: 'Data de nascimento',
              child: TextFormField(
                key: CadastroPassageiroScreen.dataNascimentoFieldKey,
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
            const _PassengerCompanyBindingHint(),
            if (_e2eMode) ...[const SizedBox(height: 12), _buildE2eShortcuts()],
            const SizedBox(height: 16),
            const _PassengerStopGuidanceCard(),
            const SizedBox(height: 16),
            _buildField(
              label: 'Endereço da parada',
              child: TextFormField(
                key: CadastroPassageiroScreen.buscarEnderecoFieldKey,
                textInputAction: TextInputAction.next,
                decoration: onboardingInputDecoration(
                  hintText: AppStrings.passageiro.buscarEnderecoHint,
                  suffixIcon: const Icon(
                    Icons.location_on_outlined,
                    color: OnboardingPalette.textMuted,
                  ),
                ),
                onChanged: _e2eMode ? null : autoCompleteSearch,
              ),
            ),
            if (!_e2eMode && _placesGateway?.isAvailable != true) ...[
              const SizedBox(height: 8),
              Text(
                AppStrings.passageiro.buscaAutomaticaEnderecoDesabilitada,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: OnboardingPalette.textMuted,
                  height: 1.4,
                ),
              ),
            ],
            if (!_e2eMode && predictions.isNotEmpty) ...[
              const SizedBox(height: 8),
              ...predictions.map(
                (prediction) => Card(
                  elevation: 0,
                  color: OnboardingPalette.surface,
                  margin: const EdgeInsets.only(bottom: 8),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                    side: BorderSide(
                      color: OnboardingPalette.border.withValues(alpha: 0.8),
                    ),
                  ),
                  child: ListTile(
                    title: Text(
                      prediction.description,
                      style: const TextStyle(
                        color: OnboardingPalette.textStrong,
                      ),
                    ),
                    onTap: () => selecionarEndereco(prediction),
                  ),
                ),
              ),
            ],
            if (endereco.isNotEmpty) ...[
              const SizedBox(height: 16),
              _PassengerStopMapConfirmation(address: endereco),
              const SizedBox(height: 16),
              OnboardingStaticField(
                label: AppStrings.passageiro.enderecoSelecionado,
                value: endereco,
              ),
              const SizedBox(height: 16),
              _buildAdaptiveRow(
                children: [
                  OnboardingStaticField(
                    label: AppStrings.passageiro.cidade,
                    value: cidade,
                  ),
                  OnboardingStaticField(
                    label: AppStrings.passageiro.cep,
                    value: cep,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _buildAdaptiveRow(
                children: [
                  OnboardingStaticField(
                    label: AppStrings.passageiro.bairro,
                    value: bairro,
                  ),
                  OnboardingStaticField(
                    label: AppStrings.passageiro.estado,
                    value: estado,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                key: CadastroPassageiroScreen.ajustarMapaButtonKey,
                onPressed: _e2eMode ? null : abrirMapaParaAjuste,
                style: OutlinedButton.styleFrom(
                  foregroundColor: OnboardingPalette.passenger,
                  side: BorderSide(
                    color: OnboardingPalette.passenger.withValues(alpha: 0.18),
                  ),
                  backgroundColor: OnboardingPalette.surfaceRaised,
                  minimumSize: const Size.fromHeight(50),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                icon: const Icon(Icons.edit_location_alt_outlined),
                label: Text(
                  _e2eMode
                      ? AppStrings.passageiro.ajustarNoMapaE2e
                      : AppStrings.passageiro.ajustarNoMapa,
                ),
              ),
            ],
            const SizedBox(height: 16),
            _buildField(
              label: AppStrings.passageiro.senha,
              child: TextFormField(
                key: CadastroPassageiroScreen.senhaFieldKey,
                controller: _senhaController,
                obscureText: true,
                textInputAction: TextInputAction.next,
                decoration: onboardingInputDecoration(
                  hintText: '••••••••',
                  suffixIcon: const Icon(
                    Icons.visibility_outlined,
                    color: OnboardingPalette.textMuted,
                  ),
                ),
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
                key: CadastroPassageiroScreen.confirmarSenhaFieldKey,
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
              _PasswordRequirementsCard(password: senha),
            ],
            const SizedBox(height: 24),
            OnboardingFooterPrompt(
              prefix: 'Já tem uma conta?',
              actionLabel: AppStrings.passageiro.entrar,
              onTap: _voltarParaLogin,
            ),
          ],
        ),
      ),
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
      buttonKey: CadastroPassageiroScreen.cadastrarButtonKey,
      label: 'Criar conta e verificar e-mail',
      onPressed: _realizarCadastro,
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF005DAC), Color(0xFF1976D2)],
      ),
    );
  }

  Widget _buildField({required String label, required Widget child}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [OnboardingFieldLabel(label), const SizedBox(height: 6), child],
    );
  }

  Widget _buildAdaptiveRow({required List<Widget> children}) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 360) {
          return Column(
            children: [
              for (var i = 0; i < children.length; i++) ...[
                children[i],
                if (i < children.length - 1) const SizedBox(height: 16),
              ],
            ],
          );
        }

        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: children[0]),
            const SizedBox(width: 16),
            Expanded(child: children[1]),
          ],
        );
      },
    );
  }

  Widget _buildE2eShortcuts() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: OnboardingPalette.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: OnboardingPalette.border.withValues(alpha: 0.7),
        ),
      ),
      child: Column(
        children: [
          SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              key: CadastroPassageiroScreen.e2ePreencherEnderecoButtonKey,
              onPressed: () {
                setState(() {
                  endereco = 'Rua de Teste, 123';
                  bairro = 'Centro';
                  cidade = 'São Paulo';
                  estado = 'SP';
                  cep = '01000-000';
                  latitude = -23.55052;
                  longitude = -46.633308;
                  predictions = [];
                });
              },
              child: Text(AppStrings.passageiro.e2ePreencherEnderecoTeste),
            ),
          ),
        ],
      ),
    );
  }
}
