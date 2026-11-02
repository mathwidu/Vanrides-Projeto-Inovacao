import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_status_pill.dart';

import '../data/noop_push_notification_gateway.dart';
import '../data/push_notification_gateway.dart';
import '../domain/push_notification_action_plan.dart';
import '../domain/push_notification_models.dart';

class PushNotificationPreferencesCard extends StatefulWidget {
  const PushNotificationPreferencesCard({super.key, this.gateway});

  static const cardKey = Key('notifications.preferences.card');
  static const statusKey = Key('notifications.preferences.status');

  final PushNotificationGateway? gateway;

  @override
  State<PushNotificationPreferencesCard> createState() =>
      _PushNotificationPreferencesCardState();
}

class _PushNotificationPreferencesCardState
    extends State<PushNotificationPreferencesCard> {
  PushPermissionStatus? _status;
  bool _loading = true;

  PushNotificationGateway get _gateway =>
      widget.gateway ?? NoopPushNotificationGateway();

  @override
  void initState() {
    super.initState();
    _loadStatus();
  }

  @override
  void didUpdateWidget(PushNotificationPreferencesCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.gateway != widget.gateway) {
      _loadStatus();
    }
  }

  Future<void> _loadStatus() async {
    setState(() {
      _loading = true;
      _status = null;
    });
    try {
      final status = await _gateway.currentPermissionStatus();
      if (mounted) {
        setState(() {
          _status = status;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _status = PushPermissionStatus.unavailable;
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = _status ?? PushPermissionStatus.unavailable;
    final state = _presentationFor(status);

    return VanRidesLiquidSurface.card(
      key: PushNotificationPreferencesCard.cardKey,
      semanticLabel: 'Preferências de notificações',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          LayoutBuilder(
            builder: (context, constraints) {
              final statusIndicator =
                  _loading
                      ? const SizedBox.square(
                        dimension: 22,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                      : VanRidesStatusPill(
                        key: PushNotificationPreferencesCard.statusKey,
                        label: state.label,
                        icon: state.icon,
                        tone: state.tone,
                      );
              final titleBlock = _NotificationTitleBlock(
                description: state.description,
              );
              final compact = constraints.maxWidth < 360;

              if (compact) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const _NotificationIcon(),
                        const SizedBox(width: 12),
                        Expanded(child: titleBlock),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Padding(
                      padding: const EdgeInsets.only(left: 56),
                      child: statusIndicator,
                    ),
                  ],
                );
              }

              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _NotificationIcon(),
                  const SizedBox(width: 12),
                  Expanded(child: titleBlock),
                  const SizedBox(width: 10),
                  statusIndicator,
                ],
              );
            },
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children:
                _notificationCategories
                    .map(
                      (category) => _NotificationCategoryChip(
                        category: category,
                        enabled: state.categoriesEnabled,
                      ),
                    )
                    .toList(),
          ),
          const SizedBox(height: 18),
          const _NotificationActionPlanSection(),
          if (status == PushPermissionStatus.denied) ...[
            const SizedBox(height: 14),
            Text(
              'Para receber avisos no aparelho, ative as notificações nos Ajustes do sistema.',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: VanRidesDesignTokens.amberWarning,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ],
      ),
    );
  }

  _NotificationPermissionPresentation _presentationFor(
    PushPermissionStatus status,
  ) {
    return switch (status) {
      PushPermissionStatus.granted => const _NotificationPermissionPresentation(
        label: 'Permitidas',
        description:
            'Você pode receber avisos operacionais quando a empresa ativar essa entrega.',
        icon: Icons.notifications_active_outlined,
        tone: VanRidesStatusTone.success,
        categoriesEnabled: true,
      ),
      PushPermissionStatus.provisional =>
        const _NotificationPermissionPresentation(
          label: 'Provisórias',
          description:
              'O aparelho aceita avisos silenciosos. Você poderá ajustar categorias depois.',
          icon: Icons.notifications_paused_outlined,
          tone: VanRidesStatusTone.tracking,
          categoriesEnabled: true,
        ),
      PushPermissionStatus.denied => const _NotificationPermissionPresentation(
        label: 'Negadas',
        description:
            'A permissão está negada no aparelho. O app não insistirá com novos prompts.',
        icon: Icons.notifications_off_outlined,
        tone: VanRidesStatusTone.warning,
        categoriesEnabled: false,
      ),
      PushPermissionStatus.notDetermined =>
        const _NotificationPermissionPresentation(
          label: 'A definir',
          description:
              'O app ainda não pediu permissão. O pedido deve acontecer só quando você ativar avisos.',
          icon: Icons.notification_add_outlined,
          tone: VanRidesStatusTone.neutral,
          categoriesEnabled: false,
        ),
      PushPermissionStatus.unavailable =>
        const _NotificationPermissionPresentation(
          label: 'Preparando',
          description:
              'A entrega real de avisos ainda está sendo preparada. Nada será pedido antes disso.',
          icon: Icons.construction_rounded,
          tone: VanRidesStatusTone.neutral,
          categoriesEnabled: false,
        ),
    };
  }
}

