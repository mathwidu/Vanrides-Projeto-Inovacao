import 'package:flutter/material.dart';

import '../../domain/admin_company_link_flow_models.dart';
import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_responsavel_passageiro_link_record.dart';
import '../../domain/admin_responsavel_record.dart';
import 'admin_portal_layout.dart';

class AdminVinculosTaskQueueLoadingState extends StatelessWidget {
  const AdminVinculosTaskQueueLoadingState({super.key});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Carregando fila de onboarding administrativo',
      container: true,
      explicitChildNodes: true,
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: const AdminPortalSurface(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 20),
                Text(
                  'Carregando fila de onboarding',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                SizedBox(height: 8),
                Text(
                  'Buscando solicitações, convites e vínculos da empresa.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 15,
                    height: 1.45,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class AdminVinculosTaskQueuePanel extends StatelessWidget {
  const AdminVinculosTaskQueuePanel({
    super.key,
    required this.accessRequests,
    required this.inviteCodes,
    required this.responsavelPassageiroLinks,
    required this.passageiros,
    required this.responsaveis,
    required this.onOpenFirstRequest,
    required this.onCreateInvite,
    required this.onCreateGuardianLink,
  });

  final List<AdminAccessRequestRecord> accessRequests;
  final List<AdminInviteCodeRecord> inviteCodes;
  final List<AdminResponsavelPassageiroLinkRecord> responsavelPassageiroLinks;
  final List<AdminPassageiroRecord> passageiros;
  final List<AdminResponsavelRecord> responsaveis;
  final Future<void> Function() onOpenFirstRequest;
  final Future<void> Function() onCreateInvite;
  final Future<void> Function() onCreateGuardianLink;

  @override
  Widget build(BuildContext context) {
    final pendingRequests = accessRequests.length;
    final activeInvites =
        inviteCodes
            .where((invite) => invite.status.toUpperCase() == 'ACTIVE')
            .length;
    final guardianLinks = responsavelPassageiroLinks.length;
    final hasPendingRequest = pendingRequests > 0;
    final canCreateGuardianLink =
        passageiros.isNotEmpty && responsaveis.isNotEmpty;

    return Semantics(
      label: 'Fila de onboarding administrativo',
      container: true,
      explicitChildNodes: true,
      child: AdminPortalSurface(
        radius: 40,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final compact = constraints.maxWidth < 760;
            final primaryCard = _TaskQueuePrimaryCard(
              pendingRequests: pendingRequests,
              firstRequestName:
                  hasPendingRequest
                      ? accessRequests.first.passageiroNome
                      : null,
              onPressed:
                  hasPendingRequest ? onOpenFirstRequest : onCreateInvite,
            );
            final inviteCard = _TaskQueueSecondaryCard(
              title: 'Criar convite assistido',
              body:
                  activeInvites == 0
                      ? 'Nenhum convite ativo. Gere um código para validar o primeiro acesso.'
                      : '$activeInvites ${activeInvites == 1 ? 'convite ativo' : 'convites ativos'} para onboarding assistido.',
              buttonLabel: 'Novo convite',
              icon: Icons.key_rounded,
              onPressed: onCreateInvite,
            );
            final guardianCard = _TaskQueueSecondaryCard(
              title: 'Vínculos familiares',
              body:
                  canCreateGuardianLink
                      ? 'Mantenha responsáveis ligados aos passageiros acompanhados.'
                      : 'Cadastre passageiro e responsável antes de criar vínculo familiar.',
              buttonLabel: 'Vincular responsável',
              icon: Icons.family_restroom_rounded,
              onPressed: canCreateGuardianLink ? onCreateGuardianLink : null,
            );

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Fila de onboarding',
                            style: TextStyle(
                              color: AdminPortalColors.text,
                              fontSize: 26,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          SizedBox(height: 8),
                          Text(
                            'Resolva primeiro o que libera acesso real para passageiros e responsáveis.',
                            style: TextStyle(
                              color: AdminPortalColors.textMuted,
                              fontSize: 15,
                              height: 1.45,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 16),
                    AdminPortalTag(
                      label:
                          hasPendingRequest
                              ? '$pendingRequests pendente(s)'
                              : 'Sem pendências',
                      background:
                          hasPendingRequest
                              ? AdminPortalColors.orangeSoft
                              : AdminPortalColors.greenSoft,
                      foreground:
                          hasPendingRequest
                              ? AdminPortalColors.orange
                              : AdminPortalColors.green,
                    ),
                  ],
                ),
                const SizedBox(height: 22),
                if (compact)
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      primaryCard,
                      const SizedBox(height: 14),
                      inviteCard,
                      const SizedBox(height: 14),
                      guardianCard,
                    ],
                  )
                else
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(flex: 3, child: primaryCard),
                      const SizedBox(width: 14),
                      Expanded(flex: 2, child: inviteCard),
                      const SizedBox(width: 14),
                      Expanded(flex: 2, child: guardianCard),
                    ],
                  ),
                const SizedBox(height: 20),
                const Text(
                  'Contadores operacionais',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    _TaskQueueMetricPill(
                      label: _requestMetricLabel(pendingRequests),
                      icon: Icons.mark_email_unread_outlined,
                    ),
                    _TaskQueueMetricPill(
                      label: '$activeInvites convites ativos',
                      icon: Icons.key_rounded,
                    ),
                    _TaskQueueMetricPill(
                      label: _guardianLinksTitle(guardianLinks),
                      icon: Icons.family_restroom_rounded,
                    ),
                  ],
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _TaskQueuePrimaryCard extends StatelessWidget {
  const _TaskQueuePrimaryCard({
    required this.pendingRequests,
    required this.firstRequestName,
    required this.onPressed,
  });

  final int pendingRequests;
  final String? firstRequestName;
  final Future<void> Function() onPressed;

  @override
  Widget build(BuildContext context) {
    final hasPendingRequest = pendingRequests > 0;
    final title =
        hasPendingRequest
            ? 'Revisar $pendingRequests ${pendingRequests == 1 ? 'solicitação pendente' : 'solicitações pendentes'}'
            : 'Onboarding em dia';
    final body =
        hasPendingRequest
            ? '${firstRequestName ?? 'Um passageiro'} aguarda decisão para entrar na operação.'
            : 'Nenhuma solicitação pendente agora.';
    final buttonLabel =
        hasPendingRequest ? 'Revisar solicitação' : 'Criar primeiro convite';

    return _TaskQueueCardFrame(
      icon:
          hasPendingRequest
              ? Icons.assignment_turned_in_outlined
              : Icons.check_circle_outline_rounded,
      iconColor:
          hasPendingRequest
              ? AdminPortalColors.orange
              : AdminPortalColors.green,
      eyebrow: 'Próxima tarefa',
      title: title,
      body: body,
      child: AdminPortalPrimaryButton(
        label: buttonLabel,
        icon:
            hasPendingRequest ? Icons.arrow_forward_rounded : Icons.key_rounded,
        onPressed: () => onPressed(),
      ),
    );
  }
}

class _TaskQueueSecondaryCard extends StatelessWidget {
  const _TaskQueueSecondaryCard({
    required this.title,
    required this.body,
    required this.buttonLabel,
    required this.icon,
    required this.onPressed,
  });

  final String title;
  final String body;
  final String buttonLabel;
  final IconData icon;
  final Future<void> Function()? onPressed;

  @override
  Widget build(BuildContext context) {
    return _TaskQueueCardFrame(
      icon: icon,
      iconColor: AdminPortalColors.blue,
      title: title,
      body: body,
      child: AdminPortalSecondaryButton(
        label: buttonLabel,
        icon: icon,
        onPressed: onPressed == null ? null : () => onPressed!(),
      ),
    );
  }
}

class _TaskQueueCardFrame extends StatelessWidget {
  const _TaskQueueCardFrame({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.body,
    required this.child,
    this.eyebrow,
  });

  final IconData icon;
  final Color iconColor;
  final String? eyebrow;
  final String title;
  final String body;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: AdminPortalColors.canvas,
        borderRadius: BorderRadius.circular(28),
        border: Border.all(
          color: AdminPortalColors.outline.withValues(alpha: 0.7),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: iconColor.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Icon(icon, color: iconColor),
                ),
                if (eyebrow != null) ...[
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      eyebrow!,
                      style: const TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.8,
                      ),
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 14),
            Text(
              title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontSize: 18,
                fontWeight: FontWeight.w900,
                height: 1.18,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              body,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 14,
                height: 1.42,
              ),
            ),
            const SizedBox(height: 16),
            child,
          ],
        ),
      ),
    );
  }
}

class _TaskQueueMetricPill extends StatelessWidget {
  const _TaskQueueMetricPill({required this.label, required this.icon});

  final String label;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: AdminPortalColors.surfaceSoft.withValues(alpha: 0.76),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 16, color: AdminPortalColors.blue),
            const SizedBox(width: 7),
            Flexible(
              child: Text(
                label,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

String _requestMetricLabel(int count) {
  return '$count ${count == 1 ? 'solicitação' : 'solicitações'}';
}

String _guardianLinksTitle(int count) {
  return '$count ${count == 1 ? 'vínculo familiar ativo' : 'vínculos familiares ativos'}';
}
