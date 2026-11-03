import 'package:flutter/material.dart';

import '../../data/admin_places_gateway.dart';
import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_servico_recorrente_input.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_destination_picker.dart';
import 'admin_portal_layout.dart';
import 'admin_servicos_recorrentes_form_widgets.dart';

class AdminServicoRecorrenteForm extends StatefulWidget {
  const AdminServicoRecorrenteForm({
    super.key,
    required this.placesGateway,
    required this.motoristas,
    required this.passageiros,
    required this.isSubmitting,
    required this.onCancel,
    required this.onSubmit,
    this.initial,
  });

  final AdminPlacesGateway placesGateway;
  final AdminServicoRecorrenteRecord? initial;
  final List<AdminMotoristaRecord> motoristas;
  final List<AdminPassageiroRecord> passageiros;
  final bool isSubmitting;
  final VoidCallback onCancel;
  final ValueChanged<AdminServicoRecorrenteInput> onSubmit;

  static const nameFieldKey = Key('admin.servicos.form.name');
  static const departureTimeFieldKey = Key('admin.servicos.form.departureTime');
  static const submitButtonKey = Key('admin.servicos.form.submit');
  static const searchPassengerFieldKey = Key(
    'admin.servicos.passageiros.search',
  );

  @override
  State<AdminServicoRecorrenteForm> createState() =>
      _AdminServicoRecorrenteFormState();
}