class _NotificationTitleBlock extends StatelessWidget {
  const _NotificationTitleBlock({required this.description});

  final String description;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Notificações',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: VanRidesDesignTokens.textPrimary,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          description,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: VanRidesDesignTokens.textSecondary,
            fontWeight: FontWeight.w700,
            height: 1.25,
          ),
        ),
      ],
    );
  }
}

class _NotificationPermissionPresentation {
  const _NotificationPermissionPresentation({
    required this.label,
    required this.description,
    required this.icon,
    required this.tone,
    required this.categoriesEnabled,
  });

  final String label;
  final String description;
  final IconData icon;
  final VanRidesStatusTone tone;
  final bool categoriesEnabled;
}

class _NotificationCategory {
  const _NotificationCategory({
    required this.title,
    required this.description,
    required this.icon,
  });

  final String title;
  final String description;
  final IconData icon;
}

const _notificationCategories = [
  _NotificationCategory(
    title: 'Viagem',
    description: 'início, fim e cancelamento',
    icon: Icons.route_outlined,
  ),
  _NotificationCategory(
    title: 'Presença',
    description: 'decisões e lembretes',
    icon: Icons.fact_check_outlined,
  ),
  _NotificationCategory(
    title: 'Mensagens',
    description: 'tickets e respostas',
    icon: Icons.mark_chat_unread_outlined,
  ),
  _NotificationCategory(
    title: 'Operação',
    description: 'mudanças importantes',
    icon: Icons.campaign_outlined,
  ),
];

class _NotificationActionPlanSection extends StatelessWidget {
  const _NotificationActionPlanSection();

  @override
  Widget build(BuildContext context) {
    final notifications = PushNotificationActionPlan.notifications;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Avisos planejados',
          style: Theme.of(context).textTheme.labelLarge?.copyWith(
            color: VanRidesDesignTokens.textPrimary,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 8),
        for (var index = 0; index < notifications.length; index++) ...[
          _NotificationActionPlanRow(notification: notifications[index]),
          if (index != notifications.length - 1)
            Divider(
              height: 18,
              color: VanRidesDesignTokens.borderSubtle.withValues(alpha: 0.7),
            ),
        ],
      ],
    );
  }
}

class _NotificationActionPlanRow extends StatelessWidget {
  const _NotificationActionPlanRow({required this.notification});

