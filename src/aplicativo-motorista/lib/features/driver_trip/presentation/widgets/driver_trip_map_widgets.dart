import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/driver_trip_progress_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

class DriverMapHero extends StatelessWidget {
  const DriverMapHero({
    super.key,
    required this.trip,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.liveLocationGateway,
    this.liveLocationWebSocketService,
    required this.mapRenderMode,
    required this.height,
    this.seedLocation,
    required this.sharingLocation,
    required this.focusMode,
  });

  final DriverTripRecord trip;
  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final TripLiveMapRenderMode mapRenderMode;
  final double height;
  final TripLiveLocationRecord? seedLocation;
  final bool sharingLocation;
  final TripLiveMapFocusMode focusMode;

  @override
  Widget build(BuildContext context) {
    return TripLiveMapPanel(
      motoristaId: motoristaId,
      email: email,
      senha: senha,
      accessToken: accessToken,
      height: height,
      gateway: liveLocationGateway,
      webSocketService: liveLocationWebSocketService,
      renderMode: mapRenderMode,
      focusMode: focusMode,
      routeGeometry: trip.rotaGeografica,
      trackingState: trip.trackingState,
      seedLocation: seedLocation,
    );
  }
}

class DriverFullMapTab extends StatelessWidget {
  const DriverFullMapTab({
    super.key,
    required this.isLoading,
    required this.errorMessage,
    required this.trip,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.liveLocationGateway,
    this.liveLocationWebSocketService,
    required this.mapRenderMode,
    this.driverLiveLocation,
    required this.onRefresh,
  });

  final bool isLoading;
  final String? errorMessage;
  final DriverTripRecord? trip;
  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final TripLiveMapRenderMode mapRenderMode;
  final TripLiveLocationRecord? driverLiveLocation;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final trip = this.trip;
    if (isLoading || errorMessage != null || trip == null) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 120),
          child: MapFirstStateCard(
            icon: Icons.map_outlined,
            title: AppStrings.motorista.mapaCompletoIndisponivel,
            message:
                errorMessage ??
                AppStrings.motorista.mapaCompletoIndisponivelDescricao,
            actionLabel:
                errorMessage != null ? AppStrings.motorista.atualizar : null,
            onAction: errorMessage != null ? onRefresh : null,
          ),
        ),
      );
    }

    return Stack(
      children: [
        Positioned.fill(
          child: TripLiveMapPanel(
            motoristaId: motoristaId,
            email: email,
            senha: senha,
            accessToken: accessToken,
            gateway: liveLocationGateway,
            webSocketService: liveLocationWebSocketService,
            renderMode: mapRenderMode,
            routeGeometry: trip.rotaGeografica,
            trackingState: trip.trackingState,
            seedLocation: driverLiveLocation,
            focusMode: TripLiveMapFocusMode.fullJourney,
          ),
        ),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
            child: Column(
              children: [
                Row(
                  children: [
                    const Spacer(),
                    MapFirstGlassChip(
                      icon: Icons.alt_route_outlined,
                      label: presentRouteVersionLabel(trip.rotaVersao),
                      compact: true,
                    ),
                  ],
                ),
                const Spacer(),
                DriverMapContextCard(trip: trip),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class DriverMapContextCard extends StatelessWidget {
  const DriverMapContextCard({super.key, required this.trip});

  final DriverTripRecord trip;

  @override
  Widget build(BuildContext context) {
    final nextStop = trip.proximaParada;
    final signal = presentLocationFreshness(
      trip.trackingState.locationFreshness,
      role: TripSignalRole.driver,
    );

    return MapFirstSurfaceCard(
      color: VanRidesMapTokens.surface.withValues(alpha: 0.96),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _DriverMapMiniChip(
                icon: Icons.flag_outlined,
                label: presentDriverRemainingStopsLabel(trip),
                color: VanRidesMapTokens.greenAction,
              ),
              _DriverMapMiniChip(
                icon: signal.icon,
                label: signal.label,
                color: signal.color,
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      AppStrings.motorista.proximaParada,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: VanRidesMapTokens.textMuted,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      nextStop?.passageiroNome ??
                          AppStrings.motorista.semProximaParada,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: VanRidesMapTokens.textPrimary,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    AppStrings.motorista.eta,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: VanRidesMapTokens.textMuted,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    presentEtaLabel(trip.etaMinutosAteProximaParada),
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      color: VanRidesMapTokens.greenAction,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _DriverMapMiniChip extends StatelessWidget {
  const _DriverMapMiniChip({
    required this.icon,
    required this.label,
    required this.color,
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.34)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 15, color: color),
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
    );
  }
}
