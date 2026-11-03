import 'package:flutter/material.dart';

import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_rota_base_record.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import '../../domain/admin_viagem_create_input.dart';
import 'admin_portal_layout.dart';
import 'admin_viagens_widgets.dart';

class AdminViagemCreateView extends StatefulWidget {
  const AdminViagemCreateView({
    super.key,
    required this.rotas,
    required this.servicos,
    required this.motoristas,
    required this.isSubmitting,
    required this.onCancel,
    required this.onSubmit,
    this.initialDate,
  });

  final List<AdminRotaBaseRecord> rotas;
  final List<AdminServicoRecorrenteRecord> servicos;
  final List<AdminMotoristaRecord> motoristas;
  final bool isSubmitting;
  final VoidCallback onCancel;
  final ValueChanged<AdminViagemCreateInput> onSubmit;
  final DateTime? initialDate;

  @override
  State<AdminViagemCreateView> createState() => _AdminViagemCreateViewState();
}

class _AdminViagemCreateViewState extends State<AdminViagemCreateView> {
  final _formKey = GlobalKey<FormState>();
  int? _servicoId;
  int? _motoristaId;
  int? _rotaBaseId;
  DateTime? _dataOperacao;

  @override
  void initState() {
    super.initState();
    final value = widget.initialDate ?? DateTime.now();
    _dataOperacao = DateTime(value.year, value.month, value.day);
  }

  AdminServicoRecorrenteRecord? get _selectedServico {
    if (_servicoId == null) return null;
    return widget.servicos.cast<AdminServicoRecorrenteRecord?>().firstWhere(
      (item) => item?.id == _servicoId,
      orElse: () => null,
    );
  }

  AdminMotoristaRecord? get _selectedMotorista {
    if (_motoristaId == null) return null;
    return widget.motoristas.cast<AdminMotoristaRecord?>().firstWhere(
      (item) => item?.id == _motoristaId,
      orElse: () => null,
    );
  }

  AdminRotaBaseRecord? get _selectedRota {
    if (_rotaBaseId == null) return null;
    return widget.rotas.cast<AdminRotaBaseRecord?>().firstWhere(
      (item) => item?.id == _rotaBaseId,
      orElse: () => null,
    );
  }

