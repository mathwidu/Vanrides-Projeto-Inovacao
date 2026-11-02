import 'package:flutter/material.dart';

import '../../../../l10n/app_strings.dart';
import '../../../tracking/domain/trip_tracking_record.dart';
import '../../../tracking/presentation/trip_live_map_panel.dart';
import '../../../tracking/presentation/trip_tracking_presenter.dart';
import '../../../tracking/presentation/widgets/compact_trip_switcher.dart';
import '../../../tracking/presentation/widgets/passenger_trip_footer_sheet_controls.dart';
import '../../../tracking/presentation/widgets/passenger_trip_footer_sheet_model.dart';
import '../../../tracking/presentation/widgets/passenger_trip_sheet_detent.dart';
import '../../../tracking/presentation/widgets/vanrides_map_tokens.dart';
import '../../domain/guardian_linked_passageiro_record.dart';
import 'guardian_map_safety_widgets.dart';

typedef GuardianTripKeyBuilder = Key Function(int tripId);

class GuardianMapStageTopLayer extends StatelessWidget {
  const GuardianMapStageTopLayer({
    super.key,
    required this.selectedPassageiroName,
    required this.focusMode,
    required this.onRefresh,
    required this.onFocusVan,
    required this.onFocusPassenger,
  });

  final String selectedPassageiroName;
  final TripLiveMapFocusMode focusMode;
  final VoidCallback onRefresh;
  final VoidCallback onFocusVan;
  final VoidCallback onFocusPassenger;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Flexible(
          fit: FlexFit.loose,
          child: _GuardianTopChip(
            icon: Icons.person_pin_circle_rounded,
            label: selectedPassageiroName,
            selected: true,
            onTap: onFocusPassenger,
          ),
        ),
        const SizedBox(width: 10),
        Column(
          children: [
            _GuardianFocusButton(
              icon: Icons.directions_bus_filled_rounded,
              label: 'Van',
              selected: focusMode == TripLiveMapFocusMode.vehicleOnly,
              onTap: onFocusVan,
            ),
            const SizedBox(height: 8),
            _GuardianFocusButton(
              icon: Icons.person_pin_circle_rounded,
              label: 'Foco',
              selected: focusMode == TripLiveMapFocusMode.passengerOnly,
              onTap: onFocusPassenger,
            ),
            const SizedBox(height: 8),
            _GuardianFocusButton(
              icon: Icons.refresh_rounded,
              label: AppStrings.guardian.atualizar,
              selected: false,
              onTap: onRefresh,
              compact: true,
            ),
          ],
        ),
      ],
    );
  }
}

