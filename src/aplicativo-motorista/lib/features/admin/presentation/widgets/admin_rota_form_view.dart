import 'package:flutter/material.dart';

import '../../data/admin_places_gateway.dart';
import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_rota_base_create_input.dart';
import '../../domain/admin_rota_base_record.dart';
import 'admin_destination_picker.dart';
import 'admin_portal_layout.dart';
import 'admin_rota_form_summary.dart';
import 'admin_rotas_widgets.dart';

class AdminRotaFormView extends StatefulWidget {
  const AdminRotaFormView({
    super.key,
    required this.placesGateway,
    required this.motoristas,
    required this.passageiros,
    required this.isSubmitting,
    required this.onCancel,
    required this.onSubmit,
    this.initialRota,
  });

  static const nameFieldKey = Key('admin.rotas.form.name');
  static const driverDropdownKey = Key('admin.rotas.form.driver');
  static const submitButtonKey = Key('admin.rotas.form.submit');

  final AdminPlacesGateway placesGateway;
  final List<AdminMotoristaRecord> motoristas;
  final List<AdminPassageiroRecord> passageiros;
  final bool isSubmitting;
  final VoidCallback onCancel;
  final ValueChanged<AdminRotaBaseCreateInput> onSubmit;
  final AdminRotaBaseRecord? initialRota;

  @override
  State<AdminRotaFormView> createState() => _AdminRotaFormViewState();
}

class _AdminRotaFormViewState extends State<AdminRotaFormView> {
  static const _dias = ['SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SAB', 'DOM'];

  final _formKey = GlobalKey<FormState>();
  final _nomeController = TextEditingController();
  final Set<int> _selectedPassageiros = <int>{};
  final Set<String> _diasOperacao = <String>{'SEG', 'TER', 'QUA', 'QUI', 'SEX'};

  int? _motoristaId;
  String? _passageirosError;
  bool _geracaoAutomatica = true;
  TimeOfDay? _horarioPartida;
  String? _destinoFinalNome;
  String? _destinoFinalEndereco;
  double? _destinoFinalLatitude;
  double? _destinoFinalLongitude;

  bool get _isEditing => widget.initialRota != null;

  @override
  void initState() {
    super.initState();
    final rota = widget.initialRota;
    if (rota == null) return;
    _nomeController.text = rota.nome;
    _motoristaId = rota.motorista.id;
    _selectedPassageiros.addAll(rota.passageiroIds);
    _geracaoAutomatica = rota.geracaoAutomaticaAtiva;
    _diasOperacao
      ..clear()
      ..addAll(rota.diasOperacao);
    _horarioPartida = _parseTime(rota.horarioPartida);
    _destinoFinalNome = rota.destinoFinalNome;
    _destinoFinalEndereco = rota.destinoFinalEndereco;
    _destinoFinalLatitude = rota.destinoFinalLatitude;
    _destinoFinalLongitude = rota.destinoFinalLongitude;
  }

  @override
  void dispose() {
    _nomeController.dispose();
    super.dispose();
  }

  List<AdminPassageiroRecord> get _passageirosDoMotoristaSelecionado {
    if (_motoristaId == null) {
      return const [];
    }
    return widget.passageiros
        .where((passageiro) => passageiro.motoristaId == _motoristaId)
        .toList(growable: false);
  }

