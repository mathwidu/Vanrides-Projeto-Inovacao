import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../domain/trip_route_geometry_record.dart';
import 'map_first_v2_components.dart';

class MapFirstTripMetricItem {
  const MapFirstTripMetricItem({
    required this.label,
    required this.value,
    required this.icon,
  });

  final String label;
  final String value;
  final IconData icon;
}

class MapFirstTripMetricGrid extends StatelessWidget {
  const MapFirstTripMetricGrid({super.key, required this.items});

  final List<MapFirstTripMetricItem> items;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final availableWidth = constraints.maxWidth;
        final tileWidth =
            availableWidth >= 720 ? (availableWidth - 12) / 2 : availableWidth;

        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            for (final item in items)
              SizedBox(
                width: tileWidth,
                child: _MapFirstTripMetricCell(item: item),
              ),
          ],
        );
      },
    );
  }
}

class _MapFirstTripMetricCell extends StatelessWidget {
  const _MapFirstTripMetricCell({required this.item});

  final MapFirstTripMetricItem item;

  @override
  Widget build(BuildContext context) {
    return MapFirstSurfaceCard(
      padding: const EdgeInsets.all(16),
      color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.84),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: MapFirstPalette.blueSoft,
            child: Icon(item.icon, size: 18, color: MapFirstPalette.blue),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.label,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: MapFirstPalette.textMuted,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  item.value,
                  maxLines: 4,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w700,
                    height: 1.25,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class MapFirstRouteContextBadge extends StatelessWidget {
  const MapFirstRouteContextBadge({
    super.key,
    required this.icon,
    required this.label,
  });

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return MapFirstGlassChip(icon: icon, label: label, compact: true);
  }
}

Color _queueStopAccent({required bool highlighted, required bool isNext}) {
  if (highlighted) {
    return MapFirstPalette.purple;
  }
  if (isNext) {
    return MapFirstPalette.amber;
  }
  return MapFirstPalette.blue;
}

Color _queueStopSurface({required bool highlighted, required bool isNext}) {
  if (highlighted) {
    return MapFirstPalette.purple.withValues(alpha: 0.16);
  }
  if (isNext) {
    return MapFirstPalette.amberSoft;
  }
  return MapFirstPalette.surfaceRaised.withValues(alpha: 0.84);
}

Color _queueStopBorder({required bool highlighted, required bool isNext}) {
  return _queueStopAccent(
    highlighted: highlighted,
    isNext: isNext,
  ).withValues(alpha: 0.24);
}

class MapFirstTripQueueStopTile extends StatelessWidget {
  const MapFirstTripQueueStopTile({
    super.key,
    required this.stop,
    required this.highlighted,
    required this.isNext,
  });

  final TripRouteWaypointRecord stop;
  final bool highlighted;
  final bool isNext;

  @override
  Widget build(BuildContext context) {
    final badgeColor = _queueStopAccent(
      highlighted: highlighted,
      isNext: isNext,
    );

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _queueStopSurface(highlighted: highlighted, isNext: isNext),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: _queueStopBorder(highlighted: highlighted, isNext: isNext),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: badgeColor.withValues(alpha: 0.14),
            child: Text(
              stop.ordem?.toString() ?? '-',
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: badgeColor,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  stop.nome,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  highlighted
                      ? AppStrings.passageiro.suaParada
                      : isNext
                      ? AppStrings.passageiro.proximaParadaDaViagem
                      : AppStrings.passageiro.paradaOperacionalDaRota,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: MapFirstPalette.textMuted,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class MapFirstTripRouteLegTile extends StatelessWidget {
  const MapFirstTripRouteLegTile({super.key, required this.leg});

  final TripRouteLegRecord leg;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          radius: 18,
          backgroundColor: MapFirstPalette.blueSoft,
          child: Text(
            '${leg.indice + 1}',
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: MapFirstPalette.blue,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${leg.origemLabel} → ${leg.destinoLabel}',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: MapFirstPalette.textStrong,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                _buildLegHelper(),
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: MapFirstPalette.textMuted,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  String _buildLegHelper() {
    final distance =
        leg.distanciaMetros == null ? null : '${leg.distanciaMetros} m';
    final duration =
        leg.duracaoSegundos == null
            ? null
            : '${(leg.duracaoSegundos! / 60).ceil()} min';

    if (distance == null && duration == null) {
      return AppStrings.passageiro.trechoOperacionalPublicado;
    }
    if (distance != null && duration != null) {
      return AppStrings.passageiro.distanciaDuracaoTrecho(distance, duration);
    }
    return distance ?? duration!;
  }
}

class MapFirstTripActionBar extends StatelessWidget {
  const MapFirstTripActionBar({
    super.key,
    required this.primaryLabel,
    this.primaryActionKey,
    this.onPrimaryAction,
    this.secondaryLabel,
    this.secondaryActionKey,
    this.onSecondaryAction,
  });

  final String primaryLabel;
  final Key? primaryActionKey;
  final VoidCallback? onPrimaryAction;
  final String? secondaryLabel;
  final Key? secondaryActionKey;
  final VoidCallback? onSecondaryAction;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final vertical = constraints.maxWidth < 520;
        if (vertical) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _PrimaryTripActionButton(
                actionKey: primaryActionKey,
                label: primaryLabel,
                onPressed: onPrimaryAction,
              ),
              if (secondaryLabel != null) ...[
                const SizedBox(height: 12),
                _SecondaryTripActionButton(
                  actionKey: secondaryActionKey,
                  label: secondaryLabel!,
                  onPressed: onSecondaryAction,
                ),
              ],
            ],
          );
        }

        return Row(
          children: [
            Expanded(
              child: _PrimaryTripActionButton(
                actionKey: primaryActionKey,
                label: primaryLabel,
                onPressed: onPrimaryAction,
              ),
            ),
            if (secondaryLabel != null) ...[
              const SizedBox(width: 12),
              Expanded(
                child: _SecondaryTripActionButton(
                  actionKey: secondaryActionKey,
                  label: secondaryLabel!,
                  onPressed: onSecondaryAction,
                ),
              ),
            ],
          ],
        );
      },
    );
  }
}

class _PrimaryTripActionButton extends StatelessWidget {
  const _PrimaryTripActionButton({
    required this.label,
    this.actionKey,
    this.onPressed,
  });

  final String label;
  final Key? actionKey;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return FilledButton.icon(
      key: actionKey,
      onPressed: onPressed,
      icon: const Icon(Icons.near_me_outlined),
      label: Text(label),
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(56),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
    );
  }
}

class _SecondaryTripActionButton extends StatelessWidget {
  const _SecondaryTripActionButton({
    required this.label,
    this.actionKey,
    this.onPressed,
  });

  final String label;
  final Key? actionKey;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      key: actionKey,
      onPressed: onPressed,
      icon: const Icon(Icons.fact_check_outlined),
      label: Text(label),
      style: OutlinedButton.styleFrom(
        minimumSize: const Size.fromHeight(56),
        foregroundColor: MapFirstPalette.textStrong,
        backgroundColor: MapFirstPalette.surfaceRaised.withValues(alpha: 0.84),
        side: BorderSide(
          color: MapFirstPalette.textStrong.withValues(alpha: 0.42),
          width: 2,
        ),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
    );
  }
}
