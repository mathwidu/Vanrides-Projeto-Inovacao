import 'package:flutter/material.dart';

import '../../domain/admin_company_link_flow_models.dart';
import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_portal_layout.dart';
import 'admin_vinculos_service_widgets.dart';

enum AdminAccessRequestDecisionType { approve, reject }

class AdminAccessRequestDecision {
  const AdminAccessRequestDecision({
    required this.type,
    this.motoristaId,
    this.reason,
    this.servicoIds = const [],
  });

  final AdminAccessRequestDecisionType type;
  final int? motoristaId;
  final String? reason;
  final List<int> servicoIds;
}

class AdminAccessRequestDecisionDialog extends StatefulWidget {
  const AdminAccessRequestDecisionDialog({
    super.key,
    required this.detail,
    required this.motoristas,
    required this.servicos,
  });

  final AdminAccessRequestDetailRecord detail;
  final List<AdminMotoristaRecord> motoristas;
  final List<AdminServicoRecorrenteRecord> servicos;

  @override
  State<AdminAccessRequestDecisionDialog> createState() =>
      _AdminAccessRequestDecisionDialogState();
}

class _AdminAccessRequestDecisionDialogState
    extends State<AdminAccessRequestDecisionDialog> {
  final _reasonController = TextEditingController();
  int? _motoristaId;
  final Set<int> _selectedServicoIds = {};

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Revisar solicitação'),
      content: SizedBox(
        width: 520,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                widget.detail.passageiroNome,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 6),
              Text(widget.detail.passageiroEmail),
              if (widget.detail.passageiroTelefone.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(widget.detail.passageiroTelefone),
              ],
              const SizedBox(height: 16),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  AdminPortalTag(
                    label:
                        widget.detail.emailVerified
                            ? 'E-mail verificado'
                            : 'E-mail pendente',
                    background:
                        widget.detail.emailVerified
                            ? AdminPortalColors.greenSoft
                            : AdminPortalColors.orangeSoft,
                    foreground:
                        widget.detail.emailVerified
                            ? AdminPortalColors.green
                            : AdminPortalColors.orange,
                  ),
                  AdminPortalTag(
                    label:
                        widget.detail.source == 'INVITE_CODE'
                            ? 'Veio por convite'
                            : 'Veio por solicitação',
                  ),
                ],
              ),
              const SizedBox(height: 18),
              DropdownButtonFormField<int>(
                initialValue: widget.detail.motoristaAtual?.id,
                decoration: const InputDecoration(
                  labelText: 'Motorista operacional inicial',
                ),
                items: [
                  const DropdownMenuItem<int>(
                    value: null,
                    child: Text('Definir pela viagem do dia'),
                  ),
                  ...widget.motoristas.map(
                    (motorista) => DropdownMenuItem<int>(
                      value: motorista.id,
                      child: Text(motorista.nome),
                    ),
                  ),
                ],
                onChanged: (value) => setState(() => _motoristaId = value),
              ),
              const SizedBox(height: 18),
              const Text(
                'Serviços liberados',
                style: TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              if (widget.servicos.isEmpty)
                const _DialogNotice(
                  text:
                      'Nenhum serviço recorrente cadastrado. O passageiro pode ser aprovado e liberado depois em Serviços.',
                )
              else
                ...widget.servicos.map(
                  (servico) => CheckboxListTile(
                    value: _selectedServicoIds.contains(servico.id),
                    onChanged:
                        (checked) => setState(() {
                          if (checked == true) {
                            _selectedServicoIds.add(servico.id);
                          } else {
                            _selectedServicoIds.remove(servico.id);
                          }
                        }),
                    dense: true,
                    controlAffinity: ListTileControlAffinity.leading,
                    contentPadding: EdgeInsets.zero,
                    title: Text(servico.nome),
                    subtitle: Text(adminVinculosServiceApprovalLabel(servico)),
                  ),
                ),
              if (widget.servicos.isNotEmpty &&
                  _selectedServicoIds.isEmpty) ...[
                const SizedBox(height: 6),
                const _DialogNotice(
                  text:
                      'Sem serviço selecionado, o passageiro entra na empresa sem viagens liberadas.',
                ),
              ],
              const SizedBox(height: 14),
              TextField(
                controller: _reasonController,
                minLines: 2,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: 'Observação da decisão (opcional)',
                  hintText: 'Ex.: motorista será escalado na viagem do dia',
                ),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        TextButton(
          onPressed:
              () => Navigator.of(context).pop(
                AdminAccessRequestDecision(
                  type: AdminAccessRequestDecisionType.reject,
                  reason: _reasonController.text.trim(),
                ),
              ),
          child: const Text('Rejeitar'),
        ),
        FilledButton(
          onPressed:
              () => Navigator.of(context).pop(
                AdminAccessRequestDecision(
                  type: AdminAccessRequestDecisionType.approve,
                  motoristaId: _motoristaId ?? widget.detail.motoristaAtual?.id,
                  reason: _reasonController.text.trim(),
                  servicoIds: _selectedServicoIds.toList(growable: false),
                ),
              ),
          child: const Text('Aprovar vínculo'),
        ),
      ],
    );
  }
}

class _DialogNotice extends StatelessWidget {
  const _DialogNotice({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AdminPortalColors.orangeSoft,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: AdminPortalColors.orange,
          fontSize: 12,
          height: 1.4,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
