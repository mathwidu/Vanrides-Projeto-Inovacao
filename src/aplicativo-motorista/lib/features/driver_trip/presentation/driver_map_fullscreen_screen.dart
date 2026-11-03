import 'package:flutter/material.dart';

import '../../../services/websocket_service.dart';
import '../../tracking/data/trip_live_location_service.dart';
import '../../tracking/domain/trip_live_location_record.dart';
import '../../tracking/presentation/map_first_v2_components.dart';
import '../../tracking/presentation/trip_live_map_panel.dart';
import '../../tracking/presentation/trip_tracking_presenter.dart';
import '../domain/driver_trip_record.dart';
import 'widgets/driver_trip_map_widgets.dart';

class DriverMapFullscreenScreen extends StatelessWidget {
  const DriverMapFullscreenScreen({
    super.key,
    required this.trip,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.liveLocationGateway,
    this.liveLocationWebSocketService,
    this.seedLocation,
    this.mapRenderMode = TripLiveMapRenderMode.interactive,
  });

  final DriverTripRecord trip;
  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final TripLiveLocationRecord? seedLocation;
  final TripLiveMapRenderMode mapRenderMode;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
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
              seedLocation: seedLocation,
              focusMode: TripLiveMapFocusMode.fullJourney,
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
              child: Column(
                children: [
                  Row(
                    children: [
                      MapFirstIconButton(
                        icon: Icons.close_rounded,
                        tooltip: 'Fechar mapa',
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                      const Spacer(),
                      MapFirstGlassChip(
                        icon: Icons.alt_route_outlined,
                        label: presentRouteVersionLabel(trip.rotaVersao),
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
      ),
    );
  }
}