class _AdminServicoRecorrenteFormState
    extends State<AdminServicoRecorrenteForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nomeController;
  late final TextEditingController _horarioController;
  late final TextEditingController _destinoNomeController;
  late final TextEditingController _destinoEnderecoController;
  late final TextEditingController _capacidadeController;
  late final TextEditingController _passageiroSearchController;
  late String _tipoOperacao;
  late String _politicaPresenca;
  late bool _geracaoAutomaticaAtiva;
  late int _motoristaPadraoValue;
  late Set<String> _diasOperacao;
  late Set<int> _passageiroIds;
  double? _destinoFinalLatitude;
  double? _destinoFinalLongitude;

  @override
  void initState() {
    super.initState();
    final initial = widget.initial;
    _nomeController = TextEditingController(text: initial?.nome ?? '');
    _horarioController = TextEditingController(
      text: initial?.horarioPartida ?? '',
    )..addListener(_refreshComputedFormState);
    _destinoNomeController = TextEditingController(
      text: initial?.destinoFinalNome ?? '',
    );
    _destinoEnderecoController = TextEditingController(
      text: initial?.destinoFinalEndereco ?? '',
    );
    _destinoFinalLatitude = initial?.destinoFinalLatitude;
    _destinoFinalLongitude = initial?.destinoFinalLongitude;
    _capacidadeController = TextEditingController(
      text: initial?.capacidadePlanejada?.toString() ?? '',
    )..addListener(_refreshComputedFormState);
    _passageiroSearchController =
        TextEditingController()..addListener(_refreshComputedFormState);
    _tipoOperacao = initial?.tipoOperacao ?? 'IDA';
    _politicaPresenca = initial?.politicaPresenca ?? 'INCLUIR_POR_PADRAO';
    _geracaoAutomaticaAtiva = initial?.geracaoAutomaticaAtiva ?? true;
    _motoristaPadraoValue = initial?.motoristaPadrao?.id ?? 0;
    _diasOperacao =
        initial?.diasOperacao.toSet() ??
        const {'SEG', 'TER', 'QUA', 'QUI', 'SEX'};
    _passageiroIds = initial?.passageiroIds.toSet() ?? <int>{};
  }

  @override
  void dispose() {
    _nomeController.dispose();
    _horarioController.dispose();
    _destinoNomeController.dispose();
    _destinoEnderecoController.dispose();
    _capacidadeController.dispose();
    _passageiroSearchController.dispose();
    super.dispose();
  }

  List<AdminPassageiroRecord> get _filteredPassageiros {
    final query = normalizeAdminServicoSearch(_passageiroSearchController.text);
    if (query.isEmpty) {
      return widget.passageiros;
    }
    return widget.passageiros
        .where((passageiro) {
          final searchable = normalizeAdminServicoSearch(
            '${passageiro.nome} ${passageiro.email}',
          );
          return searchable.contains(query);
        })
        .toList(growable: false);
  }

  int? get _capacidadePlanejadaInput {
    final raw = _capacidadeController.text.trim();
    if (raw.isEmpty) return null;
    return int.tryParse(raw);
  }

  void _selectVisiblePassageiros() {
    final visible = _filteredPassageiros;
    if (visible.isEmpty) return;
    setState(() {
      _passageiroIds.addAll(visible.map((passageiro) => passageiro.id));
    });
  }

  void _clearSelectedPassageiros() {
    if (_passageiroIds.isEmpty) return;
    setState(_passageiroIds.clear);
  }

  void _refreshComputedFormState() {
    if (mounted) {
      setState(() {});
    }
  }

  void _setDestinoFinal(
    String nome,
    String? endereco,
    double latitude,
    double longitude,
  ) {
    setState(() {
      _destinoNomeController.text = nome;
      _destinoEnderecoController.text = endereco ?? '';
      _destinoFinalLatitude = latitude;
      _destinoFinalLongitude = longitude;
    });
  }

  void _clearDestinoFinal() {
    setState(() {
      _destinoNomeController.clear();
      _destinoEnderecoController.clear();
      _destinoFinalLatitude = null;
      _destinoFinalLongitude = null;
    });
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    if (_diasOperacao.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Selecione ao menos um dia de operação.')),
      );
      return;
    }

    final capacidadeText = _capacidadeController.text.trim();
    final capacidade =
        capacidadeText.isEmpty ? null : int.tryParse(capacidadeText);

    widget.onSubmit(
      AdminServicoRecorrenteInput(
        nome: _nomeController.text,
        motoristaPadraoId:
            _motoristaPadraoValue == 0 ? null : _motoristaPadraoValue,
        tipoOperacao: _tipoOperacao,
        politicaPresenca: _politicaPresenca,
        geracaoAutomaticaAtiva: _geracaoAutomaticaAtiva,
        diasOperacao: orderedAdminServicoDays(_diasOperacao),
        horarioPartida: emptyToNullAdminServicoValue(_horarioController.text),
        destinoFinalNome: emptyToNullAdminServicoValue(
          _destinoNomeController.text,
        ),
        destinoFinalEndereco: emptyToNullAdminServicoValue(
          _destinoEnderecoController.text,
        ),
        destinoFinalLatitude: _destinoFinalLatitude,
        destinoFinalLongitude: _destinoFinalLongitude,
        capacidadePlanejada: capacidade,
        passageiroIds: _passageiroIds.toList(growable: false)..sort(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final editing = widget.initial != null;
    final filteredPassageiros = _filteredPassageiros;
    final selectedCount = _passageiroIds.length;
    final capacidade = _capacidadePlanejadaInput;
    final exceedsCapacity = capacidade != null && selectedCount > capacidade;

    return AdminPortalPage(
      breadcrumb: editing ? 'Serviços • Editar' : 'Serviços • Novo',
      title: editing ? 'Editar serviço recorrente' : 'Novo serviço recorrente',
      description:
          'Defina a linha operacional, a política de presença e os passageiros elegíveis.',
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AdminPortalSecondaryButton(
            label: 'Cancelar',
            onPressed: widget.onCancel,
          ),
          const SizedBox(width: 12),
          AdminPortalPrimaryButton(
            key: AdminServicoRecorrenteForm.submitButtonKey,
            label: widget.isSubmitting ? 'Salvando...' : 'Salvar serviço',
            icon: Icons.check_rounded,
            onPressed: widget.isSubmitting ? null : _submit,
          ),
        ],
      ),
      child: Form(
        key: _formKey,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              flex: 7,
              child: AdminPortalSurface(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const AdminServicoFormSectionTitle(
                      'Configuração operacional',
                    ),
                    const SizedBox(height: 8),
                    AdminServicoFormOperationalSummary(
                      tipoOperacao: _tipoOperacao,
                      horarioPartida: _horarioController.text,
                      politicaPresenca: _politicaPresenca,
                      geracaoAutomaticaAtiva: _geracaoAutomaticaAtiva,
                      motoristaPadraoValue: _motoristaPadraoValue,
                      motoristas: widget.motoristas,
                      selectedPassengers: selectedCount,
                      totalPassengers: widget.passageiros.length,
                    ),
                    const SizedBox(height: 16),
                    const _AdminServicoFormImpactNotice(),
                    const SizedBox(height: 18),
                    TextFormField(
                      key: AdminServicoRecorrenteForm.nameFieldKey,
                      controller: _nomeController,
                      decoration: adminServicoInputDecoration(
                        'Nome do serviço',
                      ),
                      validator:
                          (value) =>
                              value == null || value.trim().isEmpty
                                  ? 'Informe um nome para o serviço.'
                                  : null,
                    ),
                    const SizedBox(height: 18),
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: _tipoOperacao,
                            decoration: adminServicoInputDecoration(
                              'Tipo de operação',
                            ),
                            items: adminServicoTipoOperacaoOptions.entries
                                .map(
                                  (entry) => DropdownMenuItem<String>(
                                    value: entry.key,
                                    child: Text(entry.value),
                                  ),
                                )
                                .toList(growable: false),
                            onChanged:
                                (value) => setState(
                                  () => _tipoOperacao = value ?? 'IDA',
                                ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: _politicaPresenca,
                            decoration: adminServicoInputDecoration(
                              'Política de presença',
                            ),
                            items: adminServicoPoliticaOptions.entries
                                .map(
                                  (entry) => DropdownMenuItem<String>(
                                    value: entry.key,
                                    child: Text(entry.value),
                                  ),
                                )
                                .toList(growable: false),
                            onChanged:
                                (value) => setState(
                                  () =>
                                      _politicaPresenca =
                                          value ?? 'INCLUIR_POR_PADRAO',
                                ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButtonFormField<int>(
                            initialValue: _motoristaPadraoValue,
                            decoration: adminServicoInputDecoration(
                              'Motorista padrão',
                            ),
                            items: [
                              const DropdownMenuItem<int>(
                                value: 0,
                                child: Text('Sem motorista fixo'),
                              ),
                              for (final motorista in widget.motoristas)
                                DropdownMenuItem<int>(
                                  value: motorista.id,
                                  child: Text(motorista.nome),
                                ),
                            ],
                            onChanged:
                                (value) => setState(
                                  () => _motoristaPadraoValue = value ?? 0,
                                ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextFormField(
                            key:
                                AdminServicoRecorrenteForm
                                    .departureTimeFieldKey,
                            controller: _horarioController,
                            decoration: adminServicoInputDecoration(
                              'Horário de partida',
                            ),
                            validator: validateAdminServicoOptionalTime,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    const AdminServicoFormFieldLabel('Destino final'),
                    const SizedBox(height: 10),
                    AdminDestinationPicker(
                      placesGateway: widget.placesGateway,
                      nome: emptyToNullAdminServicoValue(
                        _destinoNomeController.text,
                      ),
                      endereco: emptyToNullAdminServicoValue(
                        _destinoEnderecoController.text,
                      ),
                      searchDecorationBuilder:
                          (loading) => adminServicoInputDecoration(
                            'Pesquisar destino final',
                          ).copyWith(
                            prefixIcon: const Icon(Icons.search_rounded),
                            suffixIcon:
                                loading
                                    ? const Padding(
                                      padding: EdgeInsets.all(14),
                                      child: SizedBox(
                                        width: 16,
                                        height: 16,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      ),
                                    )
                                    : null,
                          ),
                      onSelected: _setDestinoFinal,
                      onCleared: _clearDestinoFinal,
                    ),
                    const SizedBox(height: 18),
                    TextFormField(
                      controller: _capacidadeController,
                      decoration: adminServicoInputDecoration(
                        'Capacidade planejada',
                      ),
                      keyboardType: TextInputType.number,
                      validator: validateAdminServicoOptionalPositiveInt,
                    ),
                    const SizedBox(height: 18),
                    SwitchListTile.adaptive(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('Gerar viagem automaticamente'),
                      subtitle: const Text(
                        'Quando ativo, a viagem do dia pode ser criada automaticamente para este serviço.',
                      ),
                      value: _geracaoAutomaticaAtiva,
                      onChanged:
                          (value) =>
                              setState(() => _geracaoAutomaticaAtiva = value),
                    ),
                    const SizedBox(height: 18),
                    const AdminServicoFormFieldLabel('Dias de operação'),
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final day in adminServicoDayOptions)
                          FilterChip(
                            label: Text(day),
                            selected: _diasOperacao.contains(day),
                            onSelected: (selected) {
                              setState(() {
                                if (selected) {
                                  _diasOperacao.add(day);
                                } else {
                                  _diasOperacao.remove(day);
                                }
                              });
                            },
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 24),
            Expanded(
              flex: 5,
              child: AdminPortalSurface(
                color: AdminPortalColors.elevatedSurface,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Expanded(
                          child: AdminServicoFormSectionTitle(
                            'Passageiros elegíveis',
                          ),
                        ),
                        AdminPortalTag(
                          label:
                              '$selectedCount de ${widget.passageiros.length} passageiros elegíveis',
                          background:
                              exceedsCapacity
                                  ? AdminPortalColors.redSoft
                                  : AdminPortalColors.surfaceSoft,
                          foreground:
                              exceedsCapacity
                                  ? AdminPortalColors.red
                                  : AdminPortalColors.textMuted,
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'O passageiro pode pertencer a mais de um serviço. A presença do dia decide em qual viagem ele entra.',
                      style: TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 13,
                        height: 1.4,
                      ),
                    ),
                    if (exceedsCapacity) ...[
                      const SizedBox(height: 12),
                      const AdminServicoFormHintBanner(
                        icon: Icons.event_seat_outlined,
                        text:
                            'A elegibilidade está acima da capacidade planejada. A empresa ainda pode salvar, mas deve revisar presença e escala antes de gerar a viagem.',
                        color: AdminPortalColors.red,
                        background: AdminPortalColors.redSoft,
                      ),
                    ],
                    const SizedBox(height: 18),
                    TextField(
                      key: AdminServicoRecorrenteForm.searchPassengerFieldKey,
                      controller: _passageiroSearchController,
                      decoration: adminServicoInputDecoration(
                        'Buscar passageiro por nome ou e-mail',
                      ).copyWith(prefixIcon: const Icon(Icons.search)),
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        TextButton.icon(
                          onPressed:
                              filteredPassageiros.isEmpty
                                  ? null
                                  : _selectVisiblePassageiros,
                          icon: const Icon(Icons.playlist_add_check),
                          label: const Text('Selecionar visíveis'),
                        ),
                        TextButton.icon(
                          onPressed:
                              _passageiroIds.isEmpty
                                  ? null
                                  : _clearSelectedPassageiros,
                          icon: const Icon(Icons.clear_all),
                          label: const Text('Limpar seleção'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    if (widget.passageiros.isEmpty)
                      const Text(
                        'Cadastre passageiros antes de liberar acesso aos serviços.',
                        style: TextStyle(color: AdminPortalColors.textMuted),
                      )
                    else if (filteredPassageiros.isEmpty)
                      const Text(
                        'Nenhum passageiro encontrado para esta busca.',
                        style: TextStyle(color: AdminPortalColors.textMuted),
                      )
                    else
                      for (final passageiro in filteredPassageiros)
                        CheckboxListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(passageiro.nome),
                          subtitle: Text(passageiro.email),
                          value: _passageiroIds.contains(passageiro.id),
                          onChanged: (checked) {
                            setState(() {
                              if (checked == true) {
                                _passageiroIds.add(passageiro.id);
                              } else {
                                _passageiroIds.remove(passageiro.id);
                              }
                            });
                          },
                        ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AdminServicoFormImpactNotice extends StatelessWidget {
  const _AdminServicoFormImpactNotice();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AdminPortalColors.blueSoft,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AdminPortalColors.divider),
      ),
      child: const Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, color: AdminPortalColors.blue, size: 20),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              'Alterações salvas aqui atualizam a base recorrente e valem para as próximas montagens. Se a viagem de hoje já existe, revise em Viagens do dia.',
              style: TextStyle(
                color: AdminPortalColors.text,
                height: 1.4,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
