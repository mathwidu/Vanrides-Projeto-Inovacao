part of 'account_profile_screen.dart';

class _AccountProfileCompletionCard extends StatelessWidget {
  const _AccountProfileCompletionCard({
    required this.requiredItems,
    required this.recommendedItems,
    required this.canEdit,
    required this.onEdit,
  });

  final List<String> requiredItems;
  final List<String> recommendedItems;
  final bool canEdit;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 460;
          final content = Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: VanRidesMapTokens.amberWarning.withValues(alpha: 0.16),
                  shape: BoxShape.circle,
                ),
                alignment: Alignment.center,
                child: const Icon(
                  Icons.playlist_add_check_circle_outlined,
                  color: VanRidesMapTokens.amberWarning,
                  size: 20,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      requiredItems.isEmpty
                          ? 'Perfil operacional completo'
                          : 'Perfil operacional incompleto',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      requiredItems.isEmpty
                          ? 'Dados obrigatórios prontos. Complete recomendações para mais confiança.'
                          : 'Corrija os itens obrigatórios para evitar falhas na operação.',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: VanRidesMapTokens.textSecondary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final item in requiredItems)
                          _AccountProfileCompletionChip(
                            label: item,
                            required: true,
                          ),
                        for (final item in recommendedItems)
                          _AccountProfileCompletionChip(label: item),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          );

          if (!canEdit) {
            return content;
          }

          if (compact) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                content,
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  child: TextButton(
                    onPressed: onEdit,
                    child: const Text('Completar cadastro'),
                  ),
                ),
              ],
            );
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: content),
              const SizedBox(width: 10),
              TextButton(
                onPressed: onEdit,
                child: const Text('Completar cadastro'),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _AccountProfileCompletionChip extends StatelessWidget {
  const _AccountProfileCompletionChip({
    required this.label,
    this.required = false,
  });

  final String label;
  final bool required;

  @override
  Widget build(BuildContext context) {
    final color =
        required
            ? VanRidesMapTokens.amberWarning
            : VanRidesMapTokens.blueTracking;
    final background = color.withValues(alpha: required ? 0.18 : 0.14);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.36)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            required ? Icons.priority_high_rounded : Icons.auto_awesome_rounded,
            color: color,
            size: 14,
          ),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: MapFirstPalette.textStrong,
                fontSize: 12,
                fontWeight: FontWeight.w800,
                decoration: TextDecoration.none,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
