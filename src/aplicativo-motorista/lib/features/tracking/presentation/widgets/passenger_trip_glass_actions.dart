import 'package:flutter/material.dart';

import 'vanrides_map_tokens.dart';

class PassengerTripGlassActions extends StatelessWidget {
  const PassengerTripGlassActions({
    super.key,
    required this.detailsButtonKey,
    required this.presenceButtonKey,
    required this.onRefresh,
    required this.onOpenTripDetails,
    required this.onOpenMap,
    required this.onOpenPresence,
  });

  final Key? detailsButtonKey;
  final Key? presenceButtonKey;
  final VoidCallback onRefresh;
  final VoidCallback onOpenTripDetails;
  final VoidCallback onOpenMap;
  final VoidCallback onOpenPresence;

  @override
  Widget build(BuildContext context) {
    return _GlassActionButton(
      buttonKey: detailsButtonKey,
      icon: Icons.receipt_long_rounded,
      label: 'Detalhes',
      onPressed: onOpenTripDetails,
    );
  }
}

class _GlassActionButton extends StatelessWidget {
  const _GlassActionButton({
    this.buttonKey,
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  final Key? buttonKey;
  final IconData icon;
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      key: buttonKey,
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        foregroundColor: VanRidesMapTokens.textPrimary,
        backgroundColor: VanRidesMapTokens.surfaceRaised.withValues(
          alpha: 0.68,
        ),
        side: BorderSide(
          color: VanRidesMapTokens.border.withValues(alpha: 0.34),
        ),
        minimumSize: const Size.fromHeight(42),
        padding: const EdgeInsets.symmetric(horizontal: 10),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        textStyle: Theme.of(
          context,
        ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800),
      ),
      icon: Icon(icon, size: 18, color: VanRidesMapTokens.blueTracking),
      label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
  }
}
