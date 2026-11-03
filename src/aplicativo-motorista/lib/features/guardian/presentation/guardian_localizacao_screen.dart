import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../../../services/auth_headers.dart';
import '../../../services/api_constants.dart';
import '../../../services/websocket_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/ver_localizacao_screen.dart';
import '../../tracking/data/trip_tracking_service.dart';
import '../../tracking/data/trip_route_event_service.dart';
import '../../tracking/domain/trip_tracking_exception.dart';
import '../../tracking/domain/trip_tracking_record.dart';
import '../../tracking/presentation/map_first_trip_tracking_view.dart';
import '../../tracking/presentation/trip_live_map_panel.dart';
import '../../tracking/presentation/trip_tracking_presenter.dart';
import '../../tracking/presentation/widgets/passenger_map_footer_sheet_scaffold.dart';
import '../data/guardian_localizacao_service.dart';
import '../data/guardian_passageiro_service.dart';
import '../domain/guardian_experience_exception.dart';
import '../domain/guardian_linked_passageiro_record.dart';
import '../domain/guardian_localizacao_snapshot.dart';
import '../domain/guardian_session.dart';
import 'widgets/guardian_localizacao_fallback_view.dart';
import 'widgets/guardian_map_footer_nav.dart';
import 'widgets/guardian_map_footer_sheet_widgets.dart';
import 'widgets/guardian_localizacao_widgets.dart';

class GuardianLocalizacaoScreen extends StatefulWidget {
  const GuardianLocalizacaoScreen({
    super.key,
    required this.session,
    this.initialPassageiroId,
    this.initialTripId,
    this.passageiroGateway,
    this.localizacaoGateway,
    this.trackingGateway,
    this.tripRouteEventGateway,
    this.webSocketService,
    this.selectedNavIndex = 2,
    this.onDestinationSelected,
  });

  final GuardianSession session;
  final int? initialPassageiroId;
  final int? initialTripId;
  final GuardianPassageiroGateway? passageiroGateway;
  final GuardianLocalizacaoGateway? localizacaoGateway;
  final TripTrackingGateway? trackingGateway;
  final TripRouteEventGateway? tripRouteEventGateway;
  final WebSocketServiceBase? webSocketService;
  final int selectedNavIndex;
  final ValueChanged<int>? onDestinationSelected;

  static const abrirMapaButtonKey = Key('guardian.localizacao.abrir-mapa');
  static const agendaViagensKey = Key('guardian.localizacao.agendaViagens');

  static Key selecionarViagemDiaButtonKey(int tripId) =>
      Key('guardian.localizacao.agendaViagem.$tripId');

  @override
  State<GuardianLocalizacaoScreen> createState() =>
      _GuardianLocalizacaoScreenState();
}

class _GuardianLocalizacaoScreenState extends State<GuardianLocalizacaoScreen> {
  late final GuardianPassageiroGateway _passageiroGateway;
  late final GuardianLocalizacaoGateway _localizacaoGateway;
  late final TripTrackingGateway _trackingGateway;
  late final TripRouteEventGateway _tripRouteEventGateway;
  late final WebSocketServiceBase _webSocketService;

  bool _loadingPassageiros = true;
  bool _loadingTracking = false;
  String? _passageirosError;
  String? _trackingError;
  List<GuardianLinkedPassageiroRecord> _passageiros = const [];
  GuardianLinkedPassageiroRecord? _selectedPassageiro;
  TripTrackingRecord? _currentTrip;
  List<TripTrackingRecord> _dayTrips = const [];
  List<GuardianLocalizacaoSnapshot> _historico = const [];
  TripLiveMapFocusMode _focusMode = TripLiveMapFocusMode.passengerJourney;

  @override
  void initState() {
    super.initState();
    _passageiroGateway =
        widget.passageiroGateway ??
        GuardianPassageiroService(session: widget.session);
    _localizacaoGateway =
        widget.localizacaoGateway ??
        GuardianLocalizacaoService(session: widget.session);
    _trackingGateway =
        widget.trackingGateway ??
        TripTrackingService(
          email: widget.session.email,
          senha: '',
          accessToken: widget.session.accessToken,
        );
    _tripRouteEventGateway =
        widget.tripRouteEventGateway ??
        TripRouteEventService(apiBaseUrl: baseUrl);
    _webSocketService = widget.webSocketService ?? WebSocketService();
    _loadPassageiros();
  }

  @override
  void dispose() {
    _tripRouteEventGateway.disconnect();
    _tripRouteEventGateway.dispose();
    _webSocketService.disconnect();
    _webSocketService.dispose();
    super.dispose();
  }

