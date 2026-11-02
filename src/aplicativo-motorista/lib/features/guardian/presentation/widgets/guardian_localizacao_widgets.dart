import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../../l10n/app_strings.dart';
import '../../../tracking/domain/trip_tracking_record.dart';
import '../../../tracking/presentation/map_first_v2_components.dart';
import '../../../tracking/presentation/trip_offline_continuity_presenter.dart';
import '../../../tracking/presentation/trip_tracking_presenter.dart';
import '../../../tracking/presentation/widgets/vanrides_map_tokens.dart';
import '../../domain/guardian_linked_passageiro_record.dart';
import '../../domain/guardian_localizacao_snapshot.dart';

part 'guardian_localizacao_message_widgets.dart';

class GuardianLocalizacaoActiveTripSheet extends StatelessWidget {
  const GuardianLocalizacaoActiveTripSheet({
    super.key,
    required this.passageiro,
    required this.trip,
    required this.dayTrips,
    required this.selectedTripId,
    required this.onTripSelected,
    required this.passageiros,
    required this.selectedPassageiroId,
    required this.onPassageiroSelected,
    required this.onOpenMap,
    this.agendaKey,
    this.tripButtonKeyBuilder,
    this.openMapButtonKey,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final TripTrackingRecord trip;
  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final List<GuardianLinkedPassageiroRecord> passageiros;
  final int? selectedPassageiroId;
  final ValueChanged<GuardianLinkedPassageiroRecord> onPassageiroSelected;
  final VoidCallback? onOpenMap;
  final Key? agendaKey;
  final Key Function(int tripId)? tripButtonKeyBuilder;
  final Key? openMapButtonKey;

  @override
  Widget build(BuildContext context) {
    final stopsBefore = resolveStopsBeforePassenger(trip) ?? 0;
    final vanDistanceLabel = presentVanStopsAway(stopsBefore);
    final signal = presentLocationFreshness(
      trip.trackingState.locationFreshness,
      role: TripSignalRole.guardian,
    );
    final offlineContinuity = presentTripOfflineContinuity(
      trackingState: trip.trackingState,
      role: TripSignalRole.guardian,
    );
    final tipoOperacaoLabel = trip.tipoOperacaoLabel;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (dayTrips.length > 1) ...[
          GuardianLocalizacaoDayAgendaSelector(
            agendaKey: agendaKey,
            trips: dayTrips,
            selectedTripId: selectedTripId,
            tripButtonKeyBuilder: tripButtonKeyBuilder,
            onTripSelected: onTripSelected,
            dark: true,
          ),
          const SizedBox(height: 12),
        ],
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _GuardianPremiumChip(
              icon: Icons.person_pin_circle_rounded,
              label: AppStrings.guardian.passageiroSelecionado,
              color: VanRidesMapTokens.greenAction,
            ),
            _GuardianPremiumChip(
              icon: Icons.near_me_rounded,
              label: vanDistanceLabel,
              color: VanRidesMapTokens.blueTracking,
            ),
            _GuardianPremiumChip(
              icon: signal.icon,
              label: signal.label,
              color: signal.color,
            ),
            if (tipoOperacaoLabel.isNotEmpty)
              _GuardianPremiumChip(
                icon: Icons.route_rounded,
                label: tipoOperacaoLabel,
                color: VanRidesMapTokens.textSecondary,
              ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          offlineContinuity.visible ? offlineContinuity.title : signal.helper,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: VanRidesMapTokens.textSecondary,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 14),
        Text(
          passageiro.nome,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
            color: VanRidesMapTokens.textPrimary,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          trip.nomeOperacional,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: VanRidesMapTokens.textSecondary,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 12),
        MapFirstSecondaryButton(
          buttonKey: openMapButtonKey,
          icon: Icons.zoom_out_map_rounded,
          label: AppStrings.guardian.mapaCompleto,
          onPressed: onOpenMap,
          color: MapFirstPalette.blue,
        ),
        const SizedBox(height: 12),
        Text(
          AppStrings.guardian.trocarPassageiro,
          style: Theme.of(context).textTheme.labelLarge?.copyWith(
            color: VanRidesMapTokens.textMuted,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 10),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: passageiros
                .map(
                  (item) => Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(item.nome),
                      selected: selectedPassageiroId == item.id,
                      labelStyle: Theme.of(
                        context,
                      ).textTheme.labelLarge?.copyWith(
                        color:
                            selectedPassageiroId == item.id
                                ? Colors.white
                                : VanRidesMapTokens.textSecondary,
                        fontWeight: FontWeight.w800,
                      ),
                      backgroundColor: VanRidesMapTokens.surfaceRaised,
                      selectedColor: VanRidesMapTokens.greenAction,
                      side: BorderSide(
                        color:
                            selectedPassageiroId == item.id
                                ? VanRidesMapTokens.greenAction
                                : VanRidesMapTokens.border.withValues(
                                  alpha: 0.42,
                                ),
                      ),
                      showCheckmark: false,
                      onSelected: (_) => onPassageiroSelected(item),
                    ),
                  ),
                )
                .toList(growable: false),
          ),
        ),
      ],
    );
  }
}

