import 'package:flutter/material.dart';

import '../../../account/presentation/account_role_label.dart';
import '../../domain/admin_portal_session.dart';
import '../admin_login_screen.dart';
import 'admin_portal_colors.dart';
import 'admin_portal_theme.dart';

export 'admin_portal_colors.dart';

part 'admin_portal_sidebar.dart';

enum AdminPortalDestination {
  dashboard,
  motoristas,
  passageiros,
  responsaveis,
  vinculos,
  servicos,
  rotas,
  viagens,
  suporte,
  conta,
}

extension AdminPortalDestinationX on AdminPortalDestination {
  String get label => switch (this) {
    AdminPortalDestination.dashboard => 'Painel',
    AdminPortalDestination.motoristas => 'Motoristas',
    AdminPortalDestination.passageiros => 'Passageiros',
    AdminPortalDestination.responsaveis => 'Responsáveis',
    AdminPortalDestination.vinculos => 'Onboarding e vínculos',
    AdminPortalDestination.servicos => 'Serviços',
    AdminPortalDestination.rotas => 'Rotas-base',
    AdminPortalDestination.viagens => 'Viagens do dia',
    AdminPortalDestination.suporte => 'Atendimento',
    AdminPortalDestination.conta => 'Conta',
  };
  IconData get icon => switch (this) {
    AdminPortalDestination.dashboard => Icons.dashboard_outlined,
    AdminPortalDestination.motoristas => Icons.directions_bus_outlined,
    AdminPortalDestination.passageiros => Icons.person_outline,
    AdminPortalDestination.responsaveis => Icons.supervisor_account_outlined,
    AdminPortalDestination.vinculos => Icons.task_alt_outlined,
    AdminPortalDestination.servicos => Icons.route_outlined,
    AdminPortalDestination.rotas => Icons.alt_route_outlined,
    AdminPortalDestination.viagens => Icons.departure_board_outlined,
    AdminPortalDestination.suporte => Icons.support_agent_outlined,
    AdminPortalDestination.conta => Icons.admin_panel_settings_outlined,
  };
}

class AdminPortalShell extends StatelessWidget {
  const AdminPortalShell({
    super.key,
    required this.session,
    required this.selectedDestination,
    required this.onDestinationSelected,
    required this.child,
    this.contextHint = 'Operação da empresa',
    this.floatingAction,
    this.onLogout,
  });

  final AdminPortalSession session;
  final AdminPortalDestination selectedDestination;
  final ValueChanged<AdminPortalDestination> onDestinationSelected;
  final Widget child;
  final String contextHint;
  final Widget? floatingAction;
  final VoidCallback? onLogout;

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: adminPortalTheme(Theme.of(context)),
      child: Scaffold(
        backgroundColor: AdminPortalColors.canvas,
        body: SafeArea(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 1180;
              return Stack(
                children: [
                  Row(
                    children: [
                      _AdminPortalSidebar(
                        selectedDestination: selectedDestination,
                        onDestinationSelected: onDestinationSelected,
                        onLogout: onLogout,
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            _AdminPortalTopbar(
                              session: session,
                              contextHint: contextHint,
                            ),
                            Expanded(
                              child: Padding(
                                padding: EdgeInsets.fromLTRB(
                                  compact ? 20 : 24,
                                  compact ? 20 : 24,
                                  compact ? 20 : 32,
                                  24,
                                ),
                                child: child,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  if (floatingAction != null)
                    Positioned(right: 32, bottom: 28, child: floatingAction!),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class AdminPortalPage extends StatelessWidget {
  const AdminPortalPage({
    super.key,
    required this.title,
    required this.description,
    required this.child,
    this.trailing,
    this.breadcrumb,
  });

  final String title;
  final String description;
  final Widget child;
  final Widget? trailing;
  final String? breadcrumb;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (breadcrumb != null) ...[
            Text(
              breadcrumb!,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 10),
          ],
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 30,
                        fontWeight: FontWeight.w800,
                        height: 1.2,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      description,
                      style: const TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 16,
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
              if (trailing != null) ...[const SizedBox(width: 24), trailing!],
            ],
          ),
          const SizedBox(height: 28),
          child,
        ],
      ),
    );
  }
}

class AdminPortalSurface extends StatelessWidget {
  const AdminPortalSurface({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(24),
    this.radius = 32,
    this.color = AdminPortalColors.surface,
  });

  final Widget child;
  final EdgeInsets padding;
  final double radius;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            color.withValues(alpha: 0.95),
            color.withValues(alpha: 0.82),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(
          color: AdminPortalColors.glassHighlight.withValues(alpha: 0.72),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66000000),
            blurRadius: 36,
            offset: Offset(0, 18),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(radius),
        child: Padding(padding: padding, child: child),
      ),
    );
  }
}

class AdminPortalPrimaryButton extends StatelessWidget {
  const AdminPortalPrimaryButton({
    super.key,
    required this.label,
    this.icon,
    this.onPressed,
  });

  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return FilledButton.icon(
      onPressed: onPressed,
      icon: icon == null ? const SizedBox.shrink() : Icon(icon, size: 18),
      label: Text(label),
      style: FilledButton.styleFrom(
        backgroundColor: AdminPortalColors.blueBright,
        foregroundColor: AdminPortalColors.canvas,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
        elevation: 6,
        shadowColor: const Color(0x6638BDF8),
      ),
    );
  }
}

