import 'package:flutter/material.dart';

import '../../../../l10n/app_strings.dart';
import '../../../../app/widgets/vanrides_liquid_surface.dart';
import '../../../service_presence/domain/passenger_available_service_record.dart';
import '../../../service_presence/presentation/presence_today_list.dart';
import '../../domain/trip_tracking_record.dart';
import 'passenger_trip_option_helpers.dart';
import 'vanrides_map_tokens.dart';

class PassengerHomePresenceTab extends StatelessWidget {
  const PassengerHomePresenceTab({
    super.key,
    required this.trip,
    required this.dayTrips,
    required this.presenceServices,
    required this.loadingPresenceServices,
    required this.processingPresenceService,
    required this.presenceErrorMessage,
    required this.onTripSelected,
    required this.onConfirmService,
    required this.onAbsentService,
    required this.onClearService,
    required this.onOpenPresence,
    required this.onOpenTripDetails,
    this.agendaKey,
    this.tripKeyBuilder,
  });

  final TripTrackingRecord? trip;
  final List<TripTrackingRecord> dayTrips;
  final List<PassengerAvailableServiceRecord> presenceServices;
  final bool loadingPresenceServices;
  final bool processingPresenceService;
  final String? presenceErrorMessage;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final ValueChanged<PassengerAvailableServiceRecord> onConfirmService;
  final ValueChanged<PassengerAvailableServiceRecord> onAbsentService;
  final ValueChanged<PassengerAvailableServiceRecord> onClearService;
  final VoidCallback onOpenPresence;
  final VoidCallback onOpenTripDetails;
  final Key? agendaKey;
  final PassengerTripKeyBuilder? tripKeyBuilder;

  static const backdropKey = Key('passenger.presence.backdrop');
  static const heroGlassKey = Key('passenger.presence.heroGlass');

  @override
  Widget build(BuildContext context) {
    return _PresenceMapTintBackdrop(
      key: backdropKey,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(18, 20, 18, 124),
        children: [
          if (loadingPresenceServices)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (presenceServices.isNotEmpty)
            PresenceTodayList(
              services: presenceServices,
              processing: processingPresenceService,
              onConfirm: onConfirmService,
              onAbsent: onAbsentService,
              onClear: onClearService,
            )
          else
            VanRidesLiquidSurface.sheet(
              key: heroGlassKey,
              semanticLabel: 'Presença de hoje sem horários disponíveis',
              borderRadius: 30,
              padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Presença de hoje',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: VanRidesMapTokens.textPrimary,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    presenceErrorMessage ??
                        'Nenhum horário precisa de decisão agora.',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: VanRidesMapTokens.textSecondary,
                      height: 1.38,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Abra presença para revisar os horários do dia.',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: VanRidesMapTokens.textSecondary,
                      height: 1.34,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: onOpenPresence,
                      icon: const Icon(Icons.checklist_rounded),
                      label: Text(AppStrings.passageiro.abrirPresenca),
                      style: FilledButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                        backgroundColor: VanRidesMapTokens.blueTracking,
                        foregroundColor: Colors.black,
                        textStyle: Theme.of(context).textTheme.labelLarge
                            ?.copyWith(fontWeight: FontWeight.w900),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(18),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _PresenceMapTintBackdrop extends StatelessWidget {
  const _PresenceMapTintBackdrop({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: VanRidesMapTokens.mapBase,
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            VanRidesMapTokens.mapBase.withValues(alpha: 0.84),
            VanRidesMapTokens.surface.withValues(alpha: 0.68),
            VanRidesMapTokens.backgroundDeep.withValues(alpha: 0.78),
          ],
          stops: const [0, 0.48, 1],
        ),
      ),
      child: child,
    );
  }
}
