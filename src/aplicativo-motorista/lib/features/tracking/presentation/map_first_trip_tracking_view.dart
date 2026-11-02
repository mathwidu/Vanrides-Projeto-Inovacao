import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../l10n/app_strings.dart';
import '../../../services/websocket_service.dart';
import '../data/trip_live_location_service.dart';
import '../domain/trip_live_location_record.dart';
import '../domain/trip_route_geometry_record.dart';
import '../domain/trip_tracking_record.dart';
import 'map_first_trip_tracking_components.dart';
import 'map_first_v2_components.dart';
import 'trip_live_map_panel.dart';
import 'trip_tracking_presenter.dart';

class MapFirstTripTrackingView extends StatefulWidget {
  const MapFirstTripTrackingView({
    super.key,
    required this.trip,
    required this.audienceLabel,
    required this.headline,
    required this.subheadline,
    required this.nextEventLabel,
    required this.secondaryStatusLabel,
    required this.primaryActionLabel,
    this.motoristaId,
    this.email,
    this.senha,
    this.accessToken,
    this.lastUpdatedAt,
    this.primaryActionKey,
    this.secondaryActionLabel,
    this.secondaryActionKey,
    this.onPrimaryAction,
    this.onSecondaryAction,
    this.trailing,
    this.liveLocationGateway,
    this.liveLocationWebSocketService,
    this.mapRenderMode = TripLiveMapRenderMode.interactive,
    this.mapHeight = 420,
  });

  final TripTrackingRecord trip;
  final String audienceLabel;
  final String headline;
  final String subheadline;
  final int? motoristaId;
  final String? email;
  final String? senha;
  final String? accessToken;
  final DateTime? lastUpdatedAt;
  final String nextEventLabel;
  final String secondaryStatusLabel;
  final String primaryActionLabel;
  final Key? primaryActionKey;
  final String? secondaryActionLabel;
  final Key? secondaryActionKey;
  final VoidCallback? onPrimaryAction;
  final VoidCallback? onSecondaryAction;
  final Widget? trailing;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final TripLiveMapRenderMode mapRenderMode;
  final double mapHeight;

  @override
  State<MapFirstTripTrackingView> createState() =>
      _MapFirstTripTrackingViewState();
}

class _MapFirstTripTrackingViewState extends State<MapFirstTripTrackingView> {
  TripLiveLocationRecord? _latestLocation;