class GuardianLocalizacaoOperationTypeBadge extends StatelessWidget {
  const GuardianLocalizacaoOperationTypeBadge({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.88),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: MapFirstPalette.border.withValues(alpha: 0.7),
        ),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: MapFirstPalette.blue,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class GuardianLocationStatusBadge extends StatelessWidget {
  const GuardianLocationStatusBadge({
    super.key,
    required this.label,
    required this.live,
  });

  final String label;
  final bool live;

  @override
  Widget build(BuildContext context) {
    final color =
        live ? VanRidesMapTokens.blueTracking : VanRidesMapTokens.textSecondary;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.24)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            live ? Icons.radar_rounded : Icons.route_outlined,
            size: 17,
            color: color,
          ),
          const SizedBox(width: 7),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 220),
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: VanRidesMapTokens.textPrimary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _GuardianPremiumChip extends StatelessWidget {
  const _GuardianPremiumChip({
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
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 16),
          const SizedBox(width: 7),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: VanRidesMapTokens.textPrimary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class GuardianLocalizacaoDayAgendaSelector extends StatelessWidget {
  const GuardianLocalizacaoDayAgendaSelector({
    super.key,
    required this.trips,
    required this.selectedTripId,
    required this.onTripSelected,
    this.agendaKey,
    this.tripButtonKeyBuilder,
    this.dark = false,
  });

  final List<TripTrackingRecord> trips;
  final int? selectedTripId;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final Key? agendaKey;
  final Key Function(int tripId)? tripButtonKeyBuilder;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    final labelColor =
        dark ? VanRidesMapTokens.textPrimary : MapFirstPalette.textStrong;
    final helperColor =
        dark ? VanRidesMapTokens.textMuted : MapFirstPalette.textMuted;
    final chipBackground =
        dark ? VanRidesMapTokens.surfaceRaised : MapFirstPalette.surfaceRaised;
    final selectedColor =
        dark ? VanRidesMapTokens.blueTracking : MapFirstPalette.brandBlue;
    final unselectedText =
        dark ? VanRidesMapTokens.textSecondary : MapFirstPalette.textStrong;
    final unselectedBorder =
        dark
            ? VanRidesMapTokens.border.withValues(alpha: 0.42)
            : MapFirstPalette.border;

    return Column(
      key: agendaKey,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                AppStrings.guardian.agendaDeHoje,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: labelColor,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            Text(
              AppStrings.guardian.viagensNoDia(trips.length),
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: helperColor,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final trip in trips) ...[
                ChoiceChip(
                  key: tripButtonKeyBuilder?.call(trip.id),
                  selected: trip.id == selectedTripId,
                  avatar: Icon(
                    _tripStatusIcon(trip.status),
                    size: 16,
                    color:
                        trip.id == selectedTripId
                            ? Colors.white
                            : selectedColor,
                  ),
                  label: Text(_tripOptionLabel(trip)),
                  labelStyle: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color:
                        trip.id == selectedTripId
                            ? Colors.white
                            : unselectedText,
                    fontWeight: FontWeight.w800,
                  ),
                  backgroundColor: chipBackground,
                  selectedColor: selectedColor,
                  side: BorderSide(
                    color:
                        trip.id == selectedTripId
                            ? selectedColor
                            : unselectedBorder,
                  ),
                  showCheckmark: false,
                  onSelected: (_) => onTripSelected(trip),
                ),
                const SizedBox(width: 8),
              ],
            ],
          ),
        ),
      ],
    );
  }

  String _tripOptionLabel(TripTrackingRecord trip) {
    final horario = trip.horarioPartida ?? '--:--';
    final tipo = trip.tipoOperacaoLabel;
    final nome = trip.nomeOperacional.trim();
    final suffix = tipo.isEmpty ? nome : '$tipo • $nome';
    return '$horario • $suffix';
  }

  IconData _tripStatusIcon(TripTrackingStatus status) {
    return switch (status) {
      TripTrackingStatus.inProgress => Icons.directions_bus_filled_rounded,
      TripTrackingStatus.completed => Icons.check_circle_outline_rounded,
      TripTrackingStatus.canceled => Icons.block_rounded,
      _ => Icons.schedule_outlined,
    };
  }
}