class GuardianTripFooterSheetContent extends StatelessWidget {
  const GuardianTripFooterSheetContent({
    super.key,
    required this.passageiro,
    required this.trip,
    required this.dayTrips,
    required this.selectedTripId,
    required this.passageiros,
    required this.selectedPassageiroId,
    required this.lastLocationUpdatedAt,
    required this.detent,
    required this.scrollController,
    required this.onDetentSelected,
    required this.onTripSelected,
    required this.onPassageiroSelected,
    required this.onRefresh,
    required this.onFocusVan,
    required this.onFocusPassenger,
    this.tripKeyBuilder,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final TripTrackingRecord trip;
  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final List<GuardianLinkedPassageiroRecord> passageiros;
  final int? selectedPassageiroId;
  final DateTime? lastLocationUpdatedAt;
  final PassengerTripSheetDetent detent;
  final ScrollController scrollController;
  final ValueChanged<PassengerTripSheetDetent> onDetentSelected;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final ValueChanged<GuardianLinkedPassageiroRecord> onPassageiroSelected;
  final VoidCallback onRefresh;
  final VoidCallback onFocusVan;
  final VoidCallback onFocusPassenger;
  final GuardianTripKeyBuilder? tripKeyBuilder;

  @override
  Widget build(BuildContext context) {
    final model = PassengerTripFooterSheetModel.from(
      trip,
      pendingPresenceCount: 0,
      signalRole: TripSignalRole.guardian,
    );

    return Semantics(
      label: detent.semanticLabel,
      container: true,
      child: CustomScrollView(
        key: const Key('guardian.trip.sheet'),
        controller: scrollController,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverToBoxAdapter(
            child: PassengerTripSheetHandle(
              detent: detent,
              onDetentSelected: onDetentSelected,
            ),
          ),
          SliverToBoxAdapter(
            child: _GuardianPersistentSummary(
              model: model,
              passageiro: passageiro,
            ),
          ),
          SliverToBoxAdapter(
            child: _GuardianExpandedContent(
              model: model,
              trip: trip,
              passageiro: passageiro,
              dayTrips: dayTrips,
              selectedTripId: selectedTripId,
              passageiros: passageiros,
              selectedPassageiroId: selectedPassageiroId,
              lastLocationUpdatedAt: lastLocationUpdatedAt,
              onTripSelected: onTripSelected,
              onPassageiroSelected: onPassageiroSelected,
              onRefresh: onRefresh,
              onFocusVan: onFocusVan,
              onFocusPassenger: onFocusPassenger,
              tripKeyBuilder: tripKeyBuilder,
            ),
          ),
        ],
      ),
    );
  }
}

class _GuardianPersistentSummary extends StatelessWidget {
  const _GuardianPersistentSummary({
    required this.model,
    required this.passageiro,
  });

  final PassengerTripFooterSheetModel model;
  final GuardianLinkedPassageiroRecord passageiro;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 0, 18, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          PassengerTripStatusLine(model: model),
          const SizedBox(height: 14),
          _GuardianPassengerStopLine(model: model, passageiro: passageiro),
        ],
      ),
    );
  }
}

class _GuardianExpandedContent extends StatelessWidget {
  const _GuardianExpandedContent({
    required this.model,
    required this.trip,
    required this.passageiro,
    required this.dayTrips,
    required this.selectedTripId,
    required this.passageiros,
    required this.selectedPassageiroId,
    required this.lastLocationUpdatedAt,
    required this.onTripSelected,
    required this.onPassageiroSelected,
    required this.onRefresh,
    required this.onFocusVan,
    required this.onFocusPassenger,
    this.tripKeyBuilder,
  });

  final PassengerTripFooterSheetModel model;
  final TripTrackingRecord trip;
  final GuardianLinkedPassageiroRecord passageiro;
  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final List<GuardianLinkedPassageiroRecord> passageiros;
  final int? selectedPassageiroId;
  final DateTime? lastLocationUpdatedAt;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final ValueChanged<GuardianLinkedPassageiroRecord> onPassageiroSelected;
  final VoidCallback onRefresh;
  final VoidCallback onFocusVan;
  final VoidCallback onFocusPassenger;
  final GuardianTripKeyBuilder? tripKeyBuilder;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 4, 18, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (passageiros.length > 1) ...[
            const PassengerTripSectionLabel('Passageiro'),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final item in passageiros)
                  ChoiceChip(
                    label: Text(item.nome),
                    selected: item.id == selectedPassageiroId,
                    showCheckmark: false,
                    onSelected: (_) => onPassageiroSelected(item),
                  ),
              ],
            ),
            const SizedBox(height: 16),
          ],
          if (dayTrips.length > 1) ...[
            const PassengerTripSectionLabel('Hoje'),
            const SizedBox(height: 8),
            CompactTripSwitcher(
              dayTrips: dayTrips,
              selectedTripId: selectedTripId,
              onTripSelected: onTripSelected,
              tripKeyBuilder: tripKeyBuilder,
            ),
            const SizedBox(height: 16),
          ],
          GuardianSafetyOverviewCard(
            passageiro: passageiro,
            trip: trip,
            model: model,
            lastLocationUpdatedAt: lastLocationUpdatedAt,
          ),
          const SizedBox(height: 16),
          const PassengerTripSectionLabel('Van e motorista'),
          const SizedBox(height: 8),
          PassengerTripInfoRow(
            icon: Icons.directions_bus_filled_rounded,
            title: model.serviceName,
            subtitle: model.driverName,
            trailing: model.operationLabel,
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: PassengerTripSheetActionButton(
                  icon: Icons.directions_bus_filled_rounded,
                  label: 'Ver van',
                  onTap: onFocusVan,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: PassengerTripSheetActionButton(
                  icon: Icons.person_pin_circle_rounded,
                  label: 'Passageiro',
                  onTap: onFocusPassenger,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          PassengerTripSheetActionButton(
            icon: Icons.refresh_rounded,
            label: model.signal.actionLabel,
            onTap: onRefresh,
          ),
        ],
      ),
    );
  }
}

