import 'dart:async';

import 'package:flutter/material.dart';

import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_tab_scaffold.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/confirmar_presenca_screen.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/service_presence/data/service_presence_service.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/features/support/data/support_ticket_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_route_event_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_tracking_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/passenger_trip_selection.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_event.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_exception.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_shell.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/passenger_trip_details_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_account_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_bottom_nav.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_presence_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_trip_widgets.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import 'package:vanrides_frontend_dev/services/api_constants.dart';
import 'package:vanrides_frontend_dev/services/auth_headers.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';
import 'package:vanrides_frontend_dev/services/jwt_claims.dart' as jwt_claims;
import 'package:vanrides_frontend_dev/features/tracking/presentation/ver_localizacao_screen.dart';

class HomePassageiroScreen extends StatefulWidget {
  const HomePassageiroScreen({
    super.key,
    required this.nome,
    required this.idPassageiro,
    this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.trackingGateway,
    this.tripRouteEventGateway,
    this.liveLocationGateway,
    this.liveLocationWebSocketService,
    this.servicePresenceGateway,
    this.accountProfileGateway,
    this.initialTripId,
    this.mapRenderMode = TripLiveMapRenderMode.interactive,
  });

  final String nome;
  final int idPassageiro;
  final int? motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final TripTrackingGateway? trackingGateway;
  final TripRouteEventGateway? tripRouteEventGateway;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final ServicePresenceGateway? servicePresenceGateway;
  final AccountProfileGateway? accountProfileGateway;
  final int? initialTripId;
  final TripLiveMapRenderMode mapRenderMode;

  static const verLocalizacaoButtonKey = Key('passageiro.home.verLocalizacao');
  static const confirmarPresencaButtonKey = Key(
    'passageiro.home.confirmarPresenca',
  );
  static const agendaViagensKey = Key('passageiro.home.agendaViagens');

  static Key selecionarViagemDiaButtonKey(int tripId) =>
      Key('passageiro.home.agendaViagem.$tripId');

  @override
  State<HomePassageiroScreen> createState() => _HomePassageiroScreenState();
}

class _HomePassageiroScreenState extends State<HomePassageiroScreen> {
  late final TripTrackingGateway _trackingGateway;
  late final TripRouteEventGateway _tripRouteEventGateway;
  ServicePresenceGateway? _servicePresenceGateway;
  int _currentIndex = 0;
  bool _loading = true;
  bool _loadingPresenceServices = true;
  bool _processingPresenceService = false;
  String? _errorMessage;
  String? _presenceErrorMessage;
  TripTrackingRecord? _currentTrip;
  List<TripTrackingRecord> _dayTrips = const [];
  List<PassengerAvailableServiceRecord> _presenceServices = const [];
  TripOperationalAlertRecord? _latestOperationalAlert;
  bool _initialTripFallbackNotified = false;

  @override
  void initState() {
    super.initState();
    _trackingGateway =
        widget.trackingGateway ??
        TripTrackingService(
          email: widget.email,
          senha: widget.senha,
          accessToken: widget.accessToken,
        );
    _tripRouteEventGateway =
        widget.tripRouteEventGateway ??
        TripRouteEventService(apiBaseUrl: baseUrl);
    _servicePresenceGateway =
        widget.servicePresenceGateway ??
        (widget.trackingGateway == null
            ? ServicePresenceService(
              email: widget.email,
              senha: widget.senha,
              accessToken: widget.accessToken,
            )
            : null);
    _connectRouteEventsIfPossible();
    _loadTracking();
    if (_servicePresenceGateway == null) {
      _loadingPresenceServices = false;
    } else {
      _loadPresenceServices();
    }
  }

  @override
  void dispose() {
    _tripRouteEventGateway.disconnect();
    _tripRouteEventGateway.dispose();
    super.dispose();
  }

