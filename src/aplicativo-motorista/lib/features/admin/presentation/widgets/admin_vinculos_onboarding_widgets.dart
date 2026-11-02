import 'package:flutter/material.dart';

import '../../domain/admin_company_link_flow_models.dart';
import 'admin_portal_layout.dart';
import 'admin_vinculos_widgets.dart';

class AdminVinculosAccessRequestsPanel extends StatelessWidget {
  const AdminVinculosAccessRequestsPanel({
    super.key,
    required this.requests,
    required this.onRefresh,
    required this.onOpenRequest,
  });

  final List<AdminAccessRequestRecord> requests;
  final Future<void> Function() onRefresh;
  final Future<void> Function(AdminAccessRequestRecord request) onOpenRequest;

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
                  'Solicitações de acesso',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              IconButton(
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh_rounded),
                tooltip: 'Atualizar solicitações',
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            'A primeira aprovação vincula o passageiro à empresa. O motorista continua opcional nessa etapa.',
            style: TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 14,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 20),
          if (requests.isEmpty)
            const AdminVinculosInlineEmptyState(
              title: 'Nenhuma pendência agora',
              body:
                  'Quando um passageiro solicitar acesso pelo app, ele aparecerá aqui para aprovação ou rejeição.',
            )
          else
            ...requests.map(
              (request) => Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: InkWell(
                  onTap: () => onOpenRequest(request),
                  borderRadius: BorderRadius.circular(24),
                  child: Ink(
                    padding: const EdgeInsets.all(18),
                    decoration: BoxDecoration(
                      color: AdminPortalColors.canvas,
                      borderRadius: BorderRadius.circular(24),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 46,
                          height: 46,
                          decoration: BoxDecoration(
                            color:
                                request.emailVerified
                                    ? AdminPortalColors.blueSoft
                                    : AdminPortalColors.orangeSoft,
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: Icon(
                            request.emailVerified
                                ? Icons.verified_user_outlined
                                : Icons.mark_email_unread_outlined,
                            color:
                                request.emailVerified
                                    ? AdminPortalColors.blue
                                    : AdminPortalColors.orange,
                          ),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                request.passageiroNome,
                                style: const TextStyle(
                                  color: AdminPortalColors.text,
                                  fontSize: 16,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                request.passageiroEmail,
                                style: const TextStyle(
                                  color: AdminPortalColors.textMuted,
                                  fontSize: 13,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  AdminPortalTag(
                                    label:
                                        request.source == 'INVITE_CODE'
                                            ? 'Convite'
                                            : 'Solicitação',
                                    background: AdminPortalColors.surfaceSoft,
                                  ),
                                  AdminPortalTag(
                                    label:
                                        request.emailVerified
                                            ? 'E-mail verificado'
                                            : 'E-mail pendente',
                                    background:
                                        request.emailVerified
                                            ? AdminPortalColors.greenSoft
                                            : AdminPortalColors.orangeSoft,
                                    foreground:
                                        request.emailVerified
                                            ? AdminPortalColors.green
                                            : AdminPortalColors.orange,
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 12),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(
                              adminVinculosFormatAdminDateTime(
                                request.requestedAt,
                              ),
                              style: const TextStyle(
                                color: AdminPortalColors.textFaint,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 10),
                            const Icon(
                              Icons.arrow_forward_rounded,
                              color: AdminPortalColors.blue,
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class AdminVinculosInviteCodesPanel extends StatelessWidget {
  const AdminVinculosInviteCodesPanel({
    super.key,
    required this.inviteCodes,
    required this.onCreateInvite,
    required this.onOpenInvite,
  });

  final List<AdminInviteCodeRecord> inviteCodes;
  final Future<void> Function() onCreateInvite;
  final Future<void> Function(AdminInviteCodeRecord invite) onOpenInvite;

  @override
  Widget build(BuildContext context) {
    final activeCount =
        inviteCodes.where((invite) => invite.status == 'ACTIVE').length;
    final usedCount =
        inviteCodes
            .where((invite) => _isUsedInviteStatus(invite.status))
            .length;
    final expiringSoon =
        inviteCodes
            .where(
              (invite) =>
                  invite.expiresAt != null &&
                  invite.expiresAt!.isBefore(
                    DateTime.now().add(const Duration(days: 7)),
                  ),
            )
            .length;

    return AdminPortalSurface(
      radius: 40,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(
                child: Text(
                  'Códigos de convite',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              AdminPortalPrimaryButton(
                label: 'Novo convite',
                icon: Icons.key_rounded,
                onPressed: onCreateInvite,
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            'Use convites quando a empresa preferir validar o passageiro antes do vínculo automático por solicitação.',
            style: TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 14,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 18),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              AdminVinculosMiniMetricCard(
                label: 'Ativos',
                value: '$activeCount',
              ),
              AdminVinculosMiniMetricCard(
                label: 'A expirar',
                value: '$expiringSoon',
              ),
              AdminVinculosMiniMetricCard(
                label: 'Utilizados',
                value: '$usedCount',
              ),
            ],
          ),
          const SizedBox(height: 20),
          if (inviteCodes.isEmpty)
            const AdminVinculosInlineEmptyState(
              title: 'Ainda não há convites ativos',
              body:
                  'Crie o primeiro código para oferecer um onboarding assistido quando a empresa quiser validar o passageiro antes.',
            )
          else
            ...inviteCodes
                .take(4)
                .map(
                  (invite) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: InkWell(
                      onTap: () => onOpenInvite(invite),
                      borderRadius: BorderRadius.circular(22),
                      child: Ink(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AdminPortalColors.canvas,
                          borderRadius: BorderRadius.circular(22),
                        ),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    invite.code,
                                    style: const TextStyle(
                                      color: AdminPortalColors.text,
                                      fontSize: 16,
                                      fontWeight: FontWeight.w800,
                                      letterSpacing: 0.8,
                                    ),
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    invite.expiresAt == null
                                        ? 'Sem expiração definida'
                                        : 'Expira em ${adminVinculosFormatAdminDateTime(invite.expiresAt)}',
                                    style: const TextStyle(
                                      color: AdminPortalColors.textMuted,
                                      fontSize: 13,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            AdminPortalTag(
                              label: adminVinculosInviteStatusLabel(
                                invite.status,
                              ),
                              background: adminVinculosInviteStatusBackground(
                                invite.status,
                              ),
                              foreground: adminVinculosInviteStatusForeground(
                                invite.status,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
        ],
      ),
    );
  }
}

enum AdminVinculosInviteDetailAction { copyCode, cancelCode, regenerateCode }

class AdminVinculosInviteCodeDetailDialog extends StatelessWidget {
  const AdminVinculosInviteCodeDetailDialog({super.key, required this.detail});

  final AdminInviteCodeDetailRecord detail;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Detalhes do convite'),
      content: SizedBox(
        width: 460,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              detail.code,
              style: const TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w800,
                letterSpacing: 0.8,
              ),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                AdminPortalTag(
                  label: adminVinculosInviteStatusLabel(detail.status),
                  background: adminVinculosInviteStatusBackground(
                    detail.status,
                  ),
                  foreground: adminVinculosInviteStatusForeground(
                    detail.status,
                  ),
                ),
                AdminPortalTag(
                  label:
                      detail.maxUses == null
                          ? 'Usos ilimitados'
                          : '${detail.useCount}/${detail.maxUses} usos',
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              detail.expiresAt == null
                  ? 'Sem expiração definida'
                  : 'Validade: ${adminVinculosFormatAdminDateTime(detail.expiresAt)}',
            ),
            const SizedBox(height: 6),
            Text(
              detail.lastUsedAt == null
                  ? 'Ainda não utilizado'
                  : 'Último uso: ${adminVinculosFormatAdminDateTime(detail.lastUsedAt)}',
            ),
            const SizedBox(height: 16),
            const Text(
              'Histórico recente',
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            if (detail.usages.isEmpty)
              const Text(
                'Nenhum passageiro utilizou este convite até agora.',
                style: TextStyle(color: AdminPortalColors.textMuted),
              )
            else
              ...detail.usages
                  .take(3)
                  .map(
                    (usage) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Text(
                        '${usage.passageiroNome} • ${usage.passageiroEmail}',
                        style: const TextStyle(
                          color: AdminPortalColors.textMuted,
                          height: 1.4,
                        ),
                      ),
                    ),
                  ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed:
              () => Navigator.of(
                context,
              ).pop(AdminVinculosInviteDetailAction.copyCode),
          child: const Text('Copiar código'),
        ),
        TextButton(
          onPressed:
              () => Navigator.of(
                context,
              ).pop(AdminVinculosInviteDetailAction.cancelCode),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed:
              () => Navigator.of(
                context,
              ).pop(AdminVinculosInviteDetailAction.regenerateCode),
          child: const Text('Regenerar'),
        ),
      ],
    );
  }
}

String adminVinculosFormatAdminDateTime(DateTime? value) {
  if (value == null) {
    return 'Sem data';
  }
  final local = value.toLocal();
  final day = local.day.toString().padLeft(2, '0');
  final month = local.month.toString().padLeft(2, '0');
  final year = local.year.toString();
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '$day/$month/$year $hour:$minute';
}

String adminVinculosInviteStatusLabel(String status) {
  return switch (status.toUpperCase()) {
    'ACTIVE' => 'Ativo',
    'USED' => 'Utilizado',
    'USED_UP' => 'Utilizado',
    'EXPIRED' => 'Expirado',
    'CANCELLED' => 'Cancelado',
    _ => status,
  };
}

Color adminVinculosInviteStatusBackground(String status) {
  return switch (status.toUpperCase()) {
    'ACTIVE' => AdminPortalColors.blueSoft,
    'USED' => const Color(0xFFE7E8FF),
    'USED_UP' => const Color(0xFFE7E8FF),
    'EXPIRED' => AdminPortalColors.redSoft,
    'CANCELLED' => AdminPortalColors.surfaceSoft,
    _ => AdminPortalColors.surfaceSoft,
  };
}

Color adminVinculosInviteStatusForeground(String status) {
  return switch (status.toUpperCase()) {
    'ACTIVE' => AdminPortalColors.blue,
    'USED' => const Color(0xFF585D7B),
    'USED_UP' => const Color(0xFF585D7B),
    'EXPIRED' => AdminPortalColors.red,
    'CANCELLED' => AdminPortalColors.textMuted,
    _ => AdminPortalColors.textMuted,
  };
}

bool _isUsedInviteStatus(String status) {
  final normalized = status.toUpperCase();
  return normalized == 'USED' || normalized == 'USED_UP';
}
