import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

class VerLocalizacaoScreen extends StatefulWidget {
  const VerLocalizacaoScreen({
    super.key,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.trip,
    this.liveLocationGateway,
    this.liveLocationWebSocketService,
    this.mapRenderMode = TripLiveMapRenderMode.interactive,
  });

  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final TripTrackingRecord? trip;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final TripLiveMapRenderMode mapRenderMode;

  static const refreshButtonKey = Key('passageiro.localizacao.refresh');
  static const erroTextKey = Key('passageiro.localizacao.erro');
  static const mapKey = Key('passageiro.localizacao.map');

  @override
  State<VerLocalizacaoScreen> createState() => _VerLocalizacaoScreenState();
}

class _VerLocalizacaoScreenState extends State<VerLocalizacaoScreen> {
  static const bool _e2eMode = bool.fromEnvironment(
    'VANRIDES_E2E_TEST_MODE',
    defaultValue: false,
  );

  int _refreshSeed = 0;
  TripLiveLocationRecord? _latestLocation;

  @override
  Widget build(BuildContext context) {
    final trip = widget.trip;
    final routeGeometry = trip?.rotaGeografica;
    final status = trip == null ? null : presentTripStatus(trip.status);
    final stop = trip?.paradaDoPassageiro;
    final routeProviderLabel = presentRouteGeometryLabel(routeGeometry);
    final routeProviderHelper = presentRouteGeometryHelper(routeGeometry);
    final dateLabel =
        trip == null
            ? DateFormat('dd/MM/yyyy', 'pt_BR').format(DateTime.now())
            : DateFormat('dd/MM/yyyy', 'pt_BR').format(trip.dataOperacao);
    final etaLabel = presentEtaLabel(
      stop?.etaMinutos ?? trip?.etaMinutosAtePassageiro,
    );

    return Scaffold(
      backgroundColor: MapFirstPalette.background,
      appBar: AppBar(
        backgroundColor: MapFirstPalette.background,
        foregroundColor: MapFirstPalette.textStrong,
        surfaceTintColor: Colors.transparent,
        title: Text(AppStrings.passageiro.localizacaoDoMotorista),
        actions: [
          IconButton(
            key: VerLocalizacaoScreen.refreshButtonKey,
            icon: const Icon(Icons.refresh),
            tooltip: AppStrings.passageiro.atualizarSinal,
            onPressed: () {
              setState(() => _refreshSeed += 1);
            },
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 36),
          children: [
            Text(
              AppStrings.passageiro.mapaCompletoDaViagem,
              style: Theme.of(context).textTheme.displaySmall?.copyWith(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 10),
            Text(
              AppStrings.passageiro.vejaTrajetoPublicadoHoje,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                color: MapFirstPalette.textMuted,
                fontWeight: FontWeight.w400,
                height: 1.32,
              ),
            ),
            const SizedBox(height: 22),
            TripLiveMapPanel(
              key: ValueKey('trip-live-map-${_refreshSeed.toString()}'),
              motoristaId: widget.motoristaId,
              email: widget.email,
              senha: widget.senha,
              accessToken: widget.accessToken,
              height: 520,
              gateway: widget.liveLocationGateway,
              webSocketService: widget.liveLocationWebSocketService,
              renderMode: widget.mapRenderMode,
              routeGeometry: routeGeometry,
              trackingState:
                  trip?.trackingState ?? TripTrackingStateRecord.unavailable,
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
                      _TopChip(
                        icon: status?.icon ?? Icons.location_searching_outlined,
                        label:
                            status?.label ??
                            AppStrings.passageiro.aguardandoSinal,
                        color: status?.color ?? const Color(0xFF2D6CDF),
                      ),
                      _TopChip(
                        icon: Icons.calendar_today_outlined,
                        label: dateLabel,
                      ),
                      _TopChip(
                        icon: Icons.alt_route_outlined,
                        label:
                            trip == null
                                ? AppStrings.passageiro.rotaDoDia
                                : presentRouteVersionLabel(trip.rotaVersao),
                      ),
                      _TopChip(
                        icon:
                            usesRealRoadRouting(routeGeometry)
                                ? Icons.route_rounded
                                : Icons.timeline_rounded,
                        label: routeProviderLabel,
                      ),
                      _TopChip(
                        icon: Icons.radar_outlined,
                        label:
                            _latestLocation != null
                                ? AppStrings.passageiro.sinalAoVivo
                                : AppStrings.passageiro.aguardandoSinal,
                        color:
                            _latestLocation != null
                                ? MapFirstPalette.green
                                : MapFirstPalette.textStrong,
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            if (trip == null)
              Text(
                AppStrings.passageiro.aguardandoDadosDaViagemParaMapa,
                key: VerLocalizacaoScreen.erroTextKey,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: MapFirstPalette.textMuted,
                ),
              )
            else ...[
              _ExpandedSurfaceCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      trip.rotaNome,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(
                        context,
                      ).textTheme.headlineSmall?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      status?.helper ??
                          AppStrings.passageiro.viagemProntaParaAcompanhamento,
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: MapFirstPalette.textMuted,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      routeProviderHelper,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: MapFirstPalette.textMuted,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 18),
                    _ExpandedInfoRow(
                      label: AppStrings.passageiro.motorista,
                      value:
                          trip.motorista?.nome ??
                          AppStrings.passageiro.naoPublicado,
                    ),
                    const SizedBox(height: 10),
                    _ExpandedInfoRow(
                      label: AppStrings.passageiro.suaParada,
                      value:
                          stop == null
                              ? AppStrings.passageiro.aindaNaoPublicada
                              : AppStrings.passageiro.paradaResumoComOrdem(
                                stop.ordem,
                                stop.passageiroNome,
                              ),
                    ),
                    const SizedBox(height: 10),
                    _ExpandedInfoRow(
                      label: AppStrings.passageiro.etaAtual,
                      value: etaLabel,
                    ),
                  ],
                ),
              ),
              if (routeGeometry != null) ...[
                const SizedBox(height: 16),
                _ExpandedSurfaceCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        AppStrings.passageiro.filaDeParadas,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          color: MapFirstPalette.textStrong,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Text(
                        _buildQueueHelper(routeGeometry),
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: MapFirstPalette.textMuted,
                          height: 1.4,
                        ),
                      ),
                      const SizedBox(height: 16),
                      for (final waypoint in routeGeometry.orderedStops) ...[
                        _ExpandedQueueTile(waypoint: waypoint),
                        if (waypoint != routeGeometry.orderedStops.last)
                          const SizedBox(height: 10),
                      ],
                    ],
                  ),
                ),
              ],
            ],
            if (_e2eMode && _latestLocation != null) ...[
              const SizedBox(height: 12),
              Text(
                AppStrings.passageiro.coordenadasAoVivo(
                  _latestLocation!.latitude,
                  _latestLocation!.longitude,
                ),
                key: const Key('passageiro.localizacao.coords'),
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: MapFirstPalette.textMuted,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _buildQueueHelper(TripRouteGeometryRecord geometry) {
    final beforeMine = geometry.paradasAntesDoPassageiro;
    final queuePosition = geometry.posicaoPassageiroNaFila;
    final totalStops =
        geometry.filaTotalParadas ?? geometry.orderedStops.length;

    if (beforeMine == null || queuePosition == null) {
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

class _TopChip extends StatelessWidget {
  const _TopChip({
    required this.icon,
    required this.label,
    this.color = const Color(0xFF161B36),
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.9),
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
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 10),
          Text(
            label,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: color,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _ExpandedSurfaceCard extends StatelessWidget {
  const _ExpandedSurfaceCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return MapFirstSurfaceCard(
      key: VerLocalizacaoScreen.mapKey,
      padding: const EdgeInsets.all(22),
      child: child,
    );
  }
}

class _ExpandedInfoRow extends StatelessWidget {
  const _ExpandedInfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 110,
          child: Text(
            label,
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
              color: MapFirstPalette.textMuted,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            value,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    );
  }
}

class _ExpandedQueueTile extends StatelessWidget {
  const _ExpandedQueueTile({required this.waypoint});

  final TripRouteWaypointRecord waypoint;

  @override
  Widget build(BuildContext context) {
    final highlighted = waypoint.paradaDoPassageiro;
    final isNext = waypoint.proximaParada;
    final tileColor =
        highlighted
            ? MapFirstPalette.blueSoft
            : isNext
            ? MapFirstPalette.amberSoft
            : MapFirstPalette.surfaceRaised.withValues(alpha: 0.72);
    final accent =
        highlighted
            ? MapFirstPalette.blue
            : isNext
            ? MapFirstPalette.amber
            : MapFirstPalette.textMuted;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: tileColor,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: MapFirstPalette.border.withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: accent.withValues(alpha: 0.16),
            child: Text(
              waypoint.ordem?.toString() ?? '-',
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: accent,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  waypoint.nome,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  highlighted
                      ? AppStrings.passageiro.suaParada
                      : isNext
                      ? AppStrings.passageiro.proximaParadaDaViagem
                      : AppStrings.passageiro.paradaOperacionalDaRota,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: MapFirstPalette.textMuted,
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
