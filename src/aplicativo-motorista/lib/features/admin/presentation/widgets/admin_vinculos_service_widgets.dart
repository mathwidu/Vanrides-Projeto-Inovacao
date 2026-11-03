import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_portal_layout.dart';
import 'admin_vinculos_widgets.dart';

String adminVinculosServiceApprovalLabel(AdminServicoRecorrenteRecord servico) {
  final horario =
      servico.horarioPartida == null ? 'sem horário' : servico.horarioPartida!;
  final passageiros =
      servico.quantidadePassageiros == 1
          ? '1 elegível'
          : '${servico.quantidadePassageiros} elegíveis';
  return '${servico.tipoOperacaoLabel} • $horario • $passageiros';
}

String _serviceRightLabel(AdminServicoRecorrenteRecord servico) {
  final horario = servico.horarioPartida?.trim();
  if (horario == null || horario.isEmpty) {
    return '${servico.tipoOperacaoLabel} • ${servico.nome}';
  }
  return '$horario ${servico.tipoOperacaoLabel} • ${servico.nome}';
}

class AdminVinculosPassageiroServicosPanel extends StatelessWidget {
  const AdminVinculosPassageiroServicosPanel({
    super.key,
    required this.passageiros,
    required this.servicos,
    required this.onEdit,
  });

  final List<AdminPassageiroRecord> passageiros;
  final List<AdminServicoRecorrenteRecord> servicos;
  final Future<void> Function(AdminPassageiroRecord passageiro) onEdit;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 40,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(
                child: Text(
                  'Direitos por serviço',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              AdminPortalTag(
                label: '${servicos.length} serviços',
                background: AdminPortalColors.blueSoft,
                foreground: AdminPortalColors.blue,
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            'Ajustes comuns ficam nas fichas de Passageiros e Serviços. Use este painel para conferir divergências e corrigir acessos em lote.',
            style: TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 14,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 20),
          if (passageiros.isEmpty)
            const AdminVinculosInlineEmptyState(
              title: 'Nenhum passageiro aprovado',
              body:
                  'Quando houver passageiros na empresa, os direitos de uso dos serviços aparecem aqui.',
            )
          else if (servicos.isEmpty)
            const AdminVinculosInlineEmptyState(
              title: 'Nenhum serviço recorrente',
              body:
                  'Crie serviços recorrentes para liberar ida, volta ou turnos específicos aos passageiros.',
            )
          else
            ...passageiros.map((passageiro) {
              final enabledServicos = servicos
                  .where(
                    (servico) => servico.passageiroIds.contains(passageiro.id),
                  )
                  .toList(growable: false);
              return Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AdminPortalColors.canvas,
                    borderRadius: BorderRadius.circular(22),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      CircleAvatar(
                        radius: 19,
                        backgroundColor: AdminPortalColors.blueSoft,
                        child: Text(
                          passageiro.nome.isEmpty
                              ? '?'
                              : passageiro.nome.substring(0, 1).toUpperCase(),
                          style: const TextStyle(
                            color: AdminPortalColors.blue,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              passageiro.nome,
                              style: const TextStyle(
                                color: AdminPortalColors.text,
                                fontSize: 15,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              enabledServicos.isEmpty
                                  ? 'Sem serviço liberado'
                                  : '${enabledServicos.length} de ${servicos.length} serviços liberados',
                              style: const TextStyle(
                                color: AdminPortalColors.textMuted,
                                fontSize: 12,
                              ),
                            ),
                            const SizedBox(height: 10),
                            Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children:
                                  enabledServicos.isEmpty
                                      ? const [
                                        AdminPortalTag(
                                          label: 'Sem acesso operacional',
                                          background:
                                              AdminPortalColors.orangeSoft,
                                          foreground: AdminPortalColors.orange,
                                        ),
                                      ]
                                      : [
                                        ...enabledServicos
                                            .take(3)
                                            .map(
                                              (servico) => AdminPortalTag(
                                                label: _serviceRightLabel(
                                                  servico,
                                                ),
                                                background:
                                                    AdminPortalColors.greenSoft,
                                                foreground:
                                                    AdminPortalColors.green,
                                              ),
                                            ),
                                        if (enabledServicos.length > 3)
                                          AdminPortalTag(
                                            label:
                                                '+${enabledServicos.length - 3} serviços',
                                            background:
                                                AdminPortalColors.surfaceSoft,
                                          ),
                                      ],
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      IconButton(
                        onPressed: () => onEdit(passageiro),
                        icon: const Icon(Icons.tune_rounded),
                        color: AdminPortalColors.blue,
                        tooltip: 'Editar serviços de ${passageiro.nome}',
                      ),
                    ],
                  ),
                ),
              );
            }),
        ],
      ),
    );
  }
}

class AdminVinculosPassageiroServicosDialog extends StatefulWidget {
  const AdminVinculosPassageiroServicosDialog({
    super.key,
    required this.passageiro,
    required this.servicos,
    required this.initialServicoIds,
  });

  final AdminPassageiroRecord passageiro;
  final List<AdminServicoRecorrenteRecord> servicos;
  final Set<int> initialServicoIds;

  @override
  State<AdminVinculosPassageiroServicosDialog> createState() =>
      _AdminVinculosPassageiroServicosDialogState();
}

class _AdminVinculosPassageiroServicosDialogState
    extends State<AdminVinculosPassageiroServicosDialog> {
  late final Set<int> _selectedServicoIds;

  @override
  void initState() {
    super.initState();
    _selectedServicoIds = {...widget.initialServicoIds};
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 620),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Serviços liberados para ${widget.passageiro.nome}',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'O passageiro só poderá marcar presença e entrar nas viagens geradas a partir dos serviços selecionados.',
                style: TextStyle(color: AdminPortalColors.textMuted),
              ),
              const SizedBox(height: 20),
              Flexible(
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: widget.servicos.length,
                  separatorBuilder:
                      (context, index) => const Divider(height: 1),
                  itemBuilder: (context, index) {
                    final servico = widget.servicos[index];
                    return CheckboxListTile(
                      value: _selectedServicoIds.contains(servico.id),
                      contentPadding: EdgeInsets.zero,
                      title: Text(servico.nome),
                      subtitle: Text(
                        adminVinculosServiceApprovalLabel(servico),
                      ),
                      secondary: Icon(
                        servico.tipoOperacao.toUpperCase() == 'VOLTA'
                            ? Icons.keyboard_return_rounded
                            : Icons.route_outlined,
                        color: AdminPortalColors.blue,
                      ),
                      onChanged: (value) {
                        setState(() {
                          if (value == true) {
                            _selectedServicoIds.add(servico.id);
                          } else {
                            _selectedServicoIds.remove(servico.id);
                          }
                        });
                      },
                    );
                  },
                ),
              ),
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Cancelar'),
                  ),
                  const SizedBox(width: 12),
                  FilledButton.icon(
                    onPressed: () {
                      final orderedIds = widget.servicos
                          .where(
                            (servico) =>
                                _selectedServicoIds.contains(servico.id),
                          )
                          .map((servico) => servico.id)
                          .toList(growable: false);
                      Navigator.of(context).pop(orderedIds);
                    },
                    icon: const Icon(Icons.save_outlined),
                    label: const Text('Salvar serviços'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
