import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_next_action_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_premium_chip.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverActiveOperationHeader extends StatelessWidget {
  const DriverActiveOperationHeader({
    super.key,
    required this.signalIcon,
    required this.signalLabel,
    required this.signalColor,
    required this.signalHelper,
    this.signalContextLabel,
    required this.operationLabel,
    required this.stop,
    required this.routeName,
    required this.etaLabel,
  });

  final IconData signalIcon;
  final String signalLabel;
  final Color signalColor;
  final String signalHelper;
  final String? signalContextLabel;
  final String operationLabel;
  final DriverTripStopRecord stop;
  final String routeName;
  final String etaLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      label:
          '${AppStrings.motorista.painelDaParada}: $signalLabel, '
          '$operationLabel, ${stop.passageiroNome}, $etaLabel',
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.84),
          border: Border.all(
            color: VanRidesMapTokens.border.withValues(alpha: 0.26),
          ),
          borderRadius: BorderRadius.circular(22),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  DriverPremiumChip(
                    icon: signalIcon,
                    label: signalLabel,
                    color: signalColor,
                  ),
                  DriverPremiumChip(
                    icon: Icons.alt_route_rounded,
                    label: operationLabel,
                    color: VanRidesMapTokens.textSecondary,
                  ),
                ],
              ),
              const SizedBox(height: 8),
              if (signalContextLabel != null) ...[
                Text(
                  signalContextLabel!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: VanRidesMapTokens.blueTracking,
                    fontWeight: FontWeight.w800,
                    height: 1.16,
                  ),
                ),
                const SizedBox(height: 4),
              ],
              Text(
                signalHelper,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: VanRidesMapTokens.textSecondary,
                  fontWeight: FontWeight.w700,
                  height: 1.16,
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  MapFirstAvatar(
                    label: stop.passageiroNome,
                    size: 50,
                    backgroundColor: MapFirstPalette.blue,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _PassengerSummary(stop: stop, routeName: routeName),
                  ),
                  const SizedBox(width: 10),
                  _EtaSummary(etaLabel: etaLabel),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class DriverHomeReviewActionSection extends StatelessWidget {
  const DriverHomeReviewActionSection({super.key, required this.actions});

  final List<DriverHomeSecondaryAction> actions;

  @override
  Widget build(BuildContext context) {
    if (actions.isEmpty) {
      return const SizedBox.shrink();
    }

    return DecoratedBox(
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.52),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.2),
        ),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              AppStrings.motorista.acoesDeRevisao,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: VanRidesMapTokens.textPrimary,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 3),
            Text(
              AppStrings.motorista.acoesDeRevisaoDescricao,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: VanRidesMapTokens.textMuted,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 10),
            DriverHomeSecondaryActionGrid(actions: actions),
          ],
        ),
      ),
    );
  }
}

class _PassengerSummary extends StatelessWidget {
  const _PassengerSummary({required this.stop, required this.routeName});

  final DriverTripStopRecord stop;
  final String routeName;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppStrings.motorista.proximoPassageiro,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: VanRidesMapTokens.textMuted,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 3),
        Text(
          stop.passageiroNome,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
            color: VanRidesMapTokens.textPrimary,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 3),
        Text(
          AppStrings.motorista.stopRouteSummary(stop.ordem, routeName),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: VanRidesMapTokens.textSecondary,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}

class _EtaSummary extends StatelessWidget {
  const _EtaSummary({required this.etaLabel});

  final String etaLabel;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Text(
          AppStrings.motorista.eta,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: VanRidesMapTokens.textMuted,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          etaLabel,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
            color: VanRidesMapTokens.blueTracking,
            fontWeight: FontWeight.w900,
          ),
        ),
      ],
    );
  }
}
