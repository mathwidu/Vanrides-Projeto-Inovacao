import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_motorista_link_record.dart';
import 'admin_portal_layout.dart';
import 'admin_vinculos_widgets.dart';

class AdminVinculosPassageiroMotoristaPanel extends StatelessWidget {
  const AdminVinculosPassageiroMotoristaPanel({
    super.key,
    required this.links,
    required this.onRefresh,
    required this.onUnlink,
  });

  final List<AdminPassageiroMotoristaLinkRecord> links;
  final Future<void> Function() onRefresh;
  final Future<void> Function(AdminPassageiroMotoristaLinkRecord link) onUnlink;

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
                      'Atalho motorista',
                      style: TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'LEGADO / APOIO OPERACIONAL',
                      style: adminVinculosTableHeaderStyle,
                    ),
                  ],
                ),
              ),
              TextButton.icon(
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh, size: 16),
                label: const Text('Ver todos'),
              ),
            ],
          ),
          const SizedBox(height: 20),
          if (links.isEmpty)
            const AdminVinculosInlineEmptyState(
              title: 'Nenhum atalho cadastrado',
              body:
                  'Passageiros podem usar serviços liberados mesmo sem motorista fixo. '
                  'Use este bloco só quando precisar manter um motorista de referência.',
            )
          else
            ...links.map(
              (link) => Container(
                margin: const EdgeInsets.only(bottom: 14),
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: AdminPortalColors.elevatedSurface.withValues(
                    alpha: 0.72,
                  ),
                  borderRadius: BorderRadius.circular(32),
                  border: Border.all(color: AdminPortalColors.outline),
                ),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 22,
                      backgroundColor: AdminPortalColors.blueSoft,
                      child: Text(
                        link.passageiroNome.substring(0, 1).toUpperCase(),
                        style: const TextStyle(
                          color: AdminPortalColors.text,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            link.passageiroNome,
                            style: const TextStyle(
                              color: AdminPortalColors.text,
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          Text(
                            link.menorDeIdade
                                ? 'Passageiro Escolar'
                                : 'Passageiro Corporativo',
                            style: const TextStyle(
                              color: AdminPortalColors.textMuted,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 12),
                      child: Icon(
                        Icons.arrow_forward,
                        color: AdminPortalColors.textMuted,
                      ),
                    ),
                    CircleAvatar(
                      radius: 22,
                      backgroundColor: AdminPortalColors.orangeSoft,
                      child: Text(
                        (link.motorista?.nome ?? '?')
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
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            link.motorista?.nome ?? 'Sem atalho operacional',
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: AdminPortalColors.text,
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const Text(
                            'A viagem do dia define o motorista real',
                            style: TextStyle(
                              color: AdminPortalColors.textMuted,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: () => onUnlink(link),
                      icon: const Icon(Icons.link_off_outlined),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
