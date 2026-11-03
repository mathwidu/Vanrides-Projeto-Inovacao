import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

import '../../domain/trip_tracking_record.dart';
import '../../domain/trip_tracking_state_record.dart';
import '../trip_offline_continuity_presenter.dart';
import '../trip_tracking_presenter.dart';
import 'compact_trip_switcher.dart';
import 'passenger_trip_glass_actions.dart';
import 'vanrides_glass_surface.dart';
import 'vanrides_map_tokens.dart';

class PassengerTripGlassSheet extends StatelessWidget {
  const PassengerTripGlassSheet({
    super.key,
    required this.trip,
    required this.dayTrips,
    required this.selectedTripId,
    required this.onTripSelected,
    required this.onRefresh,
    required this.onOpenTripDetails,
    required this.onOpenMap,
    required this.onOpenPresence,
    this.onToggleHud,
    this.hudToggleKey,
    this.detailsButtonKey,
    this.presenceButtonKey,
    this.agendaKey,
    this.tripKeyBuilder,
  });

  final TripTrackingRecord trip;
  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final VoidCallback onRefresh;
  final VoidCallback onOpenTripDetails;
  final VoidCallback onOpenMap;
  final VoidCallback onOpenPresence;
  final VoidCallback? onToggleHud;
  final Key? hudToggleKey;
  final Key? detailsButtonKey;
  final Key? presenceButtonKey;
  final Key? agendaKey;
  final CompactTripKeyBuilder? tripKeyBuilder;

  @override
  Widget build(BuildContext context) {
    final display = PassengerTripGlassDisplay.from(trip);

    return VanRidesGlassSurface.sheet(
      semanticLabel: 'Resumo da viagem',
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _TripStatusBlock(
            display: display,
            onToggleHud: onToggleHud,
            hudToggleKey: hudToggleKey,
          ),
          const SizedBox(height: 8),
          _PassengerStopLine(display: display),
          const SizedBox(height: 8),
          PassengerTripGlassActions(
            detailsButtonKey: detailsButtonKey,
            presenceButtonKey: presenceButtonKey,
            onRefresh: onRefresh,
            onOpenTripDetails: onOpenTripDetails,
            onOpenMap: onOpenMap,
            onOpenPresence: onOpenPresence,
          ),
        ],
      ),
    );
  }
}

class PassengerTripTopGlassStatus extends StatelessWidget {
  const PassengerTripTopGlassStatus({super.key, required this.trip});

  final TripTrackingRecord trip;

