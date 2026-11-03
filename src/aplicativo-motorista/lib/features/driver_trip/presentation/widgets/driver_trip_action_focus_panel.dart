import 'package:flutter/material.dart';

import '../driver_trip_action_focus_presenter.dart';
import 'driver_premium_chip.dart';
import '../../../tracking/presentation/map_first_v2_components.dart';
import '../../../tracking/presentation/widgets/vanrides_map_tokens.dart';

class DriverTripActionFocusPanel extends StatelessWidget {
  const DriverTripActionFocusPanel({super.key, required this.display});

  final DriverTripActionFocusPresentation display;

  @override
  Widget build(BuildContext context) {
    final tone = _toneStyle(display.tone);

    return Semantics(
      container: true,
      label:
          '${display.title}. ${display.summary}. '
          '${display.routeLabel}. ${display.signalLabel}. ${display.helper}',
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: tone.color.withValues(alpha: 0.10),
          border: Border.all(color: tone.color.withValues(alpha: 0.28)),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 11, 12, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(tone.icon, color: tone.color, size: 19),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      display.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelLarge?.copyWith(
                        color: VanRidesMapTokens.textPrimary,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                display.summary,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: VanRidesMapTokens.textSecondary,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 9),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  DriverPremiumChip(
                    icon: Icons.alt_route_rounded,
                    label: display.routeLabel,
                    color: tone.color,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DriverTripActionFocusToneStyle {
  const _DriverTripActionFocusToneStyle({
    required this.icon,
    required this.color,
  });

  final IconData icon;
  final Color color;
}

_DriverTripActionFocusToneStyle _toneStyle(DriverTripActionFocusTone tone) {
  return switch (tone) {
    DriverTripActionFocusTone.ready => const _DriverTripActionFocusToneStyle(
      icon: Icons.near_me_outlined,
      color: MapFirstPalette.blue,
    ),
    DriverTripActionFocusTone.review => const _DriverTripActionFocusToneStyle(
      icon: Icons.fact_check_outlined,
      color: VanRidesMapTokens.greenAction,
    ),
    DriverTripActionFocusTone.degraded => const _DriverTripActionFocusToneStyle(
      icon: Icons.warning_amber_rounded,
      color: VanRidesMapTokens.amberWarning,
    ),
  };
}
