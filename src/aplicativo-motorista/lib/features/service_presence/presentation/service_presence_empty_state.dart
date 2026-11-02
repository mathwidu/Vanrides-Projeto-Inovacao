import 'package:flutter/material.dart';

class ServicePresenceEmptyState extends StatelessWidget {
  const ServicePresenceEmptyState({
    super.key,
    required this.title,
    required this.emptyTitle,
    required this.emptyDescription,
    this.actionLabel,
    this.onAction,
  });

  final String title;
  final String emptyTitle;
  final String emptyDescription;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Semantics(
      container: true,
      label: '$title. $emptyTitle. $emptyDescription',
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.36),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: colorScheme.primary.withValues(alpha: 0.2)),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _PresenceEmptyIcon(
                    color: colorScheme.primary,
                    icon: Icons.event_available_outlined,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: Theme.of(
                            context,
                          ).textTheme.titleLarge?.copyWith(
                            color: colorScheme.onSurface,
                            fontSize: 24,
                            height: 1.08,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          emptyTitle,
                          style: Theme.of(
                            context,
                          ).textTheme.titleSmall?.copyWith(
                            color: colorScheme.onSurface,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                emptyDescription,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: colorScheme.onSurfaceVariant,
                  height: 1.36,
                  fontWeight: FontWeight.w600,
                ),
              ),
              if (actionLabel != null && onAction != null) ...[
                const SizedBox(height: 12),
                TextButton.icon(
                  onPressed: onAction,
                  icon: const Icon(Icons.refresh_rounded),
                  label: Text(actionLabel!),
                  style: TextButton.styleFrom(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    textStyle: Theme.of(context).textTheme.labelLarge?.copyWith(
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _PresenceEmptyIcon extends StatelessWidget {
  const _PresenceEmptyIcon({required this.color, required this.icon});

  final Color color;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 42,
      height: 42,
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(14),
      ),
      alignment: Alignment.center,
      child: Icon(icon, color: color, size: 22),
    );
  }
}
