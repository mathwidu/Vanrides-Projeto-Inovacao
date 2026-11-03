import 'package:flutter/material.dart';

import '../../domain/admin_responsavel_record.dart';
import 'admin_person_avatar.dart';
import 'admin_portal_layout.dart';

export 'admin_responsaveis_detail_widgets.dart';

class AdminResponsaveisListPanel extends StatelessWidget {
  const AdminResponsaveisListPanel({
    super.key,
    required this.responsaveis,
    required this.selectedResponsavel,
    required this.onRefresh,
    required this.onCreate,
    required this.onEdit,
    required this.onDelete,
    required this.onSelect,
  });

  final List<AdminResponsavelRecord> responsaveis;
  final AdminResponsavelRecord? selectedResponsavel;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onCreate;
  final Future<void> Function() onEdit;
  final Future<void> Function() onDelete;
  final ValueChanged<AdminResponsavelRecord> onSelect;

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
                child: Text(
                  'Responsáveis cadastrados',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              IconButton(
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh_rounded),
                color: AdminPortalColors.textMuted,
                tooltip: 'Atualizar responsáveis',
              ),
            ],
          ),
          const SizedBox(height: 20),
          const _ResponsavelInviteFirstHint(),
          const SizedBox(height: 20),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                Expanded(
                  flex: 5,
                  child: Text('RESPONSÁVEL', style: _tableHeaderStyle),
                ),
                Expanded(
                  flex: 3,
                  child: Text('TELEFONE', style: _tableHeaderStyle),
                ),
                Expanded(
                  flex: 2,
                  child: Text('PASSAGEIROS', style: _tableHeaderStyle),
                ),
                SizedBox(
                  width: 96,
                  child: Text(
                    'AÇÕES',
                    style: _tableHeaderStyle,
                    textAlign: TextAlign.right,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          if (responsaveis.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Text('Nenhum responsável cadastrado para esta empresa.'),
            )
          else
            ...responsaveis.map((responsavel) {
              final isSelected = responsavel.id == selectedResponsavel?.id;
              return InkWell(
                onTap: () => onSelect(responsavel),
                borderRadius: BorderRadius.circular(32),
                child: Container(
                  margin: const EdgeInsets.only(bottom: 12),
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color:
                        isSelected
                            ? AdminPortalColors.surfaceSoft
                            : AdminPortalColors.elevatedSurface.withValues(
                              alpha: 0.72,
                            ),
                    borderRadius: BorderRadius.circular(32),
                    border: Border.all(
                      color:
                          isSelected
                              ? AdminPortalColors.blue.withValues(alpha: 0.34)
                              : AdminPortalColors.outline,
                    ),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        flex: 5,
                        child: Row(
                          children: [
                            AdminPersonAvatar(
                              name: responsavel.nome,
                              photoUrl: responsavel.fotoPerfilUrl,
                              radius: 22,
                              background: AdminPortalColors.orangeSoft,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    responsavel.nome,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      color: AdminPortalColors.text,
                                      fontSize: 16,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    responsavel.email,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      color: AdminPortalColors.textMuted,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      Expanded(
                        flex: 3,
                        child: Text(
                          responsavel.telefone,
                          style: const TextStyle(
                            color: AdminPortalColors.text,
                            fontSize: 14,
                          ),
                        ),
                      ),
                      Expanded(
                        flex: 2,
                        child: Row(
                          children: [
                            ...List.generate(
                              responsavel.passageiroCount.clamp(0, 3),
                              (index) => Transform.translate(
                                offset: Offset(
                                  index == 0 ? 0 : -10.0 * index,
                                  0,
                                ),
                                child: CircleAvatar(
                                  radius: 14,
                                  backgroundColor: AdminPortalColors.blueSoft,
                                  child: Text(
                                    index == 0 ? 'P' : '+',
                                    style: const TextStyle(
                                      color: AdminPortalColors.text,
                                      fontSize: 10,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            if (responsavel.passageiroCount == 0)
                              const Text(
                                '—',
                                style: TextStyle(
                                  color: AdminPortalColors.textMuted,
                                ),
                              ),
                          ],
                        ),
                      ),
                      SizedBox(
                        width: 96,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            IconButton(
                              onPressed: isSelected ? onEdit : null,
                              icon: const Icon(Icons.edit_outlined, size: 18),
                            ),
                            IconButton(
                              onPressed: isSelected ? onDelete : null,
                              icon: const Icon(Icons.delete_outline, size: 18),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }),
          const SizedBox(height: 12),
          Row(
            children: [
              Text(
                'Mostrando ${responsaveis.length} ${responsaveis.length == 1 ? 'responsável' : 'responsáveis'}',
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 12,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ResponsavelInviteFirstHint extends StatelessWidget {
  const _ResponsavelInviteFirstHint();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: AdminPortalColors.glassHighlight.withValues(alpha: 0.78),
        ),
      ),
      child: const Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            Icons.verified_user_outlined,
            color: AdminPortalColors.blueBright,
            size: 20,
          ),
          SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Cadastro direto ativo',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                SizedBox(height: 4),
                Text(
                  'Revise e-mail, telefone e passageiros vinculados antes de liberar acesso no app.',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    height: 1.35,
                    fontSize: 12,
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

const _tableHeaderStyle = TextStyle(
  color: AdminPortalColors.textMuted,
  fontSize: 12,
  fontWeight: FontWeight.w700,
  letterSpacing: 0.8,
);
