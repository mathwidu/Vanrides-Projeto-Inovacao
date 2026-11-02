import 'package:flutter/material.dart';

import '../theme/vanrides_design_tokens.dart';
import 'vanrides_liquid_surface.dart';

enum VanRidesStatusTone { tracking, success, warning, danger, neutral }

class VanRidesStatusPill extends StatelessWidget {
  const VanRidesStatusPill({
    super.key,
    required this.label,
    required this.icon,
    this.tone = VanRidesStatusTone.neutral,
    this.semanticLabel,
  });

  final String label;
  final IconData icon;
  final VanRidesStatusTone tone;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final accent = _accentColor(tone);
    final content = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: accent),
        const SizedBox(width: 8),
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: VanRidesDesignTokens.textPrimary,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ],
    );

    return VanRidesLiquidSurface.action(
      semanticLabel: semanticLabel ?? label,
      child: content,
    );
  }

  Color _accentColor(VanRidesStatusTone tone) {
    return switch (tone) {
      VanRidesStatusTone.tracking => VanRidesDesignTokens.blueTracking,
      VanRidesStatusTone.success => VanRidesDesignTokens.greenAction,
      VanRidesStatusTone.warning => VanRidesDesignTokens.amberWarning,
      VanRidesStatusTone.danger => VanRidesDesignTokens.redDanger,
      VanRidesStatusTone.neutral => VanRidesDesignTokens.textSecondary,
    };
  }
}