  void _connectRouteEvents(int? tripId) {
    _tripRouteEventGateway.disconnect();
    if (tripId == null) {
      return;
    }

    _tripRouteEventGateway.connect(
      authorizationHeader: buildAuthorizationHeader(
        accessToken: widget.session.accessToken,
        email: widget.session.email,
        senha: '',
      ),
      companyId: widget.session.companyId,
      tripId: tripId,
      onEvent: (event) {
        final currentTrip = _currentTrip;
        if (!mounted || currentTrip == null) {
          return;
        }
        if (event.viagemId != currentTrip.id) {
          return;
        }
        _loadTracking();
      },
    );
  }

  Future<void> _loadPassageiros() async {
    setState(() {
      _loadingPassageiros = true;
      _passageirosError = null;
    });

    try {
      final passageiros = await _passageiroGateway.fetchLinkedPassageiros();
      if (!mounted) {
        return;
      }

      final selected = _resolveSelectedPassageiro(passageiros);

      setState(() {
        _passageiros = passageiros;
        _selectedPassageiro = selected;
        _loadingPassageiros = false;
      });

      await _loadTracking(preferredTripId: widget.initialTripId);
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loadingPassageiros = false;
        _passageirosError =
            error is GuardianExperienceException
                ? error.message
                : AppStrings
                    .guardian
                    .naoFoiPossivelCarregarPassageirosVinculados;
      });
    }
  }

  GuardianLinkedPassageiroRecord? _resolveSelectedPassageiro(
    List<GuardianLinkedPassageiroRecord> passageiros,
  ) {
    if (passageiros.isEmpty) {
      return null;
    }

    final initialPassageiroId = widget.initialPassageiroId;
    if (initialPassageiroId != null) {
      for (final passageiro in passageiros) {
        if (passageiro.id == initialPassageiroId) {
          return passageiro;
        }
      }
    }

    return passageiros.first;
  }

  Future<void> _loadTracking({int? preferredTripId}) async {
    final passageiro = _selectedPassageiro;
    _webSocketService.disconnect();

    if (passageiro == null) {
      setState(() {
        _loadingTracking = false;
        _trackingError = null;
        _currentTrip = null;
        _dayTrips = const [];
        _historico = const [];
      });
      return;
    }

    setState(() {
      _loadingTracking = true;
      _trackingError = null;
    });

    try {
      final trips = await _trackingGateway.fetchDayTrips(passageiro.id);
      var resolvedPassageiro = passageiro;
      var resolvedTrips = trips;
      var trip = _selectRelevantTrip(
        trips,
        preferredTripId: preferredTripId ?? _currentTrip?.id,
      );

      if (preferredTripId != null && trip?.id != preferredTripId) {
        final preferred = await _findPreferredTripAcrossPassageiros(
          preferredTripId,
          skipPassageiroId: passageiro.id,
        );
        if (preferred != null) {
          resolvedPassageiro = preferred.passageiro;
          resolvedTrips = preferred.trips;
          trip = preferred.trip;
        }
      }

      List<GuardianLocalizacaoSnapshot> historico = const [];
      final motoristaId = trip?.motorista?.id;

      if (motoristaId != null) {
        historico = await _localizacaoGateway.fetchHistoricoPorMotorista(
          motoristaId,
        );
      }

      if (!mounted) {
        return;
      }

      setState(() {
        _selectedPassageiro = resolvedPassageiro;
        _dayTrips = resolvedTrips;
        _currentTrip = trip;
        _historico = historico;
        _loadingTracking = false;
        _focusMode = TripLiveMapFocusMode.passengerJourney;
      });
      _connectRouteEvents(trip?.id);

      if (motoristaId != null) {
        _connectWebSocket(motoristaId);
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loadingTracking = false;
        _currentTrip = null;
        _dayTrips = const [];
        _historico = const [];
        _trackingError = switch (error) {
          TripTrackingException() => error.message,
          GuardianExperienceException() => error.message,
          _ => AppStrings.guardian.naoFoiPossivelCarregarRastreio,
        };
      });
    }
  }

  Future<
    ({
      GuardianLinkedPassageiroRecord passageiro,
      List<TripTrackingRecord> trips,
      TripTrackingRecord trip,
    })?
  >
  _findPreferredTripAcrossPassageiros(
    int tripId, {
    required int skipPassageiroId,
  }) async {
    for (final passageiro in _passageiros) {
      if (passageiro.id == skipPassageiroId) {
        continue;
      }

      final trips = await _trackingGateway.fetchDayTrips(passageiro.id);
      for (final trip in trips) {
        if (trip.id == tripId) {
          return (passageiro: passageiro, trips: trips, trip: trip);
        }
      }
    }

    return null;
  }

  TripTrackingRecord? _selectRelevantTrip(
    List<TripTrackingRecord> trips, {
    int? preferredTripId,
  }) {
    if (trips.isEmpty) {
      return null;
    }

    if (preferredTripId != null) {
      for (final trip in trips) {
        if (trip.id == preferredTripId) {
          return trip;
        }
      }
    }

    for (final trip in trips) {
      if (trip.status == TripTrackingStatus.inProgress) {
        return trip;
      }
    }

    for (final trip in trips) {
      if (trip.status == TripTrackingStatus.planned) {
        return trip;
      }
    }

    for (final trip in trips.reversed) {
      if (trip.status == TripTrackingStatus.completed) {
        return trip;
      }
    }

    for (final trip in trips) {
      if (trip.status != TripTrackingStatus.canceled) {
        return trip;
      }
    }

    return trips.first;
  }

  Future<void> _selecionarViagemDoDia(TripTrackingRecord trip) async {
    if (_currentTrip?.id == trip.id) {
      return;
    }

    _webSocketService.disconnect();
    setState(() {
      _currentTrip = trip;
      _historico = const [];
      _trackingError = null;
      _loadingTracking = true;
    });

    try {
      List<GuardianLocalizacaoSnapshot> historico = const [];
      final motoristaId = trip.motorista?.id;
      if (motoristaId != null) {
        historico = await _localizacaoGateway.fetchHistoricoPorMotorista(
          motoristaId,
        );
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _currentTrip = trip;
        _historico = historico;
        _loadingTracking = false;
        _focusMode = TripLiveMapFocusMode.passengerJourney;
      });
      _connectRouteEvents(trip.id);
      if (motoristaId != null) {
        _connectWebSocket(motoristaId);
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loadingTracking = false;
        _historico = const [];
        _trackingError = switch (error) {
          TripTrackingException() => error.message,
          GuardianExperienceException() => error.message,
          _ => AppStrings.guardian.naoFoiPossivelCarregarRastreio,
        };
      });
    }
  }

  void _selectPassageiro(GuardianLinkedPassageiroRecord passageiro) {
    setState(() {
      _selectedPassageiro = passageiro;
      _currentTrip = null;
      _dayTrips = const [];
      _historico = const [];
      _focusMode = TripLiveMapFocusMode.passengerJourney;
    });
    _loadTracking();
  }

  Widget _buildGuardianMapScene(
    BuildContext context,
    GuardianLinkedPassageiroRecord passageiro,
    TripTrackingRecord trip,
  ) {
    final motoristaId = trip.motorista?.id;
    final effectiveMotoristaId = motoristaId ?? 0;

    return PassengerMapFooterSheetScaffold(
      topBar: GuardianMapStageTopLayer(
        selectedPassageiroName: passageiro.nome,
        focusMode: _focusMode,
        onRefresh: _loadPassageiros,
        onFocusVan:
            () => setState(() => _focusMode = TripLiveMapFocusMode.vehicleOnly),
        onFocusPassenger:
            () =>
                setState(() => _focusMode = TripLiveMapFocusMode.passengerOnly),
      ),
      map: TripLiveMapPanel(
        motoristaId: effectiveMotoristaId,
        email: widget.session.email,
        senha: '',
        accessToken: widget.session.accessToken,
        height: MediaQuery.sizeOf(context).height,
        renderMode:
            motoristaId == null
                ? TripLiveMapRenderMode.placeholder
                : TripLiveMapRenderMode.interactive,
        routeGeometry: trip.rotaGeografica,
        trackingState: trip.trackingState,
        focusMode: _focusMode,
        showControls: false,
        mapPadding: const EdgeInsets.fromLTRB(12, 106, 12, 332),
      ),
      footer: GuardianBottomNavContent(
        selectedIndex: widget.selectedNavIndex,
        onDestinationSelected: widget.onDestinationSelected ?? (_) {},
      ),
      sheetBuilder: (context, scrollController, detent, onDetentSelected) {
        return GuardianTripFooterSheetContent(
          passageiro: passageiro,
          trip: trip,
          dayTrips: _dayTrips,
          selectedTripId: _currentTrip?.id,
          passageiros: _passageiros,
          selectedPassageiroId: _selectedPassageiro?.id,
          lastLocationUpdatedAt:
              _historico.isEmpty ? null : _historico.first.dataHora,
          detent: detent,
          scrollController: scrollController,
          onDetentSelected: onDetentSelected,
          onTripSelected: _selecionarViagemDoDia,
          onRefresh: _loadPassageiros,
          onPassageiroSelected: _selectPassageiro,
          onFocusVan:
              () =>
                  setState(() => _focusMode = TripLiveMapFocusMode.vehicleOnly),
          onFocusPassenger:
              () => setState(
                () => _focusMode = TripLiveMapFocusMode.passengerOnly,
              ),
          tripKeyBuilder:
              GuardianLocalizacaoScreen.selecionarViagemDiaButtonKey,
        );
      },
    );
  }

  void _connectWebSocket(int motoristaId) {
    _webSocketService.onLocationReceived = (payload) {
      final receivedMotoristaId = (payload['motoristaId'] as num?)?.toInt();
      if (receivedMotoristaId != motoristaId || !mounted) {
        return;
      }

      final snapshot = GuardianLocalizacaoSnapshot(
        latitude: (payload['latitude'] as num).toDouble(),
        longitude: (payload['longitude'] as num).toDouble(),
        dataHora: DateTime.now(),
      );

      setState(() {
        final next = <GuardianLocalizacaoSnapshot>[snapshot];
        next.addAll(
          _historico.where(
            (item) =>
                item.latitude != snapshot.latitude ||
                item.longitude != snapshot.longitude ||
                item.dataHora.difference(snapshot.dataHora).inSeconds.abs() > 2,
          ),
        );
        _historico = next;
      });
    };

    _webSocketService.connect(
      authorizationHeader: buildAuthorizationHeader(
        accessToken: widget.session.accessToken,
        email: widget.session.email,
        senha: '',
      ),
      companyId: widget.session.companyId,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!_loadingPassageiros &&
        _passageirosError == null &&
        _selectedPassageiro != null &&
        !_loadingTracking &&
        _trackingError == null &&
        _currentTrip != null) {
      return _buildGuardianMapScene(
        context,
        _selectedPassageiro!,
        _currentTrip!,
      );
    }

    return GuardianLocalizacaoFallbackView(
      loadingPassageiros: _loadingPassageiros,
      passageiros: _passageiros,
      selectedPassageiro: _selectedPassageiro,
      passageirosError: _passageirosError,
      trackingContent: _buildTrackingContent(context),
      selectedNavIndex: widget.selectedNavIndex,
      onRefresh: _loadPassageiros,
      onPassageiroSelected: _selectPassageiro,
      onDestinationSelected: widget.onDestinationSelected ?? (_) {},
    );
  }

  List<Widget> _buildTrackingContent(BuildContext context) {
    final passageiro = _selectedPassageiro;
    if (passageiro == null) {
      return const [];
    }

    if (_loadingTracking) {
      return const [
        Padding(
          padding: EdgeInsets.symmetric(vertical: 48),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }

    if (_trackingError != null) {
      return [
        GuardianLocalizacaoMessageCard(
          icon: Icons.warning_amber_outlined,
          color: const Color(0xFFB3261E),
          title: AppStrings.guardian.erroAoCarregarRastreio,
          description: _trackingError!,
          actionLabel: AppStrings.guardian.tentarNovamente,
          onAction: _loadTracking,
        ),
      ];
    }

    final trip = _currentTrip;
    if (trip == null) {
      return [
        GuardianLocalizacaoStaticCard(
          icon: Icons.route_outlined,
          title: AppStrings.guardian.semViagemHoje,
          description: AppStrings.guardian.semViagemHojeDescricao,
        ),
      ];
    }

    final stop = trip.paradaDoPassageiro;
    final latest = _historico.isNotEmpty ? _historico.first : null;
    final motoristaId = trip.motorista?.id;

    return [
      MapFirstTripTrackingView(
        trip: trip,
        audienceLabel: AppStrings.guardian.responsavel,
        headline: AppStrings.guardian.acompanheViagemDe(passageiro.nome),
        subheadline: AppStrings.guardian.subheadlineRastreioPassageiro,
        motoristaId: motoristaId,
        email: widget.session.email,
        senha: '',
        accessToken: widget.session.accessToken,
        lastUpdatedAt: latest?.dataHora,
        nextEventLabel:
            stop?.proximaParada == true
                ? AppStrings.guardian.passageiroEoProximoDaRota
                : AppStrings.guardian.rotaEmAcompanhamento,
        secondaryStatusLabel:
            stop == null
                ? AppStrings.guardian.semParadaPublicada
                : AppStrings.guardian.paradaResumoComStatus(
                  stop.ordem,
                  presentStopStatus(stop.status),
                  presentRouteVersionLabel(trip.rotaVersao),
                ),
        primaryActionLabel: AppStrings.guardian.expandirMapa,
        primaryActionKey: GuardianLocalizacaoScreen.abrirMapaButtonKey,
        onPrimaryAction:
            motoristaId == null
                ? null
                : () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder:
                          (_) => VerLocalizacaoScreen(
                            motoristaId: motoristaId,
                            email: widget.session.email,
                            senha: '',
                            accessToken: widget.session.accessToken,
                            trip: trip,
                          ),
                    ),
                  );
                },
      ),
      const SizedBox(height: 18),
      GuardianLocalizacaoTimelineCard(historico: _historico),
    ];
  }
}