  final PlannedPushNotification notification;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context).textTheme;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _NotificationRoleIcon(role: notification.role),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 8,
                runSpacing: 6,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  _NotificationInlinePill(
                    label: notification.role.label,
                    muted: false,
                  ),
                  _NotificationInlinePill(
                    label: notification.category.label,
                    muted: true,
                  ),
                  _NotificationInlinePill(
                    label: notification.essential ? 'Essencial' : 'Opcional',
                    muted: !notification.essential,
                  ),
                ],
              ),
              const SizedBox(height: 7),
              Text(
                notification.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.bodyMedium?.copyWith(
                  color: VanRidesDesignTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                  height: 1.18,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                notification.body,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.bodySmall?.copyWith(
                  color: VanRidesDesignTokens.textSecondary,
                  fontWeight: FontWeight.w700,
                  height: 1.24,
                ),
              ),
              const SizedBox(height: 7),
              Wrap(
                spacing: 8,
                runSpacing: 6,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  _NotificationActionLabel(label: notification.actionLabel),
                  Text(
                    notification.destinationLabel,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.labelSmall?.copyWith(
                      color: VanRidesDesignTokens.textMuted,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _NotificationRoleIcon extends StatelessWidget {
  const _NotificationRoleIcon({required this.role});

  final PushNotificationRole role;

  @override
  Widget build(BuildContext context) {
    final icon = switch (role) {
      PushNotificationRole.passenger => Icons.person_outline_rounded,
      PushNotificationRole.guardian => Icons.supervisor_account_outlined,
      PushNotificationRole.driver => Icons.drive_eta_outlined,
      PushNotificationRole.company => Icons.support_agent_outlined,
    };

    return Container(
      width: 32,
      height: 32,
      decoration: BoxDecoration(
        color: VanRidesDesignTokens.greenAction.withValues(alpha: 0.12),
        shape: BoxShape.circle,
      ),
      alignment: Alignment.center,
      child: Icon(icon, size: 17, color: VanRidesDesignTokens.greenAction),
    );
  }
}

class _NotificationInlinePill extends StatelessWidget {
  const _NotificationInlinePill({required this.label, required this.muted});

  final String label;
  final bool muted;

  @override
  Widget build(BuildContext context) {
    final color =
        muted
            ? VanRidesDesignTokens.textMuted
            : VanRidesDesignTokens.blueTracking;

    return Text(
      label,
      style: Theme.of(context).textTheme.labelSmall?.copyWith(
        color: color,
        fontWeight: FontWeight.w900,
      ),
    );
  }
}

class _NotificationActionLabel extends StatelessWidget {
  const _NotificationActionLabel({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(
          Icons.arrow_forward_rounded,
          size: 14,
          color: VanRidesDesignTokens.blueTracking,
        ),
        const SizedBox(width: 4),
        Text(
          label,
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
            color: VanRidesDesignTokens.blueTracking,
            fontWeight: FontWeight.w900,
          ),
        ),
      ],
    );
  }
}

class _NotificationIcon extends StatelessWidget {
  const _NotificationIcon();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(
        color: VanRidesDesignTokens.blueTracking.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: VanRidesDesignTokens.blueTracking.withValues(alpha: 0.32),
        ),
      ),
      alignment: Alignment.center,
      child: const Icon(
        Icons.notifications_none_rounded,
        color: VanRidesDesignTokens.blueTracking,
        size: 22,
      ),
    );
  }
}

class _NotificationCategoryChip extends StatelessWidget {
  const _NotificationCategoryChip({
    required this.category,
    required this.enabled,
  });

  final _NotificationCategory category;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final accent =
        enabled
            ? VanRidesDesignTokens.blueTracking
            : VanRidesDesignTokens.textMuted;

    return Container(
      constraints: const BoxConstraints(minHeight: 46),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: VanRidesDesignTokens.surfaceRaised.withValues(alpha: 0.54),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: accent.withValues(alpha: 0.28)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(category.icon, color: accent, size: 18),
          const SizedBox(width: 8),
          Flexible(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  category.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: VanRidesDesignTokens.textPrimary,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                Text(
                  category.description,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: VanRidesDesignTokens.textSecondary,
                    fontWeight: FontWeight.w700,
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