  @override
  Widget build(BuildContext context) {
    final trip = widget.trip;
    final status = presentTripStatus(trip.status);
    final routeDateLabel = DateFormat(
      'dd/MM/yyyy',
      'pt_BR',
    ).format(trip.dataOperacao);
    final routeGeometry = trip.rotaGeografica;
    final passengerStop = routeGeometry?.passengerStop;
    final nextStop = routeGeometry?.nextStop;
    final routeProviderLabel = presentRouteGeometryLabel(routeGeometry);
    final routeProviderHelper = presentRouteGeometryHelper(routeGeometry);
    final navigationSummary = presentNavigationSummary(
      routeGeometry?.navegacao,
    );
    final etaLabel = presentEtaLabel(
      passengerStop?.ordem != null
          ? trip.etaMinutosAtePassageiro
          : trip.paradaDoPassageiro?.etaMinutos ?? trip.etaMinutosAtePassageiro,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _ExperienceTopBar(
          badge: widget.audienceLabel,
          trailing: widget.trailing,
        ),
        const SizedBox(height: 20),
        Text(
          widget.headline,
          style: Theme.of(context).textTheme.displaySmall?.copyWith(
            color: MapFirstPalette.textStrong,
            fontWeight: FontWeight.w800,
            height: 1.04,
          ),
        ),
        const SizedBox(height: 10),
        Text(
          widget.subheadline,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
            color: MapFirstPalette.textMuted,
            fontWeight: FontWeight.w400,
            height: 1.32,
          ),
        ),
        const SizedBox(height: 22),
        TripLiveMapPanel(
          motoristaId: widget.motoristaId ?? trip.motorista?.id ?? 0,
          email: widget.email ?? '',
          senha: widget.senha ?? '',
          accessToken: widget.accessToken,
          height: widget.mapHeight,
          gateway: widget.liveLocationGateway,
          webSocketService: widget.liveLocationWebSocketService,
          renderMode:
              widget.motoristaId == null && trip.motorista?.id == null
                  ? TripLiveMapRenderMode.placeholder
                  : widget.mapRenderMode,
          routeGeometry: routeGeometry,
          trackingState: trip.trackingState,
          seedLocation: _latestLocation,
          onLocationChanged: (location) {
            if (!mounted) {
              return;
            }
            setState(() => _latestLocation = location ?? _latestLocation);
          },
          overlay: Padding(
            padding: const EdgeInsets.all(18),
            child: Align(
              alignment: Alignment.topLeft,
              child: Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _OverlayChip(
                    icon: status.icon,
                    label: status.label,
                    backgroundColor: status.color.withValues(alpha: 0.14),
                    foregroundColor: status.color,
                  ),
                  _OverlayChip(
                    icon: Icons.calendar_today_outlined,
                    label: routeDateLabel,
                  ),
                  _OverlayChip(
                    icon: Icons.radar_outlined,
                    label:
                        _latestLocation != null
                            ? AppStrings.passageiro.sinalAoVivo
                            : AppStrings.passageiro.aguardandoSinal,
                    foregroundColor:
                        _latestLocation != null
                            ? MapFirstPalette.green
                            : MapFirstPalette.textStrong,
                  ),
                  _OverlayChip(
                    icon: Icons.alt_route_outlined,
                    label: presentRouteVersionLabel(trip.rotaVersao),
                  ),
                  _OverlayChip(
                    icon:
                        usesRealRoadRouting(routeGeometry)
                            ? Icons.route_rounded
                            : Icons.timeline_rounded,
                    label: routeProviderLabel,
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 18),
        _SurfaceCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 12,
                runSpacing: 12,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  ConstrainedBox(
                    constraints: const BoxConstraints(
                      minWidth: 180,
                      maxWidth: 420,
                    ),
                    child: Text(
                      trip.nomeOperacional,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(
                        context,
                      ).textTheme.headlineSmall?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  MapFirstRouteContextBadge(
                    icon:
                        usesRealRoadRouting(routeGeometry)
                            ? Icons.route_rounded
                            : Icons.timeline_rounded,
                    label: routeProviderLabel,
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                status.helper,
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                  color: MapFirstPalette.textMuted,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 18),
              Text(
                routeProviderHelper,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: MapFirstPalette.textMuted,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 18),
              MapFirstTripMetricGrid(
                items: [
                  MapFirstTripMetricItem(
                    label: AppStrings.passageiro.motorista,
                    value:
                        trip.motorista?.nome ??
                        AppStrings.passageiro.naoPublicado,
                    icon: Icons.directions_bus_rounded,
                  ),
                  MapFirstTripMetricItem(
                    label: AppStrings.passageiro.etaAtual,
                    value: etaLabel,
                    icon: Icons.schedule_outlined,
                  ),
                  MapFirstTripMetricItem(
                    label: widget.nextEventLabel,
                    value:
                        nextStop == null
                            ? widget.secondaryStatusLabel
                            : AppStrings.passageiro.paradaResumo(
                              nextStop.ordem,
                              nextStop.nome,
                            ),
                    icon: Icons.flag_outlined,
                  ),
                  MapFirstTripMetricItem(
                    label: AppStrings.passageiro.suaParada,
                    value:
                        passengerStop == null
                            ? AppStrings.passageiro.aindaNaoPublicada
                            : AppStrings.passageiro.paradaResumo(
                              passengerStop.ordem,
                              passengerStop.nome,
                            ),
                    icon: Icons.place_outlined,
                  ),
                  if (routeGeometry?.posicaoPassageiroNaFila != null)
                    MapFirstTripMetricItem(
                      label: AppStrings.passageiro.posicaoNaFila,
                      value: AppStrings.passageiro.posicaoNaFilaResumo(
                        routeGeometry!.posicaoPassageiroNaFila!,
                        routeGeometry.filaTotalParadas ??
                            routeGeometry.orderedStops.length,
                      ),
                      icon: Icons.format_list_numbered_rounded,
                    ),
                  if (routeGeometry?.paradasAntesDoPassageiro != null)
                    MapFirstTripMetricItem(
                      label: AppStrings.passageiro.paradasAntesDeVoceTitulo,
                      value: '${routeGeometry!.paradasAntesDoPassageiro}',
                      icon: Icons.people_alt_outlined,
                    ),
                  if (routeGeometry?.navegacao != null)
                    MapFirstTripMetricItem(
                      label: AppStrings.passageiro.navegacao,
                      value: navigationSummary,
                      icon: Icons.near_me_outlined,
                    ),
                ],
              ),
            ],
          ),
        ),
        if (routeGeometry != null) ...[
          const SizedBox(height: 14),
          _SurfaceCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  AppStrings.passageiro.filaDaViagem,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  _buildQueueHelper(routeGeometry),
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: MapFirstPalette.textMuted,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 16),
                for (final stop in routeGeometry.orderedStops) ...[
                  MapFirstTripQueueStopTile(
                    stop: stop,
                    highlighted: stop.paradaDoPassageiro,
                    isNext: stop.proximaParada,
                  ),
                  if (stop != routeGeometry.orderedStops.last)
                    const SizedBox(height: 10),
                ],
              ],
            ),
          ),
        ],
        if (routeGeometry?.legs.isNotEmpty == true) ...[
          const SizedBox(height: 14),
          _SurfaceCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  AppStrings.passageiro.trechosDaRota,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 14),
                for (final leg in routeGeometry!.legs) ...[
                  MapFirstTripRouteLegTile(leg: leg),
                  if (leg != routeGeometry.legs.last) const Divider(height: 24),
                ],
              ],
            ),
          ),
        ],
        const SizedBox(height: 18),
        MapFirstTripActionBar(
          primaryActionKey: widget.primaryActionKey,
          primaryLabel: widget.primaryActionLabel,
          onPrimaryAction: widget.onPrimaryAction,
          secondaryActionKey: widget.secondaryActionKey,
          secondaryLabel: widget.secondaryActionLabel,
          onSecondaryAction: widget.onSecondaryAction,
        ),
      ],
    );
  }

  String _buildQueueHelper(TripRouteGeometryRecord geometry) {
    final beforeMine = geometry.paradasAntesDoPassageiro;
    final queuePosition = geometry.posicaoPassageiroNaFila;
    final totalStops =
        geometry.filaTotalParadas ?? geometry.orderedStops.length;

    if (queuePosition == null || beforeMine == null) {
      return AppStrings.passageiro.filaOperacionalDescricao;
    }

    if (beforeMine <= 0) {
      return AppStrings.passageiro.suaParadaNaFrenteDaFila(totalStops);
    }

    return AppStrings.passageiro.filaComParadasAntes(
      beforeMine,
      queuePosition,
      totalStops,
    );
  }
}

