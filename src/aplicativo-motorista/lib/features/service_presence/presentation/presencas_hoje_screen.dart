import 'package:intl/intl.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/data/driver_trip_service.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class PresencasHojeScreen extends StatefulWidget {
  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final http.Client? client;
  final DriverTripGateway? gateway;
  final DateTime Function()? now;

  static const emptyStateTextKey = Key('motorista.presencasHoje.empty');

  const PresencasHojeScreen({
    super.key,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.client,
    this.gateway,
    this.now,
  });

  @override
  State<PresencasHojeScreen> createState() => _PresencasHojeScreenState();
}

class _PresencasHojeScreenState extends State<PresencasHojeScreen> {
  late final DriverTripGateway _gateway;
  List<DriverTripRecord> _viagens = const [];
  bool _carregando = true;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ??
        DriverTripService(
          email: widget.email,
          senha: widget.senha,
          accessToken: widget.accessToken,
          client: widget.client,
          now: widget.now,
        );
    _carregarManifestoDeHoje();
  }

  Future<void> _carregarManifestoDeHoje() async {
    setState(() => _carregando = true);

    try {
      final viagens = await _gateway.fetchDayTrips(widget.motoristaId);
      if (!mounted) return;
      setState(() {
        _viagens = viagens;
        _carregando = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _carregando = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.motorista.erroAoCarregarPresencas)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final hoje = widget.now?.call() ?? DateTime.now();
    final hojeFormatado = DateFormat('dd/MM/yyyy').format(hoje);
    final totalParadas = _viagens.fold<int>(
      0,
      (sum, viagem) => sum + viagem.totalParadas,
    );
    final confirmadas = _viagens.fold<int>(
      0,
      (sum, viagem) => sum + viagem.confirmadosCount,
    );
    final pendentes = totalParadas - confirmadas;

    return Scaffold(
      backgroundColor: MapFirstPalette.background,
      appBar: AppBar(
        title: Text(AppStrings.motorista.manifestoDeHoje),
        backgroundColor: Colors.transparent,
        foregroundColor: MapFirstPalette.textStrong,
        elevation: 0,
        surfaceTintColor: Colors.transparent,
        actions: [
          IconButton(
            tooltip: AppStrings.motorista.atualizar,
            onPressed: _carregando ? null : _carregarManifestoDeHoje,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body:
          _carregando
              ? const Center(
                child: CircularProgressIndicator(
                  color: MapFirstPalette.brandBlue,
                ),
              )
              : totalParadas == 0
              ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: VanRidesLiquidSurface.card(
                    padding: const EdgeInsets.all(24),
                    child: Text(
                      key: PresencasHojeScreen.emptyStateTextKey,
                      AppStrings.motorista.nenhumaParadaPlanejadaPara(
                        hojeFormatado,
                      ),
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ),
              )
              : ListView(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
                children: [
                  _ManifestSummaryCard(
                    totalParadas: totalParadas,
                    confirmadas: confirmadas,
                    pendentes: pendentes,
                    dataLabel: hojeFormatado,
                  ),
                  const SizedBox(height: 16),
                  for (final viagem in _viagens)
                    if (viagem.paradas.isNotEmpty) ...[
                      _TripManifestSection(viagem: viagem),
                      const SizedBox(height: 14),
                    ],
                ],
              ),
    );
  }
}

class _ManifestSummaryCard extends StatelessWidget {
  const _ManifestSummaryCard({
    required this.totalParadas,
    required this.confirmadas,
    required this.pendentes,
    required this.dataLabel,
  });

  final int totalParadas;
  final int confirmadas;
  final int pendentes;
  final String dataLabel;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.card(
      padding: const EdgeInsets.all(18),
      child: Row(
        children: [
          const _GlassIcon(
            icon: Icons.fact_check_outlined,
            color: MapFirstPalette.brandBlue,
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  AppStrings.motorista.manifestoOperacional,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  AppStrings.motorista.manifestoResumo(
                    totalParadas,
                    confirmadas,
                    pendentes,
                  ),
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: MapFirstPalette.textMuted,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          VanRidesLiquidSurface.action(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Text(
              dataLabel,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _TripManifestSection extends StatelessWidget {
  const _TripManifestSection({required this.viagem});

  final DriverTripRecord viagem;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.card(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _tripTitle(viagem),
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      AppStrings.motorista.manifestoResumo(
                        viagem.totalParadas,
                        viagem.confirmadosCount,
                        viagem.pendentesCount,
                      ),
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: MapFirstPalette.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              _StatusPill(
                label: _tripStatusLabel(viagem.status),
                color: _tripStatusColor(viagem.status),
              ),
            ],
          ),
          const SizedBox(height: 10),
          for (final parada in viagem.paradas)
            _StopManifestTile(parada: parada),
        ],
      ),
    );
  }
}

class _StopManifestTile extends StatelessWidget {
  const _StopManifestTile({required this.parada});

  final DriverTripStopRecord parada;

  @override
  Widget build(BuildContext context) {
    final presenceColor =
        parada.confirmadoPresenca
            ? MapFirstPalette.green
            : MapFirstPalette.amber;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: presenceColor.withValues(alpha: 0.12),
              shape: BoxShape.circle,
              border: Border.all(color: presenceColor.withValues(alpha: 0.22)),
            ),
            child: Icon(
              parada.confirmadoPresenca
                  ? Icons.check_rounded
                  : Icons.schedule_outlined,
              color: presenceColor,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        parada.passageiroNome,
                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          color: MapFirstPalette.textStrong,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    if (parada.proximaParada)
                      _StatusPill(
                        label: AppStrings.motorista.proxima,
                        color: MapFirstPalette.brandBlue,
                      ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  '${AppStrings.motorista.parada(parada.ordem)} • ${presentEtaLabel(parada.etaMinutos)}',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: MapFirstPalette.textMuted,
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _StatusPill(
                      label:
                          parada.confirmadoPresenca
                              ? AppStrings.motorista.presencaConfirmada
                              : AppStrings.motorista.presencaPendente,
                      color: presenceColor,
                    ),
                    _StatusPill(
                      label: presentStopStatus(parada.status),
                      color: _stopStatusColor(parada.status),
                    ),
                    if (parada.menorDeIdade)
                      _StatusPill(
                        label: AppStrings.motorista.menorDeIdade,
                        color: MapFirstPalette.red,
                      ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.action(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
          color: color,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _GlassIcon extends StatelessWidget {
  const _GlassIcon({required this.icon, required this.color});

  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 46,
      height: 46,
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        shape: BoxShape.circle,
        border: Border.all(color: color.withValues(alpha: 0.26)),
      ),
      child: Icon(icon, color: color),
    );
  }
}

String _tripTitle(DriverTripRecord viagem) {
  final horario = viagem.horarioPartida ?? '--:--';
  final tipo = viagem.tipoOperacaoLabel;
  final nome = viagem.nomeOperacional;
  return tipo.isEmpty ? '$horario • $nome' : '$horario • $tipo • $nome';
}

String _tripStatusLabel(TripTrackingStatus status) =>
    presentTripStatus(status).label;

Color _tripStatusColor(TripTrackingStatus status) {
  return switch (status) {
    TripTrackingStatus.planned => MapFirstPalette.brandBlue,
    TripTrackingStatus.inProgress => MapFirstPalette.green,
    TripTrackingStatus.completed => MapFirstPalette.green,
    TripTrackingStatus.canceled => MapFirstPalette.red,
    TripTrackingStatus.unknown => MapFirstPalette.textMuted,
  };
}

Color _stopStatusColor(TripTrackingStopStatus status) {
  return switch (status) {
    TripTrackingStopStatus.completed => MapFirstPalette.green,
    TripTrackingStopStatus.skipped => MapFirstPalette.red,
    TripTrackingStopStatus.noShow => MapFirstPalette.red,
    TripTrackingStopStatus.pending => MapFirstPalette.brandBlue,
    TripTrackingStopStatus.unknown => MapFirstPalette.textMuted,
  };
}