class _GuardianPassengerStopLine extends StatelessWidget {
  const _GuardianPassengerStopLine({
    required this.model,
    required this.passageiro,
  });

  final PassengerTripFooterSheetModel model;
  final GuardianLinkedPassageiroRecord passageiro;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const PassengerTripRoundIcon(icon: Icons.person_pin_circle_rounded),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                AppStrings.guardian.pessoaAcompanhada,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: VanRidesMapTokens.blueTracking,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                passageiro.nome,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                _compactStopSubtitle(model),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: VanRidesMapTokens.textSecondary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        if (model.operationLabel.isNotEmpty) ...[
          const SizedBox(width: 8),
          PassengerTripOperationPill(label: model.operationLabel),
        ],
      ],
    );
  }

  String _compactStopSubtitle(PassengerTripFooterSheetModel model) {
    final helper = _guardianStopHelper(model.stopSubtitle);
    return '${model.stopTitle} • $helper';
  }

  String _guardianStopHelper(String value) {
    final helper = value.trim().replaceFirst(RegExp(r'\.$'), '');
    final lower = helper.toLowerCase();
    if (RegExp(
      r'^v[oó]c[eê]\s+(e|é)\s+o\s+pr[oó]ximo\s+da\s+fila$',
    ).hasMatch(lower)) {
      return 'Próximo da fila';
    }
    if (RegExp(r'^sua\s+parada\s+(e|é)\s+(a\s+)?pr[oó]xima$').hasMatch(lower)) {
      return 'Parada próxima';
    }
    return helper;
  }
}

class _GuardianTopChip extends StatelessWidget {
  const _GuardianTopChip({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final foreground =
        selected
            ? VanRidesMapTokens.backgroundDeep
            : VanRidesMapTokens.textPrimary;
    final background =
        selected
            ? VanRidesMapTokens.blueTracking
            : VanRidesMapTokens.surface.withValues(alpha: 0.62);
    return Semantics(
      button: true,
      selected: selected,
      label: label,
      child: InkWell(
        borderRadius: BorderRadius.circular(999),
        onTap: onTap,
        child: Container(
          constraints: const BoxConstraints(maxWidth: 174),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: background,
            borderRadius: BorderRadius.circular(999),
            border: Border.all(
              color: VanRidesMapTokens.border.withValues(alpha: 0.34),
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 18, color: foreground),
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: foreground,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GuardianFocusButton extends StatelessWidget {
  const _GuardianFocusButton({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
    this.compact = false,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final foreground =
        selected
            ? VanRidesMapTokens.backgroundDeep
            : VanRidesMapTokens.textPrimary;
    return Semantics(
      button: true,
      selected: selected,
      label: label,
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Container(
          width: compact ? 56 : 64,
          height: compact ? 54 : 64,
          decoration: BoxDecoration(
            color:
                selected
                    ? VanRidesMapTokens.blueTracking
                    : VanRidesMapTokens.surface.withValues(alpha: 0.64),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: VanRidesMapTokens.border.withValues(alpha: 0.32),
            ),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: foreground, size: compact ? 21 : 22),
              if (!compact) ...[
                const SizedBox(height: 4),
                Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: foreground,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