class ExperiencePlaceholderView extends StatelessWidget {
  const ExperiencePlaceholderView({
    super.key,
    required this.title,
    required this.description,
    required this.icon,
    required this.badge,
    this.ctaLabel,
    this.onCta,
  });

  final String title;
  final String description;
  final IconData icon;
  final String badge;
  final String? ctaLabel;
  final VoidCallback? onCta;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _ExperienceTopBar(badge: badge),
        const SizedBox(height: 20),
        _SurfaceCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              CircleAvatar(
                radius: 24,
                backgroundColor: MapFirstPalette.blue.withValues(alpha: 0.16),
                child: Icon(icon, color: MapFirstPalette.blue),
              ),
              const SizedBox(height: 20),
              Text(
                title,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  color: MapFirstPalette.textStrong,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                description,
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                  color: MapFirstPalette.textMuted,
                  height: 1.5,
                ),
              ),
            ],
          ),
        ),
        if (ctaLabel != null) ...[
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: onCta,
            icon: const Icon(Icons.arrow_forward),
            label: Text(ctaLabel!),
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(54),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _ExperienceTopBar extends StatelessWidget {
  const _ExperienceTopBar({required this.badge, this.trailing});

  final String badge;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
          decoration: BoxDecoration(
            color: MapFirstPalette.amberSoft,
            borderRadius: BorderRadius.circular(26),
            border: Border.all(
              color: MapFirstPalette.amber.withValues(alpha: 0.24),
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.directions_bus_rounded,
                color: MapFirstPalette.amber,
              ),
              const SizedBox(width: 12),
              Text(
                badge,
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: MapFirstPalette.textStrong,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        const Spacer(),
        if (trailing != null) trailing!,
      ],
    );
  }
}

class _SurfaceCard extends StatelessWidget {
  const _SurfaceCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return MapFirstSurfaceCard(padding: const EdgeInsets.all(22), child: child);
  }
}

class _OverlayChip extends StatelessWidget {
  const _OverlayChip({
    required this.icon,
    required this.label,
    this.backgroundColor = MapFirstPalette.surfaceRaised,
    this.foregroundColor = MapFirstPalette.textStrong,
  });

  final IconData icon;
  final String label;
  final Color backgroundColor;
  final Color foregroundColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: backgroundColor.withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: MapFirstPalette.border.withValues(alpha: 0.5),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66070C12),
            blurRadius: 20,
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 20, color: foregroundColor),
          const SizedBox(width: 10),
          Text(
            label,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: foregroundColor,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}