  void _submit() {
    if (!_formKey.currentState!.validate() || _motoristaId == null) {
      return;
    }

    if (_selectedPassageiros.isEmpty) {
      setState(() {
        _passageirosError =
            'Selecione ao menos um passageiro vinculado ao motorista desta rota.';
      });
      return;
    }

    widget.onSubmit(
      AdminRotaBaseCreateInput(
        nome: _nomeController.text,
        motoristaId: _motoristaId!,
        passageiroIds: _selectedPassageiros.toList(growable: false),
        geracaoAutomaticaAtiva: _geracaoAutomatica,
        diasOperacao: _diasOperacao.toList(growable: false),
        horarioPartida:
            _horarioPartida == null
                ? null
                : '${_horarioPartida!.hour.toString().padLeft(2, '0')}:${_horarioPartida!.minute.toString().padLeft(2, '0')}',
        destinoFinalNome: _destinoFinalNome,
        destinoFinalEndereco: _destinoFinalEndereco,
        destinoFinalLatitude: _destinoFinalLatitude,
        destinoFinalLongitude: _destinoFinalLongitude,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AdminPortalPage(
      breadcrumb: _isEditing ? 'Rotas-base • Editar' : 'Rotas-base • Nova',
      title: _isEditing ? 'Editar rota-base' : 'Criar rota-base',
      description:
          'Configure a linha de referência usada para gerar viagens e orientar a operação diária.',
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AdminPortalSecondaryButton(
            label: 'Descartar',
            onPressed: widget.onCancel,
          ),
          const SizedBox(width: 12),
          KeyedSubtree(
            key: AdminRotaFormView.submitButtonKey,
            child: AdminPortalPrimaryButton(
              label:
                  widget.isSubmitting
                      ? 'Salvando...'
                      : (_isEditing
                          ? 'Salvar alterações'
                          : 'Confirmar rota-base'),
              icon: Icons.check_rounded,
              onPressed:
                  widget.isSubmitting || widget.passageiros.isEmpty
                      ? null
                      : _submit,
            ),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(flex: 7, child: _buildFormSurface()),
          const SizedBox(width: 24),
          Expanded(
            flex: 5,
            child: AdminRotaFormSummary(
              routeName: _nomeController.text,
              motoristas: widget.motoristas,
              motoristaId: _motoristaId,
              selectedPassageirosCount: _selectedPassageiros.length,
              diasOperacao: _diasOperacao,
              horarioPartida: _horarioPartida,
              destinoFinalNome: _destinoFinalNome,
              destinoFinalEndereco: _destinoFinalEndereco,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFormSurface() {
    return AdminPortalSurface(
      radius: 48,
      child: Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Dados da rota-base',
              style: TextStyle(
                color: AdminPortalColors.text,
                fontSize: 26,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 24),
            _buildNameField(),
            const SizedBox(height: 24),
            _buildDriverField(),
            const SizedBox(height: 24),
            _buildOperationDays(),
            const SizedBox(height: 24),
            _buildDepartureTimeField(),
            const SizedBox(height: 24),
            _buildDestinationPicker(),
            const SizedBox(height: 24),
            SwitchListTile.adaptive(
              value: _geracaoAutomatica,
              onChanged: (value) => setState(() => _geracaoAutomatica = value),
              contentPadding: EdgeInsets.zero,
              activeThumbColor: AdminPortalColors.blueBright,
              title: const Text(
                'Manter geração automática',
                style: TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
              subtitle: const Text(
                'Quando ativo, novas viagens podem nascer automaticamente a partir desta rota-base.',
              ),
            ),
            const SizedBox(height: 24),
            _buildPassengerSelector(),
          ],
        ),
      ),
    );
  }

  Widget _buildNameField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AdminRotasFieldLabel(label: 'Nome da rota-base'),
        const SizedBox(height: 8),
        TextFormField(
          key: AdminRotaFormView.nameFieldKey,
          controller: _nomeController,
          decoration: _portalInputDecoration(
            hintText: 'Ex: Zona Sul - Escolar Matutino',
          ),
          validator:
              (value) =>
                  value == null || value.trim().isEmpty
                      ? 'Informe um nome para a rota.'
                      : null,
        ),
      ],
    );
  }

  Widget _buildDriverField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AdminRotasFieldLabel(label: 'Motorista responsável'),
        const SizedBox(height: 8),
        DropdownButtonFormField<int>(
          key: AdminRotaFormView.driverDropdownKey,
          initialValue: _motoristaId,
          decoration: _portalInputDecoration(
            hintText: 'Selecione o motorista responsável',
          ),
          items: widget.motoristas
              .map(
                (motorista) => DropdownMenuItem<int>(
                  value: motorista.id,
                  child: Text(motorista.nome),
                ),
              )
              .toList(growable: false),
          onChanged: (value) {
            setState(() {
              _motoristaId = value;
              _selectedPassageiros.removeWhere(
                (passageiroId) =>
                    !_passageirosDoMotoristaSelecionado.any(
                      (item) => item.id == passageiroId,
                    ),
              );
              _passageirosError = null;
            });
          },
          validator:
              (value) =>
                  value == null
                      ? 'Selecione o motorista responsável desta rota-base.'
                      : null,
        ),
      ],
    );
  }

  Widget _buildOperationDays() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AdminRotasFieldLabel(label: 'Dias de operação'),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: _dias
              .map(
                (dia) => FilterChip(
                  label: Text(dia),
                  selected: _diasOperacao.contains(dia),
                  onSelected: (selected) {
                    setState(() {
                      if (selected) {
                        _diasOperacao.add(dia);
                      } else {
                        _diasOperacao.remove(dia);
                      }
                    });
                  },
                  backgroundColor: AdminPortalColors.surfaceSoft,
                  selectedColor: AdminPortalColors.blueSoft,
                  labelStyle: TextStyle(
                    color:
                        _diasOperacao.contains(dia)
                            ? AdminPortalColors.blue
                            : AdminPortalColors.textMuted,
                    fontWeight: FontWeight.w700,
                  ),
                  side: BorderSide(
                    color:
                        _diasOperacao.contains(dia)
                            ? AdminPortalColors.blue
                            : Colors.transparent,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
              )
              .toList(growable: false),
        ),
      ],
    );
  }

  Widget _buildDepartureTimeField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AdminRotasFieldLabel(label: 'Horário de partida (opcional)'),
        const SizedBox(height: 8),
        InkWell(
          onTap: () async {
            final picked = await showTimePicker(
              context: context,
              initialTime:
                  _horarioPartida ?? const TimeOfDay(hour: 7, minute: 0),
            );
            if (picked != null) setState(() => _horarioPartida = picked);
          },
          borderRadius: BorderRadius.circular(18),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
            decoration: BoxDecoration(
              color: AdminPortalColors.input,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.access_time_rounded,
                  size: 18,
                  color: AdminPortalColors.textMuted,
                ),
                const SizedBox(width: 10),
                Text(
                  _horarioPartida == null
                      ? 'Sem horário definido'
                      : '${_horarioPartida!.hour.toString().padLeft(2, '0')}:${_horarioPartida!.minute.toString().padLeft(2, '0')}',
                  style: TextStyle(
                    color:
                        _horarioPartida == null
                            ? AdminPortalColors.textMuted
                            : AdminPortalColors.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                if (_horarioPartida != null) ...[
                  const Spacer(),
                  GestureDetector(
                    onTap: () => setState(() => _horarioPartida = null),
                    child: const Icon(
                      Icons.close_rounded,
                      size: 18,
                      color: AdminPortalColors.textMuted,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildDestinationPicker() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AdminRotasFieldLabel(label: 'Destino final (opcional)'),
        const SizedBox(height: 4),
        const Text(
          'Quando definido, a navegação do motorista encerra neste ponto, independentemente das paradas.',
          style: TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 13,
            height: 1.5,
          ),
        ),
        const SizedBox(height: 12),
        AdminDestinationPicker(
          placesGateway: widget.placesGateway,
          nome: _destinoFinalNome,
          endereco: _destinoFinalEndereco,
          confirmedPadding: const EdgeInsets.symmetric(
            horizontal: 20,
            vertical: 16,
          ),
          searchDecorationBuilder:
              (loading) => _portalInputDecoration(
                hintText:
                    'Pesquisar endereço ou local (ex: Escola Municipal...)',
              ).copyWith(
                suffixIcon:
                    loading
                        ? const Padding(
                          padding: EdgeInsets.all(14),
                          child: SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        )
                        : const Icon(
                          Icons.search_rounded,
                          color: AdminPortalColors.textMuted,
                        ),
              ),
          onSelected:
              (nome, endereco, lat, lng) => setState(() {
                _destinoFinalNome = nome;
                _destinoFinalEndereco = endereco;
                _destinoFinalLatitude = lat;
                _destinoFinalLongitude = lng;
              }),
          onCleared:
              () => setState(() {
                _destinoFinalNome = null;
                _destinoFinalEndereco = null;
                _destinoFinalLatitude = null;
                _destinoFinalLongitude = null;
              }),
        ),
      ],
    );
  }

  Widget _buildPassengerSelector() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AdminRotasFieldLabel(label: 'Passageiros vinculados'),
        const SizedBox(height: 12),
        Container(
          decoration: BoxDecoration(
            color: AdminPortalColors.surfaceSoft,
            borderRadius: BorderRadius.circular(32),
          ),
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (widget.passageiros.isEmpty)
                const Text(
                  'Esta empresa ainda não possui passageiros cadastrados. Cadastre ao menos um passageiro antes de criar a rota.',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 14,
                    height: 1.5,
                  ),
                )
              else if (_motoristaId == null)
                const Text(
                  'Selecione primeiro o motorista responsável para listar apenas os passageiros compatíveis com esta rota.',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 14,
                    height: 1.5,
                  ),
                )
              else if (_passageirosDoMotoristaSelecionado.isEmpty)
                const Text(
                  'Nenhum passageiro desta empresa está vinculado ao motorista selecionado. Ajuste os vínculos antes de continuar.',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 14,
                    height: 1.5,
                  ),
                )
              else
                ..._passageirosDoMotoristaSelecionado.map(_buildPassengerTile),
              if (_passageirosError != null) ...[
                const SizedBox(height: 12),
                Text(
                  _passageirosError!,
                  style: const TextStyle(
                    color: AdminPortalColors.red,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildPassengerTile(AdminPassageiroRecord passageiro) {
    final isSelected = _selectedPassageiros.contains(passageiro.id);
    return CheckboxListTile(
      value: isSelected,
      controlAffinity: ListTileControlAffinity.leading,
      contentPadding: EdgeInsets.zero,
      activeColor: AdminPortalColors.blueBright,
      title: Text(
        passageiro.nome,
        style: const TextStyle(
          color: AdminPortalColors.text,
          fontWeight: FontWeight.w700,
        ),
      ),
      subtitle: Text(
        passageiro.motoristaNome?.isNotEmpty == true
            ? 'Motorista atual: ${passageiro.motoristaNome}'
            : passageiro.enderecoCompleto,
      ),
      onChanged: (_) {
        setState(() {
          if (isSelected) {
            _selectedPassageiros.remove(passageiro.id);
          } else {
            _selectedPassageiros.add(passageiro.id);
          }
          _passageirosError = null;
        });
      },
    );
  }
}

TimeOfDay? _parseTime(String? value) {
  final parts = value?.split(':') ?? const [];
  if (parts.length < 2) {
    return null;
  }
  return TimeOfDay(
    hour: int.tryParse(parts[0]) ?? 0,
    minute: int.tryParse(parts[1]) ?? 0,
  );
}

InputDecoration _portalInputDecoration({required String hintText}) {
  return InputDecoration(
    hintText: hintText,
    filled: true,
    fillColor: AdminPortalColors.input,
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: BorderSide.none,
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: BorderSide.none,
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(color: AdminPortalColors.blueBright),
    ),
    contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
  );
}
