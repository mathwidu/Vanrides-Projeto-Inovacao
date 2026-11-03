import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverHomeNextActionPanel extends StatelessWidget {
  const DriverHomeNextActionPanel({
    super.key,
    required this.title,
    required this.description,
    required this.primaryKey,
    required this.primaryLabel,
    required this.primaryIcon,
    required this.onPrimary,
    this.primaryBackgroundColor,
  });

  final String title;
  final String description;
  final Key primaryKey;
  final String primaryLabel;
  final IconData primaryIcon;
  final VoidCallback? onPrimary;
  final Color? primaryBackgroundColor;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      label: '${AppStrings.motorista.proximaAcao}: $title',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppStrings.motorista.proximaAcao,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: VanRidesMapTokens.blueTracking,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: VanRidesMapTokens.textPrimary,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            description,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: VanRidesMapTokens.textSecondary,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 12),
          MapFirstPrimaryButton(
            buttonKey: primaryKey,
            label: primaryLabel,
            icon: primaryIcon,
            onPressed: onPrimary,
            backgroundColor: primaryBackgroundColor ?? MapFirstPalette.green,
          ),
        ],
      ),
    );
  }
}

class DriverHomeSecondaryActionGrid extends StatelessWidget {
  const DriverHomeSecondaryActionGrid({super.key, required this.actions});

  final List<DriverHomeSecondaryAction> actions;

  @override
  Widget build(BuildContext context) {
    if (actions.isEmpty) {
      return const SizedBox.shrink();
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final itemWidth = (constraints.maxWidth - 10) / 2;
        return Wrap(
          spacing: 10,
          runSpacing: 10,
          children: actions
              .map(
                (action) => SizedBox(
                  width: itemWidth,
                  child: MapFirstSecondaryButton(
                    buttonKey: action.key,
                    icon: action.icon,
                    label: action.label,
                    onPressed: action.onPressed,
                    color: action.color ?? MapFirstPalette.textStrong,
                  ),
                ),
              )
              .toList(growable: false),
        );
      },
    );
  }
}

class DriverHomeSecondaryAction {
  const DriverHomeSecondaryAction({
    required this.key,
    required this.icon,
    required this.label,
    required this.onPressed,
    this.color,
  });

  final Key key;
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;
  final Color? color;
}
