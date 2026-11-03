import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import '../../../l10n/app_strings.dart';
import '../../../services/auth_headers.dart';
import '../../../services/google_api_config.dart';
import '../../../services/websocket_service.dart';
import '../data/trip_live_location_service.dart';
import '../domain/trip_live_location_record.dart';
import '../domain/trip_route_geometry_record.dart';
import '../domain/trip_tracking_state_record.dart';
import 'trip_live_map_motion.dart';
import 'trip_live_map_renderer.dart';
import 'trip_live_map_state.dart';
import 'vanrides_map_renderer_spike.dart';
import 'widgets/trip_live_map_surface.dart';
import 'widgets/vanrides_dark_map_style.dart';
import 'widgets/vanrides_deterministic_map_surface.dart';
import 'widgets/vanrides_maplibre_proof_surface.dart';
import 'widgets/vanrides_map_markers.dart';
import 'widgets/vanrides_route_layers.dart';

export 'trip_live_map_renderer.dart';
export 'trip_live_map_state.dart';

class TripLiveMapPanel extends StatefulWidget {
  const TripLiveMapPanel({
    super.key,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.height = 360,
    this.overlay,
    this.gateway,
    this.webSocketService,
    this.renderMode = TripLiveMapRenderMode.interactive,
    this.renderer = TripLiveMapRenderer.googleLegacy,
    this.mapLibreProofEnabled = vanRidesMapLibreProofEnabled,
    this.onLocationChanged,
    this.routeGeometry,
    this.trackingState = TripTrackingStateRecord.unavailable,
    this.seedLocation,
    this.focusMode = TripLiveMapFocusMode.fullJourney,
    this.showControls = true,
    this.mapPadding = defaultMapPadding,
  });

  static const defaultMapPadding = EdgeInsets.fromLTRB(28, 40, 28, 136);

  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final double height;
  final Widget? overlay;
  final TripLiveLocationGateway? gateway;
  final WebSocketServiceBase? webSocketService;
  final TripLiveMapRenderMode renderMode;
  final TripLiveMapRenderer renderer;
  final bool mapLibreProofEnabled;
  final ValueChanged<TripLiveLocationRecord?>? onLocationChanged;
  final TripRouteGeometryRecord? routeGeometry;
  final TripTrackingStateRecord trackingState;
  final TripLiveLocationRecord? seedLocation;
  final TripLiveMapFocusMode focusMode;
  final bool showControls;
  final EdgeInsets mapPadding;

  @override
  State<TripLiveMapPanel> createState() => _TripLiveMapPanelState();
}

class _TripLiveMapPanelState extends State<TripLiveMapPanel> {
  static const _e2eMode = bool.fromEnvironment(
    'VANRIDES_E2E_TEST_MODE',
    defaultValue: false,
  );

  GoogleMapController? _mapController;
  late final TripLiveLocationGateway _gateway;
  late final WebSocketServiceBase _webSocketService;

