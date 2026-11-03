import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_responsavel_passageiro_link_record.dart';
import '../../domain/admin_responsavel_record.dart';
import 'admin_portal_layout.dart';
import 'admin_vinculos_widgets.dart';

class AdminVinculosResponsavelPassageiroPanel extends StatelessWidget {
  const AdminVinculosResponsavelPassageiroPanel({
    super.key,
    required this.links,
    required this.onCreateLink,
    required this.onUnlink,
  });

  final List<AdminResponsavelPassageiroLinkRecord> links;
  final Future<void> Function() onCreateLink;
  final Future<void> Function(AdminResponsavelPassageiroLinkRecord link)
  onUnlink;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 48,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Custódia responsável-passageiro',
                      style: TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'CUSTÓDIA E SUPERVISÃO',
                      style: adminVinculosTableHeaderStyle,
                    ),
                  ],
                ),
              ),
              const AdminPortalTag(
                label: '3 Pendentes',
                background: AdminPortalColors.orangeSoft,
                foreground: AdminPortalColors.warningText,
              ),
              const SizedBox(width: 12),
              TextButton(onPressed: onCreateLink, child: const Text('Ajustar')),
            ],
          ),
          const SizedBox(height: 20),
          if (links.isEmpty)
            const AdminVinculosInlineEmptyState(
              title: 'Nenhum vínculo responsável cadastrado',
              body:
                  'Aqui aparecem os responsáveis e os passageiros que eles acompanham. '
                  'Faça associações comuns nas fichas de Responsáveis ou Passageiros; use este painel para conferir exceções.',
            )
          else
            Wrap(
              spacing: 16,
              runSpacing: 16,
              children: links
                  .map((link) {
                    return SizedBox(
                      width: 320,
                      child: Container(
                        padding: const EdgeInsets.all(20),
                        decoration: BoxDecoration(
                          color: AdminPortalColors.elevatedSurface.withValues(
                            alpha: 0.78,
                          ),
                          borderRadius: BorderRadius.circular(32),
                          border: Border.all(color: AdminPortalColors.outline),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                CircleAvatar(
                                  radius: 20,
                                  backgroundColor: AdminPortalColors.orangeSoft,
                                  child: Text(
                                    link.responsavel.nome
                                        .substring(0, 1)
                                        .toUpperCase(),
                                    style: const TextStyle(
                                      color: AdminPortalColors.text,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        link.responsavel.nome,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(
                                          color: AdminPortalColors.text,
                                          fontSize: 15,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                      const Text(
                                        'RESPONSÁVEL PRINCIPAL',
                                        style: TextStyle(
                                          color: AdminPortalColors.blue,
                                          fontSize: 10,
                                          fontWeight: FontWeight.w800,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                PopupMenuButton<String>(
                                  itemBuilder:
                                      (context) => const [
                                        PopupMenuItem(
                                          value: 'remove',
                                          child: Text('Remover vínculo'),
                                        ),
                                      ],
                                  onSelected: (_) => onUnlink(link),
                                  icon: const Icon(Icons.more_vert, size: 18),
                                ),
                              ],
                            ),
                            const SizedBox(height: 16),
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: AdminPortalColors.statusSurface,
                                borderRadius: BorderRadius.circular(18),
                                border: Border.all(
                                  color: AdminPortalColors.outline,
                                ),
                              ),
                              child: Row(
                                children: [
                                  CircleAvatar(
                                    radius: 16,
                                    backgroundColor: AdminPortalColors.blueSoft,
                                    child: Text(
                                      link.passageiro.nome
                                          .substring(0, 1)
                                          .toUpperCase(),
                                      style: const TextStyle(
                                        color: AdminPortalColors.text,
                                        fontSize: 11,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          link.passageiro.nome,
                                          style: const TextStyle(
                                            color: AdminPortalColors.text,
                                            fontSize: 12,
                                            fontWeight: FontWeight.w700,
                                          ),
                                        ),
                                        Text(
                                          link.passageiro.menorDeIdade
                                              ? 'Filho'
                                              : 'Dependente',
                                          style: const TextStyle(
                                            color: AdminPortalColors.textMuted,
                                            fontSize: 10,
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
                      ),
                    );
                  })
                  .toList(growable: false),
            ),
        ],
      ),
    );
  }
}

class AdminVinculosResponsavelPassageiroDialog extends StatefulWidget {
  const AdminVinculosResponsavelPassageiroDialog({
    super.key,
    required this.responsaveis,
    required this.passageiros,
  });

  final List<AdminResponsavelRecord> responsaveis;
  final List<AdminPassageiroRecord> passageiros;

  @override
  State<AdminVinculosResponsavelPassageiroDialog> createState() =>
      _AdminVinculosResponsavelPassageiroDialogState();
}

class _AdminVinculosResponsavelPassageiroDialogState
    extends State<AdminVinculosResponsavelPassageiroDialog> {
  int? _responsavelId;
  int? _passageiroId;

  @override
  void initState() {
    super.initState();
    _responsavelId =
        widget.responsaveis.isEmpty ? null : widget.responsaveis.first.id;
    _passageiroId =
        widget.passageiros.isEmpty ? null : widget.passageiros.first.id;
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Novo vínculo responsável-passageiro'),
      content: SizedBox(
        width: 420,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            DropdownButtonFormField<int>(
              initialValue: _responsavelId,
              decoration: const InputDecoration(labelText: 'Responsável'),
              items: widget.responsaveis
                  .map(
                    (item) => DropdownMenuItem<int>(
                      value: item.id,
                      child: Text(item.nome),
                    ),
                  )
                  .toList(growable: false),
              onChanged: (value) => setState(() => _responsavelId = value),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<int>(
              initialValue: _passageiroId,
              decoration: const InputDecoration(labelText: 'Passageiro'),
              items: widget.passageiros
                  .map(
                    (item) => DropdownMenuItem<int>(
                      value: item.id,
                      child: Text(item.nome),
                    ),
                  )
                  .toList(growable: false),
              onChanged: (value) => setState(() => _passageiroId = value),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed:
              _responsavelId == null || _passageiroId == null
                  ? null
                  : () => Navigator.of(context).pop(
                    AdminVinculosResponsavelPassageiroSelection(
                      responsavelId: _responsavelId!,
                      passageiroId: _passageiroId!,
                    ),
                  ),
          child: const Text('Criar vínculo'),
        ),
      ],
    );
  }
}

class AdminVinculosResponsavelPassageiroSelection {
  const AdminVinculosResponsavelPassageiroSelection({
    required this.responsavelId,
    required this.passageiroId,
  });

  final int responsavelId;
  final int passageiroId;
}
