// ignore_for_file: unused_element, unused_element_parameter

import 'dart:async';

import 'package:flutter/material.dart';

import 'package:vanrides_frontend_dev/app/theme/vanrides_motion.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/data/driver_trip_service.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/motorista_home_ui_presenter.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/motorista_location_presenter.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/motorista_session_presenter.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_lifecycle_confirmation_dialogs.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_widgets.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_action_queue_store.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_source.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue_sync.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_navigation_launcher.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_route_event_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_exception.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_shell.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/presencas_hoje_screen.dart';
import 'package:vanrides_frontend_dev/services/location_gateway.dart';
import 'package:vanrides_frontend_dev/services/location_sharing_use_case.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';
import 'package:vanrides_frontend_dev/services/auth_headers.dart';
import 'package:vanrides_frontend_dev/services/api_constants.dart';

part 'home_motorista_screen_tabs.dart';
part 'home_motorista_lifecycle_actions.dart';
part 'home_motorista_navigation_action_queue.dart';

class HomeMotoristaScreen extends StatefulWidget {
  const HomeMotoristaScreen({
    super.key,
    required this.nome,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.tripGateway,
    this.tripRouteEventGateway,
    this.liveLocationGateway,
    this.liveLocationWebSocketService,
    this.navigationLauncher,
    this.locationGateway,
    this.locationSharingWebSocketService,
    this.accountProfileGateway,
    this.navigationActionQueueStore,
    this.navigationRoutePlanSource,
    this.initialTripId,
    this.mapRenderMode = TripLiveMapRenderMode.interactive,
  });

  final String nome;
  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final DriverTripGateway? tripGateway;
  final TripRouteEventGateway? tripRouteEventGateway;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final TripNavigationLauncher? navigationLauncher;
  final LocationGateway? locationGateway;
  final WebSocketServiceBase? locationSharingWebSocketService;
  final AccountProfileGateway? accountProfileGateway;
  final DriverNavigationActionQueueStore? navigationActionQueueStore;
  final DriverNavigationRoutePlanSnapshotSource? navigationRoutePlanSource;
  final int? initialTripId;
  final TripLiveMapRenderMode mapRenderMode;

  static const compartilharLocalizacaoButtonKey = Key(
    'motorista.home.compartilharLocalizacao',
  );
  static const verPresencasHojeButtonKey = Key('motorista.home.presencasHoje');
  static const verDetalhesButtonKey = Key('motorista.home.verDetalhes');
  static const abrirMapaCompletoButtonKey = Key(
    'motorista.home.abrirMapaCompleto',
  );
  static const editarRotaButtonKey = Key('motorista.home.editarRota');
  static const salvarRotaButtonKey = Key('motorista.home.salvarRota');
  static const cancelarEdicaoRotaButtonKey = Key(
    'motorista.home.cancelarEdicaoRota',
  );
  static const seguirNavegacaoButtonKey = Key('motorista.home.seguirNavegacao');
  static const iniciarCorridaButtonKey = Key('motorista.home.iniciarCorrida');
  static const concluirParadaButtonKey = Key('motorista.home.concluirParada');
  static const pularParadaButtonKey = Key('motorista.home.pularParada');
  static const noShowParadaButtonKey = Key('motorista.home.noShowParada');
  static const encerrarCorridaButtonKey = Key('motorista.home.encerrarCorrida');
  static const atualizarButtonKey = Key('motorista.home.atualizar');
  static const agendaViagensKey = Key('motorista.home.agendaViagens');

  static Key selecionarViagemDiaButtonKey(int tripId) =>
      Key('motorista.home.agendaViagem.$tripId');

  @override
  State<HomeMotoristaScreen> createState() => _HomeMotoristaScreenState();
}

