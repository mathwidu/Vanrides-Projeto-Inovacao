part of 'guardian_localizacao_widgets.dart';

class GuardianLocalizacaoMessageCard extends StatelessWidget {
  const GuardianLocalizacaoMessageCard({
    super.key,
    required this.icon,
    required this.color,
    required this.title,
    required this.description,
    required this.actionLabel,
    required this.onAction,
  });

  final IconData icon;
  final Color color;
  final String title;
  final String description;
  final String actionLabel;
  final Future<void> Function() onAction;

  @override
  Widget build(BuildContext context) {
    return MapFirstSurfaceCard(
      padding: const EdgeInsets.all(24),
      color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.86),
      child: Column(
        children: [
          Icon(icon, size: 42, color: color),
          const SizedBox(height: 12),
          Text(
            title,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            description,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.35,
            ),
          ),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: onAction,
            icon: const Icon(Icons.refresh),
            label: Text(actionLabel),
          ),
        ],
      ),
    );
  }
}
