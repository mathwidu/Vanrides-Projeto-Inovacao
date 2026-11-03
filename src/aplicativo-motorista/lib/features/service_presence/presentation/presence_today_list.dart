import 'package:flutter/material.dart';

import '../../../../app/widgets/vanrides_liquid_surface.dart';
import '../../tracking/presentation/widgets/vanrides_map_tokens.dart';
import '../domain/passenger_available_service_record.dart';
import 'service_presence_decision_list.dart';

class PresenceTodayList extends StatelessWidget {
  const PresenceTodayList({
    super.key,
    required this.services,
    required this.processing,
    required this.onConfirm,
    required this.onAbsent,
    required this.onClear,
  });

  static const darkSurfaceKey = ValueKey('presence.today.dark-surface');

  final List<PassengerAvailableServiceRecord> services;
  final bool processing;
  final ValueChanged<PassengerAvailableServiceRecord> onConfirm;
  final ValueChanged<PassengerAvailableServiceRecord> onAbsent;
  final ValueChanged<PassengerAvailableServiceRecord> onClear;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.sheet(
      key: darkSurfaceKey,
      borderRadius: 28,
      padding: const EdgeInsets.all(14),
      child: Theme(
        data: Theme.of(context).copyWith(
          colorScheme: const ColorScheme.dark(
            primary: VanRidesMapTokens.blueTracking,
            surface: VanRidesMapTokens.backgroundDeep,
            surfaceContainerHighest: VanRidesMapTokens.surface,
            surfaceContainerHigh: VanRidesMapTokens.surfaceRaised,
            onSurface: VanRidesMapTokens.textPrimary,
            onSurfaceVariant: VanRidesMapTokens.textSecondary,
          ),
        ),
        child: ServicePresenceDecisionList(
          services: services,
          processing: processing,
          onConfirm: onConfirm,
          onAbsent: onAbsent,
          onClear: onClear,
          title: 'Presença de hoje',
        ),
      ),
    );
  }
}