  Future<void> _loadTracking() async {
    setState(() {
      _loading = true;
      _errorMessage = null;
    });

    try {
      final trips = await _trackingGateway.fetchDayTrips(widget.idPassageiro);
      final previousTripId = _currentTrip?.id;
      final trip = selectRelevantPassengerTrip(
        trips,
        preferredTripId: _currentTrip?.id ?? widget.initialTripId,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _dayTrips = trips;
        _currentTrip = trip;
        if (trip?.id != previousTripId) {
          _latestOperationalAlert = null;
        }
        _loading = false;
      });
      _connectRouteEventsIfPossible(trip?.id);
      _notifyInitialTripFallbackIfNeeded();
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loading = false;
        _errorMessage =
            error is TripTrackingException
                ? error.message
                : AppStrings.passageiro.naoFoiPossivelCarregarViagemAtual;
      });
    }
  }

  Future<void> _loadPresenceServices() async {
    final gateway = _servicePresenceGateway;
    if (gateway == null) {
      return;
    }

    setState(() {
      _loadingPresenceServices = true;
      _presenceErrorMessage = null;
    });

    try {
      final services = await gateway.fetchAvailableServices(
        passageiroId: widget.idPassageiro,
        data: DateTime.now(),
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _presenceServices = services;
        _loadingPresenceServices = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _presenceServices = const [];
        _loadingPresenceServices = false;
        _presenceErrorMessage =
            error is ServicePresenceException
                ? error.message
                : AppStrings.passageiro.nenhumServicoLiberadoHoje;
      });
    }
  }

  int? get _effectiveMotoristaId =>
      _currentTrip?.motorista?.id ?? widget.motoristaId;

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

  void _connectRouteEventsIfPossible([int? tripId]) {
    _tripRouteEventGateway.disconnect();
    final companyId = jwt_claims.extractCompanyIdFromAccessToken(
      widget.accessToken,
    );
    if (companyId == null || tripId == null) {
      return;
    }

    _tripRouteEventGateway.connect(
      authorizationHeader: buildAuthorizationHeader(
        accessToken: widget.accessToken,
        email: widget.email,
        senha: widget.senha,
      ),
      companyId: companyId,
      tripId: tripId,
      onEvent: (event) {
        final currentTrip = _currentTrip;
        if (!mounted || currentTrip == null) {
          return;
        }
        if (event.viagemId != currentTrip.id) {
          return;
        }
        final alert = selectPassengerOperationalAlert(
          event,
          widget.idPassageiro,
        );
        setState(() => _latestOperationalAlert = alert);
        _loadTracking();
      },
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

  void _abrirMapa() {
    final trip = _currentTrip;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => VerLocalizacaoScreen(
              motoristaId: _effectiveMotoristaId ?? 0,
              email: widget.email,
              senha: widget.senha,
              accessToken: widget.accessToken,
              trip: trip,
              liveLocationGateway: widget.liveLocationGateway,
              liveLocationWebSocketService: widget.liveLocationWebSocketService,
              mapRenderMode: widget.mapRenderMode,
            ),
      ),
    );
  }

  void _selecionarViagemDoDia(TripTrackingRecord trip) {
    if (_currentTrip?.id == trip.id) {
      return;
    }
    setState(() {
      _currentTrip = trip;
      _latestOperationalAlert = null;
    });
    _connectRouteEventsIfPossible(trip.id);
  }

  void _abrirPresenca() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => ConfirmarPresencaScreen(
              idPassageiro: widget.idPassageiro,
              motoristaId: widget.motoristaId,
              email: widget.email,
              senha: widget.senha,
              accessToken: widget.accessToken,
            ),
      ),
    );
  }

  Future<void> _abrirDetalhesViagem() async {
    final trip = _currentTrip;
    if (trip == null) {
      return;
    }
    final action = await Navigator.push<PassengerTripDetailsAction>(
      context,
      MaterialPageRoute(builder: (_) => PassengerTripDetailsScreen(trip: trip)),
    );
    if (!mounted) {
      return;
    }
    if (action == PassengerTripDetailsAction.openSupport) {
      _selectHomeDestination(2);
    }
  }

  Future<void> _marcarPresencaServico(
    PassengerAvailableServiceRecord service,
    String status,
  ) async {
    final gateway = _servicePresenceGateway;
    if (gateway == null) {
      return;
    }

    setState(() => _processingPresenceService = true);
    try {
      await gateway.setPresenceStatus(
        service: service,
        passageiroId: widget.idPassageiro,
        status: status,
      );
      await _loadPresenceServices();
      _showPresenceMessage(
        status == 'CONFIRMED'
            ? 'Presença confirmada para ${service.nome}.'
            : 'Ausência registrada para ${service.nome}.',
      );
    } catch (error) {
      _showPresenceMessage(
        error is ServicePresenceException
            ? error.message
            : 'Não foi possível atualizar a presença do serviço.',
      );
    }
    if (mounted) {
      setState(() => _processingPresenceService = false);
    }
  }

  Future<void> _limparPresencaServico(
    PassengerAvailableServiceRecord service,
  ) async {
    final gateway = _servicePresenceGateway;
    final presencaId = service.presencaId;
    if (gateway == null || presencaId == null) {
      return;
    }

    setState(() => _processingPresenceService = true);
    try {
      await gateway.clearPresence(presencaId);
      await _loadPresenceServices();
      _showPresenceMessage('Decisão removida para ${service.nome}.');
    } catch (error) {
      _showPresenceMessage(
        error is ServicePresenceException
            ? error.message
            : 'Não foi possível limpar a decisão de presença.',
      );
    }
    if (mounted) {
      setState(() => _processingPresenceService = false);
    }
  }

  void _showPresenceMessage(String message) {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Widget _buildPassengerMapScene(TripTrackingRecord trip) {
    return PassengerHomeMapScene(
      trip: trip,
      dayTrips: _dayTrips,
      selectedTripId: _currentTrip?.id,
      effectiveMotoristaId: _effectiveMotoristaId ?? 0,
      email: widget.email,
      senha: widget.senha,
      accessToken: widget.accessToken,
      liveLocationGateway: widget.liveLocationGateway,
      liveLocationWebSocketService: widget.liveLocationWebSocketService,
      mapRenderMode: widget.mapRenderMode,
      onRefresh: () => _loadTracking(),
      onTripSelected: _selecionarViagemDoDia,
      onOpenTripDetails: _abrirDetalhesViagem,
      onOpenMap: _abrirMapa,
      onOpenPresence: _abrirPresenca,
      pendingPresenceCount: _pendingPresenceCount,
      operationalAlert: _latestOperationalAlert,
      selectedNavIndex: _currentIndex,
      onDestinationSelected: _selectHomeDestination,
      detailsButtonKey: HomePassageiroScreen.verLocalizacaoButtonKey,
      presenceButtonKey: HomePassageiroScreen.confirmarPresencaButtonKey,
      agendaKey: HomePassageiroScreen.agendaViagensKey,
      tripKeyBuilder: HomePassageiroScreen.selecionarViagemDiaButtonKey,
    );
  }

  int get _pendingPresenceCount {
    if (_loadingPresenceServices) {
      return 0;
    }
    return _presenceServices.where(_isPresenceDecisionPending).length;
  }

  bool _isPresenceDecisionPending(PassengerAvailableServiceRecord service) {
    final status = service.presencaStatus.trim().toUpperCase();
    return status != 'CONFIRMED' && status != 'ABSENT';
  }

  @override
  Widget build(BuildContext context) {
    final tripDrawsMapBehindDock =
        _currentTrip != null && !_loading && _errorMessage == null;
    final currentPageDrawsMapBehindDock =
        _currentIndex == 0 && tripDrawsMapBehindDock;
    final pages = [
      tripDrawsMapBehindDock
          ? _buildTripTab(context)
          : _withBottomDockClearance(_buildTripTab(context)),
      _withBottomDockClearance(SafeArea(child: _buildPresenceTab())),
      _withBottomDockClearance(
        PassengerHomeAccountTab(
          nome: widget.nome,
          email: widget.email,
          enabled: _currentIndex == 2,
          accountProfileGateway:
              widget.accountProfileGateway ??
              AccountProfileService(
                email: widget.email,
                senha: widget.senha,
                accessToken: widget.accessToken,
              ),
          supportTicketGateway: UserSupportTicketService(
            email: widget.email,
            senha: widget.senha,
            accessToken: widget.accessToken,
          ),
          trip: _currentTrip,
          dayTrips: _dayTrips,
          onLogout: () => _logout(context),
        ),
      ),
    ];

    return VanRidesLiquidTabScaffold(
      selectedIndex: _currentIndex,
      backgroundColor: MapFirstPalette.background,
      pages: pages,
      overlays: [
        if (!currentPageDrawsMapBehindDock)
          Align(
            alignment: Alignment.bottomCenter,
            child: PassengerHomeBottomNav(
              selectedIndex: _currentIndex,
              onDestinationSelected: _selectHomeDestination,
            ),
          ),
      ],
    );
  }

  Widget _withBottomDockClearance(Widget child) {
    return Padding(
      padding: const EdgeInsets.only(
        bottom: PassengerHomeBottomNav.overlayClearance,
      ),
      child: child,
    );
  }

  void _selectHomeDestination(int index) {
    setState(() => _currentIndex = index);
  }

  Widget _buildTripTab(BuildContext context) {
    final trip = _currentTrip;

    if (trip != null && !_loading && _errorMessage == null) {
      return _buildPassengerMapScene(trip);
    }

    if (_loading) {
      return const SafeArea(child: Center(child: CircularProgressIndicator()));
    }

    return RefreshIndicator(
      onRefresh: _loadTracking,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: [
          MapFirstEditorialStateView(
            icon:
                _errorMessage != null
                    ? Icons.wifi_off_rounded
                    : Icons.route_outlined,
            iconColor:
                _errorMessage != null
                    ? MapFirstPalette.red
                    : MapFirstPalette.brandBlue,
            title:
                _errorMessage != null
                    ? AppStrings.passageiro.networkError
                    : AppStrings.passageiro.semViagemHoje,
            message:
                _errorMessage ??
                AppStrings.passageiro.semViagemAutomaticaDescricao,
            primaryActionLabel: AppStrings.passageiro.atualizarStatus,
            onPrimaryAction: () => _loadTracking(),
            secondaryActionLabel:
                _errorMessage != null
                    ? AppStrings.passageiro.abrirPresenca
                    : null,
            onSecondaryAction: _errorMessage != null ? _abrirPresenca : null,
            issues:
                _errorMessage != null
                    ? [
                      AppStrings.passageiro.sinalDeRedeFracoOuInstavel,
                      AppStrings.passageiro.viagemNaoSincronizadaNoDispositivo,
                      AppStrings.passageiro.backendIndisponivelMomentaneamente,
                    ]
                    : null,
          ),
        ],
      ),
    );
  }

  Widget _buildPresenceTab() {
    return PassengerHomePresenceTab(
      trip: _currentTrip,
      dayTrips: _dayTrips,
      presenceServices: _presenceServices,
      loadingPresenceServices: _loadingPresenceServices,
      processingPresenceService: _processingPresenceService,
      presenceErrorMessage: _presenceErrorMessage,
      onTripSelected: _selecionarViagemDoDia,
      onConfirmService:
          (service) => _marcarPresencaServico(service, 'CONFIRMED'),
      onAbsentService: (service) => _marcarPresencaServico(service, 'ABSENT'),
      onClearService: _limparPresencaServico,
      onOpenPresence: _abrirPresenca,
      onOpenTripDetails: _abrirDetalhesViagem,
      agendaKey: HomePassageiroScreen.agendaViagensKey,
      tripKeyBuilder: HomePassageiroScreen.selecionarViagemDiaButtonKey,
    );
  }
}