  void _submit() {
    if (!_formKey.currentState!.validate() || _dataOperacao == null) {
      return;
    }

    if (_servicoId == null && _rotaBaseId == null) {
      return;
    }

    widget.onSubmit(
      AdminViagemCreateInput(
        servicoId: _servicoId,
        motoristaId: _motoristaId,
        rotaBaseId: _servicoId == null ? _rotaBaseId : null,
        dataOperacao: _dataOperacao!,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final servico = _selectedServico;
    final rota = _selectedRota;
    final motoristaOperacional =
        _selectedMotorista ?? servico?.motoristaPadrao ?? rota?.motorista;
    final quantidadeParadas =
        servico?.quantidadePassageiros ?? rota?.quantidadeParadas ?? 0;
    final horarioPartida = servico?.horarioPartida ?? rota?.horarioPartida;
    final nomeOperacional = servico?.nome ?? rota?.nome;
    final destinoFinal =
        servico?.destinoFinalNome ??
        rota?.destinoFinalNome ??
        'Destino final será calculado pela rota operacional no início da viagem.';

    return AdminPortalPage(
      breadcrumb: 'Viagens • Exceção do dia',
      title: 'Exceção do dia',
      description:
          'Use só para uma viagem pontual fora do plano recorrente. Se o serviço já tem viagem planejada, abra a viagem existente e use Sincronizar lista.',
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AdminPortalSecondaryButton(
            label: 'Descartar',
            onPressed: widget.onCancel,
          ),
          const SizedBox(width: 12),
          AdminPortalPrimaryButton(
            label: widget.isSubmitting ? 'Confirmando...' : 'Criar exceção',
            icon: Icons.check_rounded,
            onPressed: widget.isSubmitting ? null : _submit,
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            flex: 7,
            child: Column(
              children: [
                AdminPortalSurface(
                  radius: 48,
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Detalhes da Operação',
                          style: TextStyle(
                            color: AdminPortalColors.text,
                            fontSize: 26,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 24),
                        const AdminViagensFieldLabel(
                          label: 'Serviço recorrente',
                        ),
                        const SizedBox(height: 8),
                        DropdownButtonFormField<int>(
                          initialValue: _servicoId,
                          decoration: adminViagensInputDecoration(
                            hintText: 'Selecione a linha/serviço do dia...',
                          ),
                          items: widget.servicos
                              .map(
                                (servico) => DropdownMenuItem<int>(
                                  value: servico.id,
                                  child: Text(
                                    '${servico.nome} • ${servico.tipoOperacaoLabel}',
                                  ),
                                ),
                              )
                              .toList(growable: false),
                          onChanged: (value) {
                            setState(() {
                              _servicoId = value;
                              _rotaBaseId = null;
                              _motoristaId = null;
                            });
                          },
                          validator: (value) {
                            if (value == null && _rotaBaseId == null) {
                              return 'Selecione um serviço recorrente.';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          'A exceção copia a lista do serviço nesta data. Não use este caminho para adicionar passageiro em uma viagem que já existe.',
                          style: TextStyle(
                            color: AdminPortalColors.textMuted,
                            fontSize: 11,
                          ),
                        ),
                        if (widget.servicos.isEmpty &&
                            widget.rotas.isNotEmpty) ...[
                          const SizedBox(height: 16),
                          const AdminViagensFieldLabel(
                            label: 'Fallback legado por rota-base',
                          ),
                          const SizedBox(height: 8),
                          DropdownButtonFormField<int>(
                            initialValue: _rotaBaseId,
                            decoration: adminViagensInputDecoration(
                              hintText: 'Selecione uma rota-base legada...',
                            ),
                            items: widget.rotas
                                .map(
                                  (rota) => DropdownMenuItem<int>(
                                    value: rota.id,
                                    child: Text(rota.nome),
                                  ),
                                )
                                .toList(growable: false),
                            onChanged:
                                (value) => setState(() => _rotaBaseId = value),
                          ),
                        ],
                        const SizedBox(height: 24),
                        Row(
                          children: [
                            Expanded(
                              child: AdminViagensDateField(
                                label: 'Data da exceção',
                                value:
                                    _dataOperacao == null
                                        ? 'mm/dd/yyyy'
                                        : adminViagensFormatShortDate(
                                          _dataOperacao!,
                                        ),
                                onTap: () async {
                                  final now = DateTime.now();
                                  final selected = await showDatePicker(
                                    context: context,
                                    initialDate: _dataOperacao ?? now,
                                    firstDate: DateTime(now.year - 1),
                                    lastDate: DateTime(now.year + 1),
                                    builder: adminViagensDatePickerBuilder,
                                  );
                                  if (selected != null) {
                                    setState(() => _dataOperacao = selected);
                                  }
                                },
                              ),
                            ),
                            const SizedBox(width: 16),
                            Expanded(
                              child: AdminViagensDisabledField(
                                label: 'Horário de Saída',
                                value: horarioPartida ?? '--:--',
                                helper:
                                    horarioPartida != null
                                        ? 'Horário herdado do serviço selecionado.'
                                        : 'Selecione um serviço para ver o horário de partida.',
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),
                        const AdminViagensFieldLabel(
                          label: 'Motorista operacional',
                        ),
                        const SizedBox(height: 8),
                        DropdownButtonFormField<int>(
                          initialValue: _motoristaId,
                          decoration: adminViagensInputDecoration(
                            hintText:
                                servico?.motoristaPadrao == null
                                    ? 'Selecione o motorista desta viagem...'
                                    : 'Usar motorista padrão ou trocar...',
                          ),
                          items: widget.motoristas
                              .map(
                                (motorista) => DropdownMenuItem<int>(
                                  value: motorista.id,
                                  child: Text(motorista.nome),
                                ),
                              )
                              .toList(growable: false),
                          onChanged:
                              (value) => setState(() => _motoristaId = value),
                          validator: (value) {
                            if (servico != null &&
                                !servico.hasMotoristaPadrao &&
                                value == null) {
                              return 'Selecione um motorista para este serviço.';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 8),
                        Text(
                          motoristaOperacional == null
                              ? 'Serviços sem motorista padrão precisam de um motorista operacional para gerar a viagem.'
                              : 'Motorista operacional: ${motoristaOperacional.nome}',
                          style: const TextStyle(
                            color: AdminPortalColors.textMuted,
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 24),
                if (motoristaOperacional != null)
                  AdminPortalSurface(
                    radius: 40,
                    color: AdminPortalColors.surfaceSoft,
                    child: Row(
                      children: [
                        CircleAvatar(
                          radius: 32,
                          backgroundColor: AdminPortalColors.blueSoft,
                          child: Text(
                            motoristaOperacional.nome
                                .substring(0, 1)
                                .toUpperCase(),
                            style: const TextStyle(
                              color: AdminPortalColors.text,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                motoristaOperacional.nome,
                                style: const TextStyle(
                                  color: AdminPortalColors.text,
                                  fontSize: 24,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                'Motorista operacional desta viagem • ${motoristaOperacional.telefone}',
                                style: const TextStyle(
                                  color: AdminPortalColors.textMuted,
                                  fontSize: 14,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const AdminPortalTag(
                          label: 'Motorista padrão',
                          background: AdminPortalColors.blueSoft,
                          foreground: AdminPortalColors.blue,
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 24),
          Expanded(
            flex: 5,
            child: Column(
              children: [
                AdminPortalSurface(
                  radius: 40,
                  color: AdminPortalColors.navy,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const AdminPortalTag(
                        label: 'RESUMO DO PERCURSO',
                        background: Color(0x1FFFFFFF),
                        foreground: Colors.white70,
                      ),
                      const SizedBox(height: 16),
                      Text(
                        nomeOperacional ?? 'Serviço ainda não selecionado',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 32,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 24),
                      const AdminViagensDarkInfoLine(
                        label: 'Ponto de partida',
                        value: 'Garagem operacional da empresa',
                      ),
                      const SizedBox(height: 18),
                      AdminViagensDarkInfoLine(
                        label: 'Paradas intermediárias',
                        value:
                            nomeOperacional == null
                                ? 'Selecione um serviço para visualizar'
                                : '$quantidadeParadas passageiros operacionais',
                      ),
                      const SizedBox(height: 18),
                      AdminViagensDarkInfoLine(
                        label: 'Destino final',
                        value: destinoFinal,
                      ),
                      const SizedBox(height: 24),
                      const Divider(color: Color(0x26FFFFFF)),
                      const SizedBox(height: 24),
                      AdminViagensDarkMetricRow(
                        leftLabel: 'DISTÂNCIA EST.',
                        leftValue:
                            nomeOperacional == null
                                ? '-- km'
                                : '${(quantidadeParadas * 1.2).toStringAsFixed(1)} km',
                        rightLabel: 'TEMPO DE VIAGEM',
                        rightValue:
                            nomeOperacional == null
                                ? '-- min'
                                : '${(quantidadeParadas * 4).clamp(20, 90)} min',
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                const AdminPortalSurface(
                  radius: 32,
                  color: AdminPortalColors.surfaceSoft,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.info_outline_rounded,
                        color: AdminPortalColors.blue,
                      ),
                      SizedBox(width: 16),
                      Expanded(
                        child: Text(
                          'Mudanças de elenco devem acontecer em Serviços ou Passageiros. Se a viagem do dia já existe, abra o detalhe e use Sincronizar lista antes do início.',
                          style: TextStyle(
                            color: AdminPortalColors.textMuted,
                            fontSize: 14,
                            height: 1.55,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                AdminPortalSurface(
                  radius: 32,
                  color: AdminPortalColors.orangeSoft,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(
                        Icons.warning_amber_rounded,
                        color: AdminPortalColors.warningText,
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Text(
                          nomeOperacional == null
                              ? 'Selecione um serviço para avaliar ocupação e coerência operacional antes de confirmar a viagem.'
                              : 'Este serviço possui $quantidadeParadas passageiros elegíveis. A viagem do dia respeita ausências e a política operacional de confirmação.',
                          style: const TextStyle(
                            color: AdminPortalColors.warningText,
                            fontSize: 14,
                            height: 1.6,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
