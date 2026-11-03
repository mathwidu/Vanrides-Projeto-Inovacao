import 'package:flutter/material.dart';

import '../passenger_trip_readiness_presenter.dart';
import 'vanrides_map_tokens.dart';

class PassengerTripReadinessLine extends StatelessWidget {
  const PassengerTripReadinessLine({super.key, required this.readiness});

  final PassengerTripReadinessPresentation readiness;

  @override
  Widget build(BuildContext context) {
    final tone = _readinessTone(readiness.tone);

    return Semantics(
      label: '${readiness.title}. ${readiness.summary}. ${readiness.helper}',
      container: true,
      child: Container(
        key: const Key('passenger.trip.readinessLine'),
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
        decoration: BoxDecoration(
          color: tone.color.withValues(alpha: 0.10),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: tone.color.withValues(alpha: 0.26)),
        ),
        child: Row(
          children: [
            Icon(tone.icon, color: tone.color, size: 19),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    readiness.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: VanRidesMapTokens.textPrimary,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    readiness.summary,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: VanRidesMapTokens.textSecondary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ReadinessToneStyle {
  const _ReadinessToneStyle({required this.icon, required this.color});

  final IconData icon;
  final Color color;
}

_ReadinessToneStyle _readinessTone(PassengerTripReadinessTone tone) {
  return switch (tone) {
    PassengerTripReadinessTone.reliable => const _ReadinessToneStyle(
      icon: Icons.verified_outlined,
      color: VanRidesMapTokens.greenAction,
    ),
    PassengerTripReadinessTone.observing => const _ReadinessToneStyle(
      icon: Icons.schedule_rounded,
      color: VanRidesMapTokens.amberWarning,
    ),
    PassengerTripReadinessTone.fallback => const _ReadinessToneStyle(
      icon: Icons.signal_wifi_statusbar_connected_no_internet_4_rounded,
      color: VanRidesMapTokens.textSecondary,
    ),
  };
}