class GuardianLocalizacaoSelectedPassageiroCard extends StatelessWidget {
  const GuardianLocalizacaoSelectedPassageiroCard({
    super.key,
    required this.passageiro,
  });

  final GuardianLinkedPassageiroRecord passageiro;

  @override
  Widget build(BuildContext context) {
    return MapFirstSurfaceCard(
      padding: const EdgeInsets.all(18),
      color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.86),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            passageiro.nome,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            passageiro.menorDeIdade
                ? AppStrings.guardian.passageiroMenorDeIdade
                : AppStrings.guardian.passageiroAdulto,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(color: MapFirstPalette.textMuted),
          ),
          if ((passageiro.motoristaNome ?? '').isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              AppStrings.guardian.motoristaAssociadoDetalhe(
                passageiro.motoristaNome!,
              ),
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: MapFirstPalette.textMuted,
              ),
            ),
          ],
          if (passageiro.enderecoResumo.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              AppStrings.guardian.endereco(passageiro.enderecoResumo),
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: MapFirstPalette.textMuted,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class GuardianLocalizacaoTimelineCard extends StatelessWidget {
  const GuardianLocalizacaoTimelineCard({super.key, required this.historico});

  final List<GuardianLocalizacaoSnapshot> historico;

  @override
  Widget build(BuildContext context) {
    return MapFirstSurfaceCard(
      padding: const EdgeInsets.all(18),
      color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.86),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppStrings.guardian.atualizacoesRecentes,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 12),
          if (historico.isEmpty)
            Text(
              AppStrings.guardian.nenhumPontoLocalizacao,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: MapFirstPalette.textMuted,
              ),
            )
          else
            ...historico
                .take(5)
                .map(
                  (snapshot) => ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(
                      Icons.place_outlined,
                      color: MapFirstPalette.blue,
                    ),
                    title: Text(
                      '${snapshot.latitude.toStringAsFixed(5)}, ${snapshot.longitude.toStringAsFixed(5)}',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    subtitle: Text(
                      DateFormat(
                        'dd/MM/yyyy HH:mm',
                        'pt_BR',
                      ).format(snapshot.dataHora),
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: MapFirstPalette.textMuted,
                      ),
                    ),
                  ),
                ),
        ],
      ),
    );
  }
}

class GuardianLocalizacaoStaticCard extends StatelessWidget {
  const GuardianLocalizacaoStaticCard({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    return MapFirstSurfaceCard(
      padding: const EdgeInsets.all(24),
      color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.86),
      child: Column(
        children: [
          Icon(icon, size: 42, color: MapFirstPalette.blue),
          const SizedBox(height: 12),
          Text(
            title,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            description,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.35,
            ),
          ),
        ],
      ),
    );
  }
}
