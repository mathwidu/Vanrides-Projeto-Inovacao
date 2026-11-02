import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../data/trip_tracking_service.dart';
import '../domain/trip_route_geometry_record.dart';
import '../domain/trip_tracking_record.dart';
import 'widgets/vanrides_map_tokens.dart';

part 'passenger_trip_details_widgets.dart';

enum PassengerTripDetailsAction { openSupport }

class PassengerTripDetailsScreen extends StatefulWidget {
  const PassengerTripDetailsScreen({super.key, required this.trip})
    : passageiroId = null,
      email = null,
      senha = null,
      accessToken = null,
      trackingGateway = null;

  const PassengerTripDetailsScreen.fromSession({
    super.key,
    required this.passageiroId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.trackingGateway,
  }) : trip = null;

  final TripTrackingRecord? trip;
  final int? passageiroId;
  final String? email;
  final String? senha;
  final String? accessToken;
  final TripTrackingGateway? trackingGateway;

  @override
  State<PassengerTripDetailsScreen> createState() =>
      _PassengerTripDetailsScreenState();
}

class _PassengerTripDetailsScreenState
    extends State<PassengerTripDetailsScreen> {
  late final TripTrackingGateway? _trackingGateway;
  TripTrackingRecord? _trip;
  bool _loading = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _trip = widget.trip;
    if (widget.trip == null) {
      _trackingGateway =
          widget.trackingGateway ??
          TripTrackingService(
            email: widget.email ?? '',
            senha: widget.senha ?? '',
            accessToken: widget.accessToken,
          );
      _loadTrip();
    } else {
      _trackingGateway = null;
    }
  }

  Future<void> _loadTrip() async {
    final trackingGateway = _trackingGateway;
    final passageiroId = widget.passageiroId;
    if (trackingGateway == null || passageiroId == null) {
      return;
    }

    setState(() {
      _loading = true;
      _errorMessage = null;
    });

    try {
      final trip = await trackingGateway.fetchCurrentTrip(passageiroId);
      if (!mounted) {
        return;
      }
      setState(() {
        _trip = trip;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loading = false;
        _errorMessage = error.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final trip = _trip;
    if (_loading) {
      return const _DetailsStateScaffold(
        child: Center(child: CircularProgressIndicator()),
      );
    }

    if (_errorMessage != null) {
      return _DetailsStateScaffold(
        child: _StatePanel(
          icon: Icons.warning_amber_rounded,
          title: AppStrings.passageiro.erroAoCarregarViagem,
          message: _errorMessage!,
        ),
      );
    }

    if (trip == null) {
      return _DetailsStateScaffold(
        child: _StatePanel(
          icon: Icons.alt_route_rounded,
          title: AppStrings.passageiro.semViagemHoje,
          message: AppStrings.passageiro.semViagemHojeDescricao,
        ),
      );
    }

    final stop = trip.paradaDoPassageiro;
    final routeStops = trip.rotaGeografica?.orderedStops ?? const [];
    final stopsBefore = _stopsBefore(routeStops, stop);
    final stopsAfter = _stopsAfter(routeStops, stop);

    return Scaffold(
      backgroundColor: VanRidesMapTokens.backgroundDeep,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(18, 14, 18, 28),
          children: [
            _DetailsHeader(trip: trip),
            const SizedBox(height: 16),
            _LiquidPanel(
              semanticLabel: 'Resumo operacional da viagem',
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _SectionTitle('Resumo operacional'),
                  const SizedBox(height: 12),
                  _SummaryGrid(
                    items: [
                      _SummaryItem(
                        icon: Icons.schedule_rounded,
                        label: 'Horario',
                        value: _operationSummary(trip),
                      ),
                      _SummaryItem(
                        icon: Icons.route_rounded,
                        label: 'Servico',
                        value: trip.nomeOperacional,
                      ),
                      _SummaryItem(
                        icon: Icons.flag_rounded,
                        label: 'Status',
                        value: _tripStatusLabel(trip.status),
                      ),
                      _SummaryItem(
                        icon: Icons.fact_check_rounded,
                        label: 'Status de presença',
                        value: _presenceLabel(stop),
                      ),
                    ],
                  ),
                  const SizedBox(height: 18),
                  _DetailRow(
                    icon: Icons.person_pin_circle_rounded,
                    label: 'Minha parada',
                    title:
                        stop == null
                            ? 'Parada não publicada'
                            : 'Parada ${stop.ordem}',
                    subtitle:
                        stop == null
                            ? 'A empresa ainda não publicou sua parada.'
                            : _stopHelper(trip, stop),
                    trailing: trip.tipoOperacaoLabel,
                  ),
                  const SizedBox(height: 14),
                  _DetailRow(
                    icon: Icons.directions_bus_filled_rounded,
                    label: 'Van e motorista',
                    title:
                        trip.motorista?.nome ??
                        AppStrings.passageiro.motoristaNaoPublicado,
                    subtitle: trip.nomeOperacional,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            _LiquidPanel(
              semanticLabel: 'Paradas da viagem',
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _SectionTitle('Paradas da viagem'),
                  const SizedBox(height: 12),
                  if (routeStops.isEmpty)
                    const _EmptyLine(
                      icon: Icons.alt_route_rounded,
                      title: 'Lista ainda nao publicada',
                      subtitle: 'A empresa ainda nao enviou as paradas do dia.',
                    )
                  else ...[
                    _StopGroup(
                      title: 'Antes de voce',
                      emptyText: 'Voce e a proxima parada.',
                      stops: stopsBefore,
                    ),
                    const SizedBox(height: 12),
                    _StopGroup(
                      title: 'Minha parada',
                      emptyText: 'Sua parada ainda nao foi publicada.',
                      stops:
                          stop == null
                              ? const []
                              : routeStops
                                  .where((item) => item.paradaDoPassageiro)
                                  .toList(growable: false),
                      highlighted: true,
                    ),
                    const SizedBox(height: 12),
                    _StopGroup(
                      title: 'Depois de voce',
                      emptyText: 'Nao ha paradas depois de voce nesta viagem.',
                      stops: stopsAfter,
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 14),
            _LiquidPanel(
              semanticLabel: 'Suporte da viagem',
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _SectionTitle('Suporte da viagem'),
                  const SizedBox(height: 8),
                  Text(
                    'Use suporte quando a parada, a van ou o horario nao baterem com o combinado.',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: VanRidesMapTokens.textSecondary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      key: const Key('passenger.trip.details.supportAction'),
                      onPressed:
                          () => Navigator.of(
                            context,
                          ).pop(PassengerTripDetailsAction.openSupport),
                      icon: const Icon(Icons.support_agent_rounded, size: 18),
                      label: const Text('Suporte da viagem'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: VanRidesMapTokens.textPrimary,
                        backgroundColor: VanRidesMapTokens.blueTracking
                            .withValues(alpha: 0.14),
                        side: BorderSide(
                          color: VanRidesMapTokens.blueTracking.withValues(
                            alpha: 0.42,
                          ),
                        ),
                        minimumSize: const Size.fromHeight(46),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(18),
                        ),
                        textStyle: Theme.of(context).textTheme.labelLarge
                            ?.copyWith(fontWeight: FontWeight.w900),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