  TripLiveLocationRecord? _location;
  bool _loading = true;
  String? _errorMessage;
  bool _cameraLockedToContent = true;
  bool _programmaticCameraMove = false;
  TripLiveMapMotionPolicy _motionPolicy = TripLiveMapMotionPolicy.animated;
  late final Future<VanRidesMapMarkerIconSet> _markerIconsFuture;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ??
        TripLiveLocationService(
          email: widget.email,
          senha: widget.senha,
          accessToken: widget.accessToken,
        );
    _webSocketService = widget.webSocketService ?? WebSocketService();
    _location = widget.seedLocation;
    _markerIconsFuture = const VanRidesMapMarkerIconFactory().create();
    if (_shouldSkipNetworkWork) {
      _loading = false;
      return;
    }
    _loadInitialLocation();
    _connectWebSocket();
  }

  @override
  void didUpdateWidget(covariant TripLiveMapPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    final nextSeed = widget.seedLocation;
    if (nextSeed != null &&
        (oldWidget.seedLocation?.latitude != nextSeed.latitude ||
            oldWidget.seedLocation?.longitude != nextSeed.longitude)) {
      _location = nextSeed;
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _fitCameraToContent(force: true),
      );
    }
    if (oldWidget.routeGeometry?.polyline != widget.routeGeometry?.polyline) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _fitCameraToContent(force: true),
      );
    }
    if (oldWidget.focusMode != widget.focusMode) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _fitCameraToContent(force: true),
      );
    }
  }

  bool get _shouldSkipNetworkWork {
    return widget.renderMode == TripLiveMapRenderMode.placeholder ||
        widget.motoristaId <= 0 ||
        (widget.accessToken == null &&
            (widget.email.isEmpty || widget.senha.isEmpty));
  }

  @override
  void dispose() {
    _webSocketService.disconnect();
    _webSocketService.dispose();
    super.dispose();
  }

  Future<void> _loadInitialLocation() async {
    setState(() {
      _loading = true;
      _errorMessage = null;
    });

    try {
      final location = await _gateway.fetchLatestLocation(widget.motoristaId);
      if (!mounted) {
        return;
      }
      setState(() {
        _location = location ?? _location;
        _loading = false;
      });
      widget.onLocationChanged?.call(location);
      unawaited(_fitCameraToContent());
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loading = false;
        _errorMessage =
            error is TripLiveLocationException
                ? error.message
                : AppStrings.passageiro.mapaIndisponivelNoMomento;
      });
    }
  }

  void _connectWebSocket() {
    final companyId = extractCompanyIdFromAccessToken(widget.accessToken);
    if (companyId == null) {
      return;
    }

    _webSocketService.onLocationReceived = (payload) {
      final receivedMotoristaId = (payload['motoristaId'] as num?)?.toInt();
      if (receivedMotoristaId != widget.motoristaId || !mounted) {
        return;
      }

      final latitude = (payload['latitude'] as num?)?.toDouble();
      final longitude = (payload['longitude'] as num?)?.toDouble();
      if (latitude == null || longitude == null) {
        return;
      }
      final payloadFreshness = parseTripLocationFreshness(
        payload['freshness'] as String?,
      );

      final location = TripLiveLocationRecord(
        latitude: latitude,
        longitude: longitude,
        freshness:
            payloadFreshness == TripLocationFreshness.unavailable
                ? TripLocationFreshness.live
                : payloadFreshness,
      );

      setState(() {
        _location = location;
        _errorMessage = null;
        _loading = false;
      });
      widget.onLocationChanged?.call(location);
      _fitCameraToContent();
    };

    _webSocketService.connect(
      authorizationHeader: buildAuthorizationHeader(
        accessToken: widget.accessToken,
        email: widget.email,
        senha: widget.senha,
      ),
      companyId: companyId,
    );
  }

  List<LatLng> _collectMapPoints() {
    return collectTripLiveMapPoints(
      location: _location,
      routeGeometry: widget.routeGeometry,
      focusMode: widget.focusMode,
    );
  }

  Future<void> _fitCameraToContent({bool force = false}) async {
    final controller = _mapController;
    if (controller == null) {
      return;
    }
    if (!force && !_cameraLockedToContent) {
      return;
    }

    final points = _collectMapPoints();
    if (points.isEmpty) {
      return;
    }

    if (points.length == 1) {
      await _runProgrammaticCameraMove(
        animate:
            () => controller.animateCamera(
              CameraUpdate.newCameraPosition(
                CameraPosition(target: points.first, zoom: 15.6),
              ),
            ),
        instant:
            () => controller.moveCamera(
              CameraUpdate.newCameraPosition(
                CameraPosition(target: points.first, zoom: 15.6),
              ),
            ),
      );
      return;
    }

    double minLat = points.first.latitude;
    double maxLat = points.first.latitude;
    double minLng = points.first.longitude;
    double maxLng = points.first.longitude;

    for (final point in points.skip(1)) {
      minLat = math.min(minLat, point.latitude);
      maxLat = math.max(maxLat, point.latitude);
      minLng = math.min(minLng, point.longitude);
      maxLng = math.max(maxLng, point.longitude);
    }

    await _runProgrammaticCameraMove(
      animate:
          () => controller.animateCamera(
            CameraUpdate.newLatLngBounds(
              LatLngBounds(
                southwest: LatLng(minLat, minLng),
                northeast: LatLng(maxLat, maxLng),
              ),
              88,
            ),
          ),
      instant:
          () => controller.moveCamera(
            CameraUpdate.newLatLngBounds(
              LatLngBounds(
                southwest: LatLng(minLat, minLng),
                northeast: LatLng(maxLat, maxLng),
              ),
              88,
            ),
          ),
    );
  }

  Future<void> _runProgrammaticCameraMove({
    required Future<void> Function() animate,
    required Future<void> Function() instant,
  }) async {
    _programmaticCameraMove = true;
    try {
      if (_motionPolicy.animatesCamera) {
        await animate();
        if (_motionPolicy.settleDelay > Duration.zero) {
          await Future<void>.delayed(_motionPolicy.settleDelay);
        }
      } else {
        await instant();
      }
    } finally {
      _programmaticCameraMove = false;
    }
  }

  void _markUserCameraInteraction() {
    if (_programmaticCameraMove || !_cameraLockedToContent) {
      return;
    }
    setState(() => _cameraLockedToContent = false);
  }

  Future<void> _recenterMap() async {
    setState(() => _cameraLockedToContent = true);
    await _fitCameraToContent(force: true);
  }

  Future<void> _stepZoom(double delta) async {
    final controller = _mapController;
    if (controller == null) {
      return;
    }
    final currentZoom = await controller.getZoomLevel();
    if (mounted) {
      setState(() => _cameraLockedToContent = false);
    } else {
      _cameraLockedToContent = false;
    }
    await _runProgrammaticCameraMove(
      animate:
          () => controller.animateCamera(
            CameraUpdate.zoomTo((currentZoom + delta).clamp(3.5, 18.5)),
          ),
      instant:
          () => controller.moveCamera(
            CameraUpdate.zoomTo((currentZoom + delta).clamp(3.5, 18.5)),
          ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.maybeOf(context);
    _motionPolicy = resolveTripLiveMapMotionPolicy(
      disableAnimations: mediaQuery?.disableAnimations ?? false,
      accessibleNavigation: mediaQuery?.accessibleNavigation ?? false,
    );

    final renderer = resolveTripLiveMapRenderer(
      renderMode: widget.renderMode,
      requestedRenderer: widget.renderer,
      mapLibreProofEnabled: widget.mapLibreProofEnabled,
      e2eMode: _e2eMode,
      isWeb: kIsWeb,
      googleApiKey: vanridesGooglePlacesApiKey,
    );
    final body = switch (renderer) {
      TripLiveMapRenderer.placeholder => _buildPlaceholderSurface(context),
      TripLiveMapRenderer.googleLegacy => _buildInteractiveSurface(context),
      TripLiveMapRenderer.mapLibreProof => _buildMapLibreProofSurface(context),
      TripLiveMapRenderer.deterministic => _buildDeterministicSurface(context),
    };

    return TripLiveMapFrame(
      height: widget.height,
      body: body,
      overlay: widget.overlay,
      showControls:
          widget.showControls && renderer == TripLiveMapRenderer.googleLegacy,
      cameraLockedToContent: _cameraLockedToContent,
      onRecenter: _recenterMap,
      onZoomIn: () => _stepZoom(1),
      onZoomOut: () => _stepZoom(-1),
    );
  }

  Widget _buildMapLibreProofSurface(BuildContext context) {
    if (_collectMapPoints().isEmpty) {
      return _buildPlaceholderSurface(context);
    }

    return VanRidesMapLibreProofSurface(
      scene: buildVanRidesMapRendererScene(
        routeGeometry: widget.routeGeometry,
        location: _location,
        trackingState: widget.trackingState,
        focusMode: widget.focusMode,
      ),
    );
  }

  Widget _buildInteractiveSurface(BuildContext context) {
    final mapPoints = _collectMapPoints();
    final initialTarget = mapPoints.isEmpty ? null : mapPoints.first;
    if (initialTarget == null) {
      return _buildPlaceholderSurface(context);
    }

    final routeLayers = buildVanRidesRouteLayers(
      routeGeometry: widget.routeGeometry,
      trackingState: widget.trackingState,
    );

    return FutureBuilder<VanRidesMapMarkerIconSet>(
      future: _markerIconsFuture,
      builder: (context, snapshot) {
        final markerIcons = snapshot.data;
        return GoogleMap(
          key: const Key('trip.live.map'),
          initialCameraPosition: CameraPosition(
            target: initialTarget,
            zoom: 15.2,
          ),
          style: vanRidesDarkGoogleMapStyle,
          zoomControlsEnabled: false,
          zoomGesturesEnabled: true,
          scrollGesturesEnabled: true,
          rotateGesturesEnabled: false,
          tiltGesturesEnabled: false,
          myLocationButtonEnabled: false,
          mapToolbarEnabled: false,
          compassEnabled: false,
          padding: widget.mapPadding,
          gestureRecognizers: {
            Factory<OneSequenceGestureRecognizer>(
              () => EagerGestureRecognizer(),
            ),
          },
          onCameraMoveStarted: _markUserCameraInteraction,
          markers:
              markerIcons == null
                  ? const <Marker>{}
                  : _buildMarkers(markerIcons),
          polylines: buildVanRidesRoutePolylines(routeLayers),
          onMapCreated: (controller) {
            _mapController = controller;
            _fitCameraToContent(force: true);
          },
        );
      },
    );
  }

  Widget _buildDeterministicSurface(BuildContext context) {
    if (_collectMapPoints().isEmpty) {
      return _buildPlaceholderSurface(context);
    }

    return VanRidesDeterministicMapSurface(
      routeLayers: buildVanRidesRouteLayers(
        routeGeometry: widget.routeGeometry,
        trackingState: widget.trackingState,
      ),
      routeGeometry: widget.routeGeometry,
      location: _location,
      focusMode: widget.focusMode,
      trackingState: widget.trackingState,
    );
  }

  Set<Marker> _buildMarkers(VanRidesMapMarkerIconSet markerIcons) {
    final markers = <Marker>{};
    final liveLocation = _location;
    if (liveLocation != null) {
      final freshness = _effectiveLocationFreshness(liveLocation);
      final spec = vanRidesVehicleMarkerSpec(freshness);
      markers.add(
        Marker(
          markerId: const MarkerId('motorista'),
          position: LatLng(liveLocation.latitude, liveLocation.longitude),
          anchor: const Offset(0.5, 0.5),
          infoWindow: InfoWindow(
            title: spec.semanticLabel,
            snippet: spec.stateLabel,
          ),
          icon: markerIcons.vehicleIconFor(freshness),
          zIndex: 12,
        ),
      );
    }

    final routeGeometry = widget.routeGeometry;
    final waypoints =
        routeGeometry == null
            ? const <TripRouteWaypointRecord>[]
            : visibleTripRouteWaypoints(routeGeometry, widget.focusMode);
    for (final waypoint in waypoints) {
      if (!waypoint.hasCoordinates) {
        continue;
      }
      if (waypoint.paradaDoPassageiro) {
        markers.add(_passengerMarker(waypoint, markerIcons));
      } else {
        markers.add(_stopMarker(waypoint, markerIcons));
      }
    }
    return markers;
  }

  Marker _passengerMarker(
    TripRouteWaypointRecord waypoint,
    VanRidesMapMarkerIconSet markerIcons,
  ) {
    final spec = vanRidesPassengerMarkerSpec(label: waypoint.nome);
    return Marker(
      markerId: MarkerId(_waypointMarkerId('passenger', waypoint)),
      position: LatLng(waypoint.latitude!, waypoint.longitude!),
      anchor: const Offset(0.5, 0.5),
      infoWindow: InfoWindow(
        title: spec.semanticLabel,
        snippet: spec.stateLabel,
      ),
      icon: markerIcons.passengerIcon(),
      zIndex: 10,
    );
  }

  Marker _stopMarker(
    TripRouteWaypointRecord waypoint,
    VanRidesMapMarkerIconSet markerIcons,
  ) {
    final state = vanRidesStopMarkerStateFor(waypoint, widget.trackingState);
    final spec = vanRidesStopMarkerSpec(
      state: state,
      order: waypoint.ordem,
      label: waypoint.nome,
    );
    return Marker(
      markerId: MarkerId(_waypointMarkerId('stop', waypoint)),
      position: LatLng(waypoint.latitude!, waypoint.longitude!),
      anchor: const Offset(0.5, 0.5),
      infoWindow: InfoWindow(
        title: spec.semanticLabel,
        snippet: spec.stateLabel,
      ),
      icon: markerIcons.stopIconFor(state),
      zIndex: state == VanRidesStopMarkerState.next ? 9 : 6,
    );
  }

  TripLocationFreshness _effectiveLocationFreshness(
    TripLiveLocationRecord liveLocation,
  ) {
    if (liveLocation.freshness != TripLocationFreshness.unavailable &&
        liveLocation.freshness != TripLocationFreshness.unknown) {
      return liveLocation.freshness;
    }
    final stateFreshness = widget.trackingState.locationFreshness;
    if (stateFreshness != TripLocationFreshness.unavailable &&
        stateFreshness != TripLocationFreshness.unknown) {
      return stateFreshness;
    }
    return liveLocation.freshness;
  }

  String _waypointMarkerId(String prefix, TripRouteWaypointRecord waypoint) {
    final id = waypoint.paradaId ?? waypoint.passageiroId ?? waypoint.ordem;
    return '$prefix-${id ?? waypoint.nome}-${waypoint.ordem ?? 0}';
  }

  Widget _buildPlaceholderSurface(BuildContext context) {
    return TripLiveMapPlaceholderSurface(
      content: TripLiveMapPlaceholderContent.from(
        loading: _loading,
        location: _location,
        routeGeometry: widget.routeGeometry,
        errorMessage: _errorMessage,
      ),
    );
  }
}
