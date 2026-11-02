import 'package:flutter/material.dart';

import 'admin_portal_layout.dart';

class AdminPersonNextActionCard extends StatelessWidget {
  const AdminPersonNextActionCard({
    super.key,
    required this.actionLabel,
    required this.body,
    this.icon = Icons.bolt_outlined,
    this.accent = AdminPortalColors.blue,
    this.onAction,
  });

  final String actionLabel;
  final String body;
  final IconData icon;
  final Color accent;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.86),
        borderRadius: BorderRadius.circular(32),
        border: Border.all(color: accent.withValues(alpha: 0.42)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: accent, size: 18),
              const SizedBox(width: 8),
              Text(
                'Próxima ação',
                style: TextStyle(
                  color: accent,
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(
            actionLabel,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 18,
              fontWeight: FontWeight.w900,
              height: 1.15,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            body,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 12,
              height: 1.35,
            ),
          ),
          if (onAction != null) ...[
            const SizedBox(height: 14),
            AdminPortalSecondaryButton(
              label: 'Resolver agora',
              icon: Icons.arrow_forward_rounded,
              onPressed: onAction,
            ),
          ],
        ],
      ),
    );
  }
}

class AdminDetailDisclosureSection extends StatefulWidget {
  const AdminDetailDisclosureSection({
    super.key,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.child,
    this.initiallyExpanded = false,
    this.accent = AdminPortalColors.blue,
    this.trailing,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final Widget child;
  final bool initiallyExpanded;
  final Color accent;
  final Widget? trailing;

  @override
  State<AdminDetailDisclosureSection> createState() =>
      _AdminDetailDisclosureSectionState();
}

class _AdminDetailDisclosureSectionState
    extends State<AdminDetailDisclosureSection> {
  late bool _expanded;

  @override
  void initState() {
    super.initState();
    _expanded = widget.initiallyExpanded;
  }

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 32,
      padding: const EdgeInsets.all(20),
      color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.72),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            borderRadius: BorderRadius.circular(22),
            onTap: () {
              setState(() => _expanded = !_expanded);
            },
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: widget.accent.withValues(alpha: 0.18),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AdminPortalColors.outline),
                    ),
                    child: Icon(widget.icon, color: widget.accent),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.title,
                          style: const TextStyle(
                            color: AdminPortalColors.text,
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          widget.subtitle,
                          style: const TextStyle(
                            color: AdminPortalColors.textMuted,
                            fontSize: 13,
                            height: 1.4,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (widget.trailing != null) ...[
                    const SizedBox(width: 12),
                    widget.trailing!,
                  ],
                  const SizedBox(width: 10),
                  Icon(
                    _expanded
                        ? Icons.keyboard_arrow_up_rounded
                        : Icons.keyboard_arrow_down_rounded,
                    color: AdminPortalColors.textMuted,
                  ),
                ],
              ),
            ),
          ),
          AnimatedSize(
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            alignment: Alignment.topCenter,
            child:
                _expanded
                    ? Padding(
                      padding: const EdgeInsets.only(top: 18),
                      child: widget.child,
                    )
                    : const SizedBox(width: double.infinity),
          ),
        ],
      ),
    );
  }
}