class _HomeMotoristaScreenState extends State<HomeMotoristaScreen>
    with WidgetsBindingObserver {
  late final DriverTripGateway _tripGateway;
  late final TripRouteEventGateway _tripRouteEventGateway;
  late final TripNavigationLauncher _navigationLauncher;
  late final LocationGateway _locationGateway;
  late final MotoristaSessionPresenter _sessionPresenter;
  late final MotoristaLocationPresenter _locationPresenter;
  late final MotoristaHomeUiPresenter _uiPresenter;
  late final DriverNavigationActionQueueStore _navigationActionQueueStore;
  late final DriverNavigationRoutePlanSnapshotSource _navigationRoutePlanSource;
  final DriverNavigationActionQueueSync _navigationActionQueueSync =
      const DriverNavigationActionQueueSync();
  late final Listenable _screenListenable;

  final ValueNotifier<DriverNavigationActionQueue> _navigationActionQueue =
      ValueNotifier(const DriverNavigationActionQueue.empty());
  bool _tripRefreshedFromLiveAnchor = false;
  bool _initialTripFallbackNotified = false;
  String? _lastLocationError;
  bool _lastWasSharingLocation = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _tripGateway =
        widget.tripGateway ??
        DriverTripService(
          email: widget.email,
          senha: widget.senha,
          accessToken: widget.accessToken,
        );
    _tripRouteEventGateway =
        widget.tripRouteEventGateway ??
        TripRouteEventService(apiBaseUrl: baseUrl);
    _navigationLauncher =
        widget.navigationLauncher ?? const ExternalTripNavigationLauncher();
    _sessionPresenter = MotoristaSessionPresenter(tripGateway: _tripGateway);
    _locationGateway = widget.locationGateway ?? GeolocatorLocationGateway();
    final locationSharingWebSocketService =
        widget.locationSharingWebSocketService ?? WebSocketService();
    _locationPresenter = MotoristaLocationPresenter(
      webSocketService: locationSharingWebSocketService,
      locationSharingUseCase: LocationSharingUseCase(gateway: _locationGateway),
      authorizationHeader: buildAuthorizationHeader(
        accessToken: widget.accessToken,
        email: widget.email,
        senha: widget.senha,
      ),
    );
    _uiPresenter = MotoristaHomeUiPresenter();
    _navigationActionQueueStore =
        widget.navigationActionQueueStore ??
        SharedPreferencesDriverNavigationActionQueueStore();
    _navigationRoutePlanSource =
        widget.navigationRoutePlanSource ??
        const DriverTripNavigationRoutePlanSnapshotSource();
    _screenListenable = Listenable.merge([
      _sessionPresenter,
      _locationPresenter,
      _uiPresenter,
      _navigationActionQueue,
    ]);
    _locationPresenter.addListener(_handleLocationPresenterChanged);
    unawaited(_loadNavigationActionQueue(syncAfterLoad: true));
    _loadTrip();
  }

  MotoristaSessionState get _sessionState => _sessionPresenter.state;
  MotoristaHomeUiState get _uiState => _uiPresenter.state;
  bool get _loading => _sessionState.status == MotoristaSessionStatus.loading;
  String? get _errorMessage => _sessionState.errorMessage;
  DriverTripRecord? get _trip => _sessionState.trip;
  List<DriverTripRecord> get _dayTrips => _sessionState.dayTrips;
  List<DriverTripStopRecord> get _editableStops => _sessionState.editableStops;
  bool get _savingRoute => _uiState.savingRoute;
  bool get _openingNavigation => _uiState.openingNavigation;
  bool get _runningLifecycleAction => _uiState.runningLifecycleAction;
  bool get _editingRoute => _uiState.editingRoute;
  int get _currentTabIndex => _uiState.currentTabIndex;
  DriverStopFilter get _stopFilter => _uiState.stopFilter;
  bool get _sharingLocation => _locationPresenter.isSharing;
  TripLiveLocationRecord? get _driverLiveLocation =>
      _locationPresenter.latestLocation;
  DriverNavigationActionQueueSummary? get _navigationActionQueueSummary =>
      _navigationActionQueue.value.pendingSummary;

  void _handleLocationPresenterChanged() {
    final locationError = _locationPresenter.locationError;
    final isSharing = _locationPresenter.isSharing;
    final shouldShowLocationError =
        locationError != null &&
        locationError != _lastLocationError &&
        _lastWasSharingLocation &&
        !isSharing;
    _lastLocationError = locationError;
    _lastWasSharingLocation = isSharing;

    if (shouldShowLocationError && mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(locationError)));
    }

    if (!_tripRefreshedFromLiveAnchor &&
        _driverLiveLocation != null &&
        _trip?.status == TripTrackingStatus.inProgress) {
      _tripRefreshedFromLiveAnchor = true;
      unawaited(_refreshTripAfterLiveAnchor());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _tripRouteEventGateway.dispose();
    _sessionPresenter.disconnectRouteEvents();
    _sessionPresenter.dispose();
    _locationPresenter.removeListener(_handleLocationPresenterChanged);
    _locationPresenter.dispose();
    _uiPresenter.dispose();
    _navigationActionQueue.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _handleNavigationActionQueueLifecycleStateChanged(state);
  }

  Future<void> _loadTrip() async {
    final trip = await _sessionPresenter.loadTrip(
      widget.motoristaId,
      preferredTripId: _trip?.id ?? widget.initialTripId,
    );
    if (!mounted) {
      return;
    }
    _wireRouteEventsIfPossible(trip?.id);
    _notifyInitialTripFallbackIfNeeded();
  }

  Future<void> _refreshTripAfterLiveAnchor() async {
    final trip = await _sessionPresenter.refreshTripSilently(
      widget.motoristaId,
    );
    if (!mounted || trip == null) {
      return;
    }
    _wireRouteEventsIfPossible(trip.id);
  }

  void _selecionarViagemDoDia(DriverTripRecord trip) {
    if (_trip?.id == trip.id) {
      return;
    }
    _uiPresenter.finishRouteEditing();
    _sessionPresenter.selectTrip(trip);
    _wireRouteEventsIfPossible(trip.id);
  }

  void _wireRouteEventsIfPossible(int? tripId) {
    final companyId = extractCompanyIdFromAccessToken(widget.accessToken);
    if (companyId == null || tripId == null) {
      _sessionPresenter.disconnectRouteEvents();
      return;
    }

    _sessionPresenter.connectRouteEvents(
      _tripRouteEventGateway,
      companyId,
      buildAuthorizationHeader(
        accessToken: widget.accessToken,
        email: widget.email,
        senha: widget.senha,
      ),
    );
  }

  void _notifyInitialTripFallbackIfNeeded() {
    final initialTripId = widget.initialTripId;
    if (_initialTripFallbackNotified || initialTripId == null) {
      return;
    }
    final found = _dayTrips.any((trip) => trip.id == initialTripId);
    if (found) {
      return;
    }
    _initialTripFallbackNotified = true;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(AppStrings.deepLink.viagemNaoEncontrada)),
    );
  }

  Future<bool> _startLiveSharing({bool openSettingsIfNeeded = true}) async {
    if (_sharingLocation) {
      return true;
    }

    final companyId = extractCompanyIdFromAccessToken(widget.accessToken);
    if (companyId == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppStrings.motorista.unableIdentifyCompanyForLiveSignal,
            ),
          ),
        );
      }
      return false;
    }

    final sharingStarted = await _locationPresenter.startSharing(
      widget.motoristaId,
      companyId,
    );
    final permissionResult = _locationPresenter.lastPermissionResult;
    if (permissionResult is LocationSharingServiceDisabled) {
      if (openSettingsIfNeeded) {
        await _locationGateway.openLocationSettings();
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppStrings.motorista.enableLocationToStartLiveSignal),
          ),
        );
      }
      return false;
    }
    if (permissionResult is LocationSharingPermissionDenied) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppStrings.motorista.locationPermissionDeniedLiveMap),
          ),
        );
      }
      return false;
    }
    if (permissionResult is LocationSharingPermissionDeniedForever) {
      if (openSettingsIfNeeded) {
        await _locationGateway.openAppSettings();
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppStrings.motorista.locationPermissionBlockedOpenSettings,
            ),
          ),
        );
      }
      return false;
    }
    if (permissionResult is LocationSharingPermissionUnableToDetermine) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppStrings.motorista.unableDetermineLocationPermission,
            ),
          ),
        );
      }
      return false;
    }
    if (!sharingStarted) {
      return false;
    }

    if (permissionResult is LocationSharingPermissionGranted &&
        !permissionResult.allowBackground &&
        mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppStrings.motorista.liveSignalActiveForegroundOnly),
        ),
      );
    }

    return true;
  }

  Future<void> _stopLiveSharing() async {
    await _locationPresenter.stopSharing();
  }

  void _iniciarEdicaoDaRota() {
    final trip = _trip;
    if (trip == null) {
      return;
    }

    _sessionPresenter.startRouteEditing();
    _uiPresenter.startRouteEditing();
  }

  void _cancelarEdicaoDaRota() {
    _sessionPresenter.resetEditableStopsFromTrip();
    _uiPresenter.finishRouteEditing();
  }

  void _reordenarParadas(int oldIndex, int newIndex) {
    _sessionPresenter.reorderEditableStops(oldIndex, newIndex);
  }

  Future<void> _salvarRotaDoDia() async {
    final trip = _trip;
    if (trip == null || _editableStops.isEmpty) {
      return;
    }

    _uiPresenter.setSavingRoute(true);

    try {
      final updatedTrip = await _tripGateway.reorderTrip(
        trip.id,
        _editableStops.map((item) => item.id).toList(growable: false),
      );

      if (!mounted) {
        return;
      }

      _sessionPresenter.syncTripSnapshot(updatedTrip);
      _uiPresenter.finishRouteEditing();
      _uiPresenter.setSavingRoute(false);

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            AppStrings.motorista.routeSavedAs(
              presentRouteVersionLabel(updatedTrip.rotaVersao),
            ),
          ),
        ),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      _uiPresenter.setSavingRoute(false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            error is TripTrackingException
                ? error.message
                : AppStrings.motorista.unableSaveRouteOfDay,
          ),
        ),
      );
    }
  }

  Future<void> _logout(BuildContext context) async {
    await AppSessionStore().clear();
    if (!context.mounted) {
      return;
    }
    unawaited(
      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
        (route) => false,
      ),
    );
  }

  void _abrirPresencasHoje() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => PresencasHojeScreen(
              motoristaId: widget.motoristaId,
              email: widget.email,
              senha: widget.senha,
              accessToken: widget.accessToken,
            ),
      ),
    );
  }

  Future<void> _abrirNavegacaoExterna() async {
    final trip = _trip;
    final navigationUrl =
        trip?.rotaGeografica?.navegacaoPreferencial?.url ?? '';
    if (navigationUrl.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.motorista.navigationNotReadyForTrip)),
      );
      return;
    }

    _uiPresenter.setOpeningNavigation(true);
    try {
      final opened = await _navigationLauncher.open(navigationUrl);
      if (!mounted) {
        return;
      }
      if (!opened) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppStrings.motorista.unableOpenMapsOnDevice)),
        );
      }
    } finally {
      if (mounted) {
        _uiPresenter.setOpeningNavigation(false);
      }
    }
  }

  List<DriverTripStopRecord> get _filteredStops {
    final trip = _trip;
    if (trip == null) {
      return const [];
    }

    if (_editingRoute) {
      return _editableStops;
    }

    switch (_stopFilter) {
      case DriverStopFilter.pending:
        return trip.paradas.where((item) => !item.confirmadoPresenca).toList();
      case DriverStopFilter.confirmed:
        return trip.paradas.where((item) => item.confirmadoPresenca).toList();
      case DriverStopFilter.all:
        return trip.paradas;
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _screenListenable,
      builder: (context, _) {
        final motion = VanRidesMotionPolicy.of(context);
        final trip = _trip;
        final routePlanResponse =
            trip == null
                ? null
                : _navigationRoutePlanSource.snapshotForTrip(trip);
        final pages = [
          DriverHomeTripTab(
            isLoading: _loading,
            errorMessage: _errorMessage,
            trip: trip,
            motoristaId: widget.motoristaId,
            email: widget.email,
            senha: widget.senha,
            accessToken: widget.accessToken,
            liveLocationGateway: widget.liveLocationGateway,
            liveLocationWebSocketService: widget.liveLocationWebSocketService,
            mapRenderMode: widget.mapRenderMode,
            driverLiveLocation: _driverLiveLocation,
            sharingLocation: _sharingLocation,
            editingRoute: _editingRoute,
            dayTrips: _dayTrips,
            selectedTripId: trip?.id,
            editableStops: _editableStops,
            savingRoute: _savingRoute,
            openingNavigation: _openingNavigation,
            runningLifecycleAction: _runningLifecycleAction,
            navigationActionQueueSummary: _navigationActionQueueSummary,
            routePlanResponse: routePlanResponse,
            refreshKey: HomeMotoristaScreen.atualizarButtonKey,
            agendaKey: HomeMotoristaScreen.agendaViagensKey,
            tripKeyBuilder: HomeMotoristaScreen.selecionarViagemDiaButtonKey,
            startTripKey: HomeMotoristaScreen.iniciarCorridaButtonKey,
            detailsKey: HomeMotoristaScreen.verDetalhesButtonKey,
            mapKey: HomeMotoristaScreen.abrirMapaCompletoButtonKey,
            editRouteKey: HomeMotoristaScreen.editarRotaButtonKey,
            saveRouteKey: HomeMotoristaScreen.salvarRotaButtonKey,
            cancelRouteKey: HomeMotoristaScreen.cancelarEdicaoRotaButtonKey,
            completeStopKey: HomeMotoristaScreen.concluirParadaButtonKey,
            navigationKey: HomeMotoristaScreen.seguirNavegacaoButtonKey,
            skipStopKey: HomeMotoristaScreen.pularParadaButtonKey,
            noShowKey: HomeMotoristaScreen.noShowParadaButtonKey,
            endTripKey: HomeMotoristaScreen.encerrarCorridaButtonKey,
            onRefresh: _loadTrip,
            onSelectTrip: _selecionarViagemDoDia,
            onReorderStops: _reordenarParadas,
            onSaveRoute: _salvarRotaDoDia,
            onCancelRoute: _cancelarEdicaoDaRota,
            onStartTrip: _iniciarCorrida,
            onOpenStops: () => _uiPresenter.selectTab(2),
            onOpenMap: () => _uiPresenter.selectTab(1),
            onEditRoute: _iniciarEdicaoDaRota,
            onCompleteStop: _concluirProximaParada,
            onOpenNavigation: _abrirNavegacaoExterna,
            onSkipStop: _pularProximaParada,
            onNoShowStop: _marcarNoShowProximaParada,
            onRetryQueuedActions:
                _navigationActionQueue.value.pendingCount == 0
                    ? null
                    : _retryNavigationActionQueue,
            onEndTrip: _encerrarCorrida,
          ),
          _buildMapTab(context, trip),
          _buildStopsTab(context, trip),
          _buildAccountTab(context),
        ];

        return Scaffold(
          backgroundColor: MapFirstPalette.background,
          body: AnimatedSwitcher(
            duration: motion.duration(VanRidesMotionTokens.driverTabSwitch),
            switchInCurve: motion.curve(VanRidesMotionTokens.easeOutQuart),
            switchOutCurve: motion.curve(VanRidesMotionTokens.easeOutQuart),
            child: KeyedSubtree(
              key: ValueKey(_currentTabIndex),
              child: pages[_currentTabIndex],
            ),
          ),
          bottomNavigationBar: MapFirstBottomNavBar(
            selectedIndex: _currentTabIndex,
            onDestinationSelected: _uiPresenter.selectTab,
            destinations: [
              MapFirstNavDestination(
                icon: Icons.home_outlined,
                selectedIcon: Icons.home,
                label: AppStrings.motorista.inicio,
              ),
              MapFirstNavDestination(
                icon: Icons.map_outlined,
                selectedIcon: Icons.map,
                label: AppStrings.motorista.mapa,
              ),
              MapFirstNavDestination(
                icon: Icons.list_alt_outlined,
                selectedIcon: Icons.list_alt,
                label: AppStrings.motorista.paradas,
              ),
              MapFirstNavDestination(
                icon: Icons.person_outline,
                selectedIcon: Icons.person,
                label: AppStrings.motorista.conta,
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _abrirPainelDetalhes(DriverTripRecord trip) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        Future<void> closeAndRun(FutureOr<void> Function() action) async {
          Navigator.of(context).pop();
          await action();
        }

        return AnimatedBuilder(
          animation: _uiPresenter,
          builder: (context, _) {
            return DriverTripDetailPanel(
              trip: trip,
              editingRoute: _editingRoute,
              editableStops: _editableStops,
              filteredStops: _filteredStops,
              stopFilter: _stopFilter,
              onStopFilterSelected: _uiPresenter.selectStopFilter,
              actionKeys: const DriverTripDetailActionKeys(
                maps: HomeMotoristaScreen.seguirNavegacaoButtonKey,
                presence: HomeMotoristaScreen.verPresencasHojeButtonKey,
                editRoute: HomeMotoristaScreen.editarRotaButtonKey,
                skipStop: HomeMotoristaScreen.pularParadaButtonKey,
                noShowStop: HomeMotoristaScreen.noShowParadaButtonKey,
                endTrip: HomeMotoristaScreen.encerrarCorridaButtonKey,
              ),
              openingNavigation: _openingNavigation,
              runningLifecycleAction: _runningLifecycleAction,
              onOpenMaps:
                  _openingNavigation
                      ? null
                      : () {
                        unawaited(closeAndRun(_abrirNavegacaoExterna));
                      },
              onOpenPresence: () {
                unawaited(
                  closeAndRun(() async {
                    _abrirPresencasHoje();
                  }),
                );
              },
              onEditRoute:
                  trip.status != TripTrackingStatus.inProgress
                      ? () {
                        unawaited(
                          closeAndRun(() async {
                            _iniciarEdicaoDaRota();
                          }),
                        );
                      }
                      : null,
              onSkipStop:
                  trip.status == TripTrackingStatus.inProgress &&
                          !_runningLifecycleAction &&
                          trip.proximaParada != null
                      ? () {
                        unawaited(closeAndRun(_pularProximaParada));
                      }
                      : null,
              onNoShowStop:
                  trip.status == TripTrackingStatus.inProgress &&
                          !_runningLifecycleAction &&
                          trip.proximaParada != null
                      ? () {
                        unawaited(closeAndRun(_marcarNoShowProximaParada));
                      }
                      : null,
              onEndTrip:
                  trip.status == TripTrackingStatus.inProgress &&
                          !_runningLifecycleAction
                      ? () {
                        unawaited(closeAndRun(_encerrarCorrida));
                      }
                      : null,
            );
          },
        );
      },
    );
  }
}
