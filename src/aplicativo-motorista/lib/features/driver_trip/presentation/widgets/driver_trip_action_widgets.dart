import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_base_widgets.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverTripDetailActionKeys {
  const DriverTripDetailActionKeys({
    required this.maps,
    required this.presence,
    required this.editRoute,
    required this.skipStop,
    required this.noShowStop,
    required this.endTrip,
  });

  final Key maps;
  final Key presence;
  final Key editRoute;
  final Key skipStop;
  final Key noShowStop;
  final Key endTrip;
}

class DriverDetailActionSection extends StatelessWidget {
  const DriverDetailActionSection({
    super.key,
    required this.actionKeys,
    required this.planningMode,
    required this.canOpenMaps,
    required this.openingNavigation,
    required this.runningLifecycleAction,
    required this.hasNextStop,
    required this.onOpenMaps,
    required this.onOpenPresence,
    required this.onEditRoute,
    required this.onSkipStop,
    required this.onNoShowStop,
    required this.onEndTrip,
  });

  final DriverTripDetailActionKeys actionKeys;
  final bool planningMode;
  final bool canOpenMaps;
  final bool openingNavigation;
  final bool runningLifecycleAction;
  final bool hasNextStop;
  final VoidCallback? onOpenMaps;
  final VoidCallback? onOpenPresence;
  final VoidCallback? onEditRoute;
  final VoidCallback? onSkipStop;
  final VoidCallback? onNoShowStop;
  final VoidCallback? onEndTrip;

  @override
  Widget build(BuildContext context) {
    final actions =
        planningMode
            ? <DriverUtilityAction>[
              DriverUtilityAction(
                key: actionKeys.maps,
                icon: Icons.near_me_outlined,
                label: AppStrings.motorista.maps,
                onPressed:
                    canOpenMaps && !openingNavigation ? onOpenMaps : null,
              ),
              DriverUtilityAction(
                key: actionKeys.presence,
                icon: Icons.people_outline,
                label: AppStrings.motorista.presencas,
                onPressed: onOpenPresence,
              ),
              DriverUtilityAction(
                key: actionKeys.editRoute,
                icon: Icons.edit_outlined,
                label: AppStrings.motorista.editarRota,
                onPressed: onEditRoute,
              ),
            ]
            : <DriverUtilityAction>[
              DriverUtilityAction(
                key: actionKeys.maps,
                icon: Icons.near_me_outlined,
                label: AppStrings.motorista.maps,
                onPressed:
                    canOpenMaps && !openingNavigation ? onOpenMaps : null,
              ),
              DriverUtilityAction(
                key: actionKeys.skipStop,
                icon: Icons.skip_next_rounded,
                label: AppStrings.motorista.pularParada,
                onPressed:
                    runningLifecycleAction || !hasNextStop ? null : onSkipStop,
              ),
              DriverUtilityAction(
                key: actionKeys.noShowStop,
                icon: Icons.person_off_outlined,
                label: AppStrings.motorista.marcarNaoEmbarcou,
                onPressed:
                    runningLifecycleAction || !hasNextStop
                        ? null
                        : onNoShowStop,
              ),
              DriverUtilityAction(
                key: actionKeys.endTrip,
                icon: Icons.flag_outlined,
                label: AppStrings.motorista.encerrar,
                onPressed: runningLifecycleAction ? null : onEndTrip,
              ),
            ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppStrings.motorista.acoesDaCorrida,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: const Color(0xFF161B36),
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 12),
        for (var index = 0; index < actions.length; index++) ...[
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              key: actions[index].key,
              onPressed: actions[index].onPressed,
              icon: Icon(actions[index].icon, size: 18),
              label: Text(actions[index].label),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 14,
                ),
                foregroundColor: const Color(0xFF161B36),
                side: const BorderSide(color: Color(0xFFD7DCE8), width: 1.2),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(18),
                ),
                backgroundColor: Colors.white,
                alignment: Alignment.centerLeft,
              ),
            ),
          ),
          if (index != actions.length - 1) const SizedBox(height: 10),
        ],
      ],
    );
  }
}
