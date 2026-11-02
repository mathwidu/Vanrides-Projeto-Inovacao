import 'package:flutter/material.dart';

import '../../../../l10n/app_strings.dart';
import '../../../tracking/domain/trip_tracking_record.dart';
import '../../../tracking/presentation/trip_tracking_presenter.dart';
import '../../../tracking/presentation/widgets/passenger_trip_footer_sheet_controls.dart';
import '../../../tracking/presentation/widgets/passenger_trip_footer_sheet_model.dart';
import '../../../tracking/presentation/widgets/vanrides_map_tokens.dart';
import '../../domain/guardian_linked_passageiro_record.dart';

class GuardianSafetyOverviewCard extends StatelessWidget {
  const GuardianSafetyOverviewCard({
    super.key,
    required this.passageiro,
    required this.trip,
    required this.model,
    required this.lastLocationUpdatedAt,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final TripTrackingRecord trip;
  final PassengerTripFooterSheetModel model;
  final DateTime? lastLocationUpdatedAt;

  @override
  Widget build(BuildContext context) {
    final tripStatus = presentTripStatus(trip.status);
    final stop = trip.paradaDoPassageiro;
    final stopValue =
        stop == null
            ? AppStrings.guardian.semParadaPublicada
            : AppStrings.guardian.paradaResumoComStatus(
              stop.ordem,
              presentStopStatus(stop.status),
              presentRouteVersionLabel(trip.rotaVersao),
            );

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.62),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.38),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const PassengerTripRoundIcon(icon: Icons.verified_user_outlined),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  AppStrings.guardian.acompanhamentoSeguro,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: VanRidesMapTokens.textPrimary,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          _GuardianSafetyRow(
            label: AppStrings.guardian.pessoaAcompanhada,
            value: passageiro.nome,
          ),
          _GuardianSafetyRow(
            label: AppStrings.guardian.vanDaViagem,
            value: '${model.serviceName} • ${model.driverName}',
          ),
          _GuardianSafetyRow(
            label: AppStrings.guardian.minhaParada,
            value: stopValue,
          ),
          _GuardianSafetyRow(
            label: AppStrings.guardian.ultimaAtualizacao,
            value: _updatedAtLabel(lastLocationUpdatedAt),
          ),
          _GuardianSafetyRow(
            label: AppStrings.guardian.statusDaViagem,
            value: '${tripStatus.label} • ${model.signal.label}',
            trailingColor: tripStatus.color,
          ),
        ],
      ),
    );
  }

  String _updatedAtLabel(DateTime? value) {
    if (value == null) {
      return AppStrings.guardian.aguardandoSinalDaVan;
    }
    final hour = value.hour.toString().padLeft(2, '0');
    final minute = value.minute.toString().padLeft(2, '0');
    return AppStrings.guardian.atualizadaAs('$hour:$minute');
  }
}

class _GuardianSafetyRow extends StatelessWidget {
  const _GuardianSafetyRow({
    required this.label,
    required this.value,
    this.trailingColor,
  });

  final String label;
  final String value;
  final Color? trailingColor;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            flex: 4,
            child: Text(
              label,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: VanRidesMapTokens.textSecondary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 5,
            child: Text(
              value,
              softWrap: true,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: trailingColor ?? VanRidesMapTokens.textPrimary,
                fontWeight: FontWeight.w900,
                height: 1.16,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
