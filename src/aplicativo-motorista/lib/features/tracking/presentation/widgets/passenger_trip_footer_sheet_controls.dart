import 'package:flutter/material.dart';

import '../../../../app/theme/vanrides_motion.dart';
import '../trip_tracking_presenter.dart';
import 'passenger_trip_footer_sheet_model.dart';
import 'passenger_trip_glass_sheet.dart';
import 'passenger_trip_sheet_detent.dart';
import 'vanrides_map_tokens.dart';

class PassengerTripSheetHandle extends StatelessWidget {
  const PassengerTripSheetHandle({
    super.key,
    required this.detent,
    required this.onDetentSelected,
  });

  final PassengerTripSheetDetent detent;
  final ValueChanged<PassengerTripSheetDetent> onDetentSelected;

  @override
  Widget build(BuildContext context) {
    final motion = VanRidesMotionPolicy.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 10, 10, 4),
      child: Row(
        children: [
          Expanded(
            child: Center(
              child: GestureDetector(
                key: const Key('passenger.trip.sheet.handle'),
                behavior: HitTestBehavior.translucent,
                onTap: () => onDetentSelected(detent.expand()),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 10,
                  ),
                  child: Container(
                    width: 48,
                    height: 5,
                    decoration: BoxDecoration(
                      color: VanRidesMapTokens.textSecondary.withValues(
                        alpha: 0.58,
                      ),
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                ),
              ),
            ),
          ),
          IconButton(
            key: const Key('passenger.trip.sheet.chevron'),
            tooltip:
                detent == PassengerTripSheetDetent.expanded
                    ? 'Recolher resumo'
                    : 'Expandir resumo',
            visualDensity: VisualDensity.compact,
            constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
            padding: EdgeInsets.zero,
            onPressed: () => onDetentSelected(detent.toggleByChevron()),
            icon: AnimatedRotation(
              turns: detent == PassengerTripSheetDetent.expanded ? 0 : 0.5,
              duration: motion.duration(
                VanRidesMotionTokens.navigationSelection,
              ),
              curve: motion.curve(VanRidesMotionTokens.easeOutQuart),
              child: const Icon(
                Icons.keyboard_arrow_down_rounded,
                color: VanRidesMapTokens.textSecondary,
                size: 24,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class PassengerTripStatusLine extends StatelessWidget {
  const PassengerTripStatusLine({
    super.key,
    required this.model,
    this.expanded = false,
  });

  final PassengerTripFooterSheetModel model;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    final titleStyle =
        expanded
            ? Theme.of(context).textTheme.titleLarge
            : Theme.of(context).textTheme.titleMedium;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                model.statusTitle,
                maxLines: expanded ? 2 : 1,
                overflow: TextOverflow.ellipsis,
                style: titleStyle?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                  height: 1.04,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                model.statusSubtitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: VanRidesMapTokens.blueTracking,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 10),
        PassengerTripSignalPill(signal: model.signal, compact: !expanded),
      ],
    );
  }
}

class PassengerTripStopLine extends StatelessWidget {
  const PassengerTripStopLine({super.key, required this.model});

  final PassengerTripFooterSheetModel model;

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
                'Minha parada',
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: VanRidesMapTokens.blueTracking,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                model.stopTitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                model.stopSubtitle,
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
}

class PassengerTripInfoRow extends StatelessWidget {
  const PassengerTripInfoRow({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    this.trailing = '',
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        PassengerTripRoundIcon(icon: icon),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                subtitle,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: VanRidesMapTokens.textSecondary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        if (trailing.isNotEmpty) ...[
          const SizedBox(width: 8),
          PassengerTripOperationPill(label: trailing),
        ],
      ],
    );
  }
}

class PassengerTripSignalPill extends StatelessWidget {
  const PassengerTripSignalPill({
    super.key,
    required this.signal,
    required this.compact,
  });

  final TripTrackingPresentation signal;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final label = passengerTripSignalDisplayLabel(
      signal.label,
      compact: compact,
      compactLabel: signal.compactLabel,
    );

    return Semantics(
      label: signal.label,
      child: Container(
        constraints: const BoxConstraints(minHeight: 34, maxWidth: 156),
        padding: EdgeInsets.symmetric(
          horizontal: compact ? 9 : 11,
          vertical: compact ? 7 : 8,
        ),
        decoration: BoxDecoration(
          color: signal.color.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: signal.color.withValues(alpha: 0.32)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(signal.icon, color: signal.color, size: compact ? 15 : 16),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class PassengerTripRoundIcon extends StatelessWidget {
  const PassengerTripRoundIcon({super.key, required this.icon});

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 42,
      height: 42,
      decoration: BoxDecoration(
        color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.28),
        ),
      ),
      child: Icon(icon, color: VanRidesMapTokens.blueTracking, size: 22),
    );
  }
}

class PassengerTripOperationPill extends StatelessWidget {
  const PassengerTripOperationPill({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(maxWidth: 92),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.58),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.34),
        ),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: VanRidesMapTokens.textPrimary,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }
}

class PassengerTripPresenceAction extends StatelessWidget {
  const PassengerTripPresenceAction({
    super.key,
    required this.label,
    required this.onTap,
  });

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        key: const Key('passenger.trip.sheet.presenceInline'),
        onPressed: onTap,
        icon: const Icon(Icons.fact_check_rounded, size: 18),
        label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
        style: OutlinedButton.styleFrom(
          foregroundColor: VanRidesMapTokens.textPrimary,
          backgroundColor: VanRidesMapTokens.amberWarning.withValues(
            alpha: 0.10,
          ),
          side: BorderSide(
            color: VanRidesMapTokens.amberWarning.withValues(alpha: 0.32),
          ),
          minimumSize: const Size.fromHeight(42),
          padding: const EdgeInsets.symmetric(horizontal: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          textStyle: Theme.of(
            context,
          ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w900),
        ),
      ),
    );
  }
}

class PassengerTripSheetActionButton extends StatelessWidget {
  const PassengerTripSheetActionButton({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onTap,
      icon: Icon(icon, size: 18),
      label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
      style: OutlinedButton.styleFrom(
        foregroundColor: VanRidesMapTokens.textPrimary,
        backgroundColor: VanRidesMapTokens.surfaceRaised.withValues(
          alpha: 0.40,
        ),
        side: BorderSide(
          color: VanRidesMapTokens.border.withValues(alpha: 0.32),
        ),
        minimumSize: const Size.fromHeight(48),
        padding: const EdgeInsets.symmetric(horizontal: 10),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        textStyle: Theme.of(
          context,
        ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w900),
      ),
    );
  }
}

class PassengerTripSectionLabel extends StatelessWidget {
  const PassengerTripSectionLabel(this.label, {super.key});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: Theme.of(context).textTheme.labelLarge?.copyWith(
        color: VanRidesMapTokens.blueTracking,
        fontWeight: FontWeight.w900,
      ),
    );
  }
}