class AdminPortalSecondaryButton extends StatelessWidget {
  const AdminPortalSecondaryButton({
    super.key,
    required this.label,
    this.icon,
    this.onPressed,
  });

  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onPressed,
      icon: icon == null ? const SizedBox.shrink() : Icon(icon, size: 18),
      label: Text(label),
      style: OutlinedButton.styleFrom(
        foregroundColor: AdminPortalColors.text,
        side: const BorderSide(color: AdminPortalColors.outline),
        backgroundColor: AdminPortalColors.surfaceSoft.withValues(alpha: 0.74),
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
      ),
    );
  }
}

class AdminPortalTag extends StatelessWidget {
  const AdminPortalTag({
    super.key,
    required this.label,
    this.background = AdminPortalColors.surfaceSoft,
    this.foreground = AdminPortalColors.textMuted,
  });

  final String label;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: foreground,
          fontSize: 11,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class AdminPortalMetricCard extends StatelessWidget {
  const AdminPortalMetricCard({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    this.trailingTag,
    this.subtitle,
    this.iconBackground = AdminPortalColors.blueSoft,
  });

  final String label;
  final String value;
  final IconData icon;
  final Widget? trailingTag;
  final String? subtitle;
  final Color iconBackground;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      padding: const EdgeInsets.all(24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 220),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: iconBackground,
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Icon(icon, color: AdminPortalColors.blue),
                ),
                if (trailingTag != null) ...[
                  const SizedBox(width: 12),
                  Expanded(
                    child: Align(
                      alignment: Alignment.topRight,
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        child: trailingTag!,
                      ),
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 18),
            Text(
              label.toUpperCase(),
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                letterSpacing: 0,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              value,
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontSize: 30,
                fontWeight: FontWeight.w800,
                height: 1.2,
              ),
            ),
            if (subtitle != null) ...[
              const SizedBox(height: 8),
              Text(
                subtitle!,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 12,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class AdminPortalEmptyMetric extends StatelessWidget {
  const AdminPortalEmptyMetric(this.label, {super.key});

  final String label;

  @override
  Widget build(BuildContext context) {
    return const Text(
      '—',
      style: TextStyle(
        color: AdminPortalColors.text,
        fontSize: 30,
        fontWeight: FontWeight.w800,
      ),
    );
  }
}

class AdminPortalFloatingAction extends StatelessWidget {
  const AdminPortalFloatingAction({
    super.key,
    this.onPressed,
    this.icon = Icons.auto_awesome,
  });

  final VoidCallback? onPressed;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AdminPortalColors.surface.withValues(alpha: 0.94),
      elevation: 12,
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(999),
        child: SizedBox(
          width: 64,
          height: 64,
          child: Icon(icon, color: AdminPortalColors.blueBright),
        ),
      ),
    );
  }
}

class _AdminPortalTopbar extends StatelessWidget {
  const _AdminPortalTopbar({required this.session, required this.contextHint});

  final AdminPortalSession session;
  final String contextHint;

  @override
  Widget build(BuildContext context) {
    final company =
        session.companyName.trim().isNotEmpty
            ? session.companyName.trim()
            : session.companyDisplayName;
    final adminLabel = presentAccountRoleLabel(
      tipoUsuario: session.backendRoleLabel,
      fallback: 'Administrador',
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < 900,
            veryNarrow = constraints.maxWidth < 760;

        return Container(
          height: 64,
          padding: EdgeInsets.symmetric(horizontal: veryNarrow ? 16 : 32),
          decoration: BoxDecoration(
            color: AdminPortalColors.canvas.withValues(alpha: 0.78),
            border: const Border(
              bottom: BorderSide(color: AdminPortalColors.divider),
            ),
          ),
          child: Row(
            children: [
              Expanded(
                child: ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: narrow ? 360 : 420),
                  child: Container(
                    height: 38,
                    decoration: BoxDecoration(
                      color: AdminPortalColors.surfaceSoft.withValues(
                        alpha: 0.58,
                      ),
                      borderRadius: BorderRadius.circular(999),
                      border: Border.all(
                        color: AdminPortalColors.glassHighlight.withValues(
                          alpha: 0.72,
                        ),
                      ),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.auto_awesome_mosaic_outlined,
                          size: 18,
                          color: AdminPortalColors.blue,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            contextHint,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: AdminPortalColors.textMuted,
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 16),
              if (!veryNarrow)
                Container(
                  width: 1,
                  height: 24,
                  color: AdminPortalColors.outline.withValues(alpha: 0.4),
                ),
              if (!veryNarrow) const SizedBox(width: 16),
              if (!narrow)
                Flexible(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        company,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AdminPortalColors.text,
                          fontSize: 14,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      Text(
                        adminLabel.toUpperCase(),
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 10,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0,
                        ),
                      ),
                    ],
                  ),
                ),
              if (!narrow) const SizedBox(width: 12),
              CircleAvatar(
                radius: 20,
                backgroundColor: AdminPortalColors.blueSoft,
                child: Text(
                  _initials(session.adminName),
                  style: const TextStyle(
                    color: AdminPortalColors.blueBright,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  static String _initials(String value) {
    final parts = value
        .trim()
        .split(RegExp(r'\s+'))
        .where((part) => part.isNotEmpty)
        .toList(growable: false);
    if (parts.isEmpty) {
      return 'AD';
    }
    if (parts.length == 1) return parts.first.substring(0, 1).toUpperCase();
    return (parts.first.substring(0, 1) + parts.last.substring(0, 1))
        .toUpperCase();
  }
}