  @override
  Widget build(BuildContext context) {
    final display = PassengerTripGlassDisplay.from(trip);
    return Align(
      alignment: Alignment.centerLeft,
      child: VanRidesGlassSurface.subtle(
        semanticLabel: 'Status da viagem',
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(display.signal.icon, color: display.signal.color, size: 18),
            const SizedBox(width: 8),
            Flexible(
              child: Text(
                display.topStatusLabel,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class PassengerTripGlassDisplay {
  const PassengerTripGlassDisplay({
    required this.primary,
    required this.secondary,
    required this.signal,
    required this.vanDistanceLabel,
    required this.myStopTitle,
    required this.stopHelper,
    required this.serviceName,
    required this.driverName,
    required this.operationLabel,
  });

  final String primary;
  final String secondary;
  final TripTrackingPresentation signal;
  final String vanDistanceLabel;
  final String myStopTitle;
  final String stopHelper;
  final String serviceName;
  final String driverName;
  final String operationLabel;

  String get topStatusLabel => '${signal.label} • $vanDistanceLabel';

  factory PassengerTripGlassDisplay.from(
    TripTrackingRecord trip, {
    TripSignalRole signalRole = TripSignalRole.passenger,
  }) {
    final stopsBefore = resolveStopsBeforePassenger(trip) ?? 0;
    final signal = presentLocationFreshness(
      trip.trackingState.locationFreshness,
      role: signalRole,
    );
    final offlineContinuity = presentTripOfflineContinuity(
      trackingState: trip.trackingState,
      role: signalRole,
    );
    final passengerStop = trip.paradaDoPassageiro;
    final vanDistance = _presentVanDistance(
      stopsBefore: stopsBefore,
      status: trip.status,
      freshness: trip.trackingState.locationFreshness,
      signal: signal,
      offlineContinuity: offlineContinuity,
    );
    final status = _statusCopy(
      trip: trip,
      stopsBefore: stopsBefore,
      signal: signal,
      offlineContinuity: offlineContinuity,
    );

    return PassengerTripGlassDisplay(
      primary: status.primary,
      secondary: status.secondary,
      signal: signal,
      vanDistanceLabel: vanDistance,
      myStopTitle:
          passengerStop == null
              ? AppStrings.passageiro.minhaParadaAindaNaoPublicada
              : AppStrings.passageiro.paradaResumoComOrdem(
                passengerStop.ordem,
                passengerStop.passageiroNome,
              ),
      stopHelper:
          passengerStop == null
              ? AppStrings.passageiro.etapaDaFilaNaoPublicada
              : stopsBefore == 0
              ? AppStrings.passageiro.voceEoProximoDaFila
              : AppStrings.passageiro.paradasAntesDeVoce(stopsBefore),
      serviceName: trip.nomeOperacional,
      driverName:
          trip.motorista?.nome ?? AppStrings.passageiro.motoristaNaoPublicado,
      operationLabel: trip.tipoOperacaoLabel,
    );
  }
}

class _StatusCopy {
  const _StatusCopy({required this.primary, required this.secondary});

  final String primary;
  final String secondary;
}

_StatusCopy _statusCopy({
  required TripTrackingRecord trip,
  required int stopsBefore,
  required TripTrackingPresentation signal,
  required TripOfflineContinuityPresentation offlineContinuity,
}) {
  if (trip.status == TripTrackingStatus.planned) {
    return _StatusCopy(
      primary: AppStrings.passageiro.vanAindaNaoIniciou,
      secondary: AppStrings.passageiro.aguardandoSaida,
    );
  }
  if (trip.status == TripTrackingStatus.completed) {
    return const _StatusCopy(
      primary: 'Viagem encerrada',
      secondary: 'Tudo certo por aqui',
    );
  }
  if (trip.status == TripTrackingStatus.canceled) {
    return const _StatusCopy(
      primary: 'Viagem cancelada',
      secondary: 'Confira detalhes com a empresa',
    );
  }
  if (stopsBefore <= 0 &&
      trip.trackingState.locationFreshness == TripLocationFreshness.live) {
    return _StatusCopy(
      primary: AppStrings.passageiro.suaParadaEProxima,
      secondary: AppStrings.passageiro.prepareSeParaEmbarcar,
    );
  }
  return switch (trip.trackingState.locationFreshness) {
    TripLocationFreshness.live => _StatusCopy(
      primary: 'Van a caminho',
      secondary: _remainingStopsLabel(stopsBefore),
    ),
    TripLocationFreshness.delayed => _StatusCopy(
      primary: signal.label,
      secondary:
          offlineContinuity.visible ? offlineContinuity.helper : signal.helper,
    ),
    TripLocationFreshness.stale => _StatusCopy(
      primary: signal.label,
      secondary:
          offlineContinuity.visible ? offlineContinuity.helper : signal.helper,
    ),
    TripLocationFreshness.paused => _StatusCopy(
      primary: signal.label,
      secondary:
          offlineContinuity.visible ? offlineContinuity.helper : signal.helper,
    ),
    TripLocationFreshness.unavailable ||
    TripLocationFreshness.unknown => _StatusCopy(
      primary: signal.label,
      secondary:
          offlineContinuity.visible ? offlineContinuity.helper : signal.helper,
    ),
  };
}

String _presentVanDistance({
  required int? stopsBefore,
  required TripTrackingStatus status,
  required TripLocationFreshness freshness,
  required TripTrackingPresentation signal,
  required TripOfflineContinuityPresentation offlineContinuity,
}) {
  if (status == TripTrackingStatus.planned) {
    return AppStrings.passageiro.aguardandoSaida;
  }
  if (status == TripTrackingStatus.completed) {
    return 'Viagem encerrada';
  }
  if (status == TripTrackingStatus.canceled) {
    return 'Viagem cancelada';
  }
  if (freshness != TripLocationFreshness.live) {
    if (offlineContinuity.visible) {
      return offlineContinuity.title;
    }
    return signal.helper;
  }
  if ((stopsBefore ?? 0) <= 0) {
    return AppStrings.passageiro.prepareSeParaEmbarcar;
  }
  return _remainingStopsLabel(stopsBefore!);
}

String _remainingStopsLabel(int stopsBefore) {
  if (stopsBefore <= 0) {
    return AppStrings.passageiro.suaParadaEProxima;
  }
  if (stopsBefore == 1) {
    return AppStrings.passageiro.faltaUmaParada;
  }
  return AppStrings.passageiro.faltamParadas(stopsBefore);
}

class _TripStatusBlock extends StatelessWidget {
  const _TripStatusBlock({
    required this.display,
    this.onToggleHud,
    this.hudToggleKey,
  });

  final PassengerTripGlassDisplay display;
  final VoidCallback? onToggleHud;
  final Key? hudToggleKey;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxWidth < 360;
        return DecoratedBox(
          decoration: BoxDecoration(
            color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.46),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: VanRidesMapTokens.border.withValues(alpha: 0.28),
            ),
          ),
          child: Padding(
            padding: EdgeInsets.fromLTRB(13, compact ? 10 : 12, 13, 12),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        display.primary,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(
                          context,
                        ).textTheme.titleMedium?.copyWith(
                          color: VanRidesMapTokens.textPrimary,
                          fontWeight: FontWeight.w900,
                          height: 1.08,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        display.vanDistanceLabel,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: VanRidesMapTokens.blueTracking,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                _SignalPill(signal: display.signal, compact: compact),
                if (onToggleHud != null) ...[
                  const SizedBox(width: 4),
                  _HudToggleButton(
                    buttonKey: hudToggleKey,
                    expanded: true,
                    onPressed: onToggleHud!,
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}

class _HudToggleButton extends StatelessWidget {
  const _HudToggleButton({
    required this.expanded,
    required this.onPressed,
    this.buttonKey,
  });

  final bool expanded;
  final VoidCallback onPressed;
  final Key? buttonKey;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: expanded ? 'Recolher resumo' : 'Expandir resumo',
      child: IconButton(
        key: buttonKey,
        onPressed: onPressed,
        visualDensity: VisualDensity.compact,
        constraints: const BoxConstraints(minWidth: 34, minHeight: 34),
        padding: EdgeInsets.zero,
        icon: Icon(
          expanded
              ? Icons.keyboard_arrow_down_rounded
              : Icons.keyboard_arrow_up_rounded,
          color: VanRidesMapTokens.textSecondary,
          size: 24,
        ),
      ),
    );
  }
}

class _SignalPill extends StatelessWidget {
  const _SignalPill({required this.signal, required this.compact});

  final TripTrackingPresentation signal;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final label = passengerTripSignalDisplayLabel(
      signal.label,
      compact: compact,
      compactLabel: signal.compactLabel,
    );
    return Semantics(
      label: signal.label,
      child: Container(
        padding: EdgeInsets.symmetric(
          horizontal: compact ? 9 : 10,
          vertical: 8,
        ),
        decoration: BoxDecoration(
          color: signal.color.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: signal.color.withValues(alpha: 0.32)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(signal.icon, color: signal.color, size: 16),
            const SizedBox(width: 6),
            Text(
              label,
              style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: VanRidesMapTokens.textPrimary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

String passengerTripSignalDisplayLabel(
  String label, {
  required bool compact,
  String? compactLabel,
}) {
  return _passengerTripSignalDisplayLabel(
    label,
    compact: compact,
    compactLabel: compactLabel,
  );
}

String _passengerTripSignalDisplayLabel(
  String label, {
  required bool compact,
  String? compactLabel,
}) {
  if (compact && compactLabel != null && compactLabel.trim().isNotEmpty) {
    return compactLabel;
  }
  if (label == AppStrings.passageiro.aguardandoSinal) {
    return 'Sem sinal';
  }
  if (!compact) {
    return label;
  }
  if (label.startsWith('Sinal ')) {
    return _capitalizeSignalLabel(label.substring('Sinal '.length));
  }
  return label;
}

String _capitalizeSignalLabel(String label) {
  if (label.isEmpty) {
    return label;
  }
  return label.substring(0, 1).toUpperCase() + label.substring(1);
}

class _PassengerStopLine extends StatelessWidget {
  const _PassengerStopLine({required this.display});

  final PassengerTripGlassDisplay display;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(15),
              border: Border.all(
                color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.28),
              ),
            ),
            child: const Icon(
              Icons.person_pin_circle_rounded,
              color: VanRidesMapTokens.blueTracking,
              size: 20,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  AppStrings.passageiro.minhaParada,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: VanRidesMapTokens.blueTracking,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  display.myStopTitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: VanRidesMapTokens.textPrimary,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '${display.stopHelper} • ${display.serviceName} • ${display.driverName}',
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
          if (display.operationLabel.isNotEmpty) ...[
            const SizedBox(width: 8),
            _MiniPill(icon: Icons.route_rounded, label: display.operationLabel),
          ],
        ],
      ),
    );
  }
}

class _MiniPill extends StatelessWidget {
  const _MiniPill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.36),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: VanRidesMapTokens.textSecondary, size: 16),
          const SizedBox(width: 6),
          Text(
            label,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: VanRidesMapTokens.textPrimary,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}
