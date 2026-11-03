import 'dart:ui';

import 'package:flutter/material.dart';

import '../../../app/theme/vanrides_motion.dart';
import '../../../l10n/app_strings.dart';
import 'map_first_v2_components.dart';
import 'widgets/vanrides_map_tokens.dart';

class MapFirstNavDestination {
  const MapFirstNavDestination({
    required this.icon,
    required this.selectedIcon,
    required this.label,
  });

  final IconData icon;
  final IconData selectedIcon;
  final String label;
}

class MapFirstBrandedTopBar extends StatelessWidget {
  const MapFirstBrandedTopBar({
    super.key,
    this.avatar,
    this.title = 'VanRides',
    this.trailing,
    this.badge,
    this.showDivider = true,
  });

  final Widget? avatar;
  final String title;
  final Widget? trailing;
  final Widget? badge;
  final bool showDivider;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: MapFirstPalette.background,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 12),
            child: Row(
              children: [
                if (avatar != null) ...[avatar!, const SizedBox(width: 12)],
                Text(
                  title,
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: MapFirstPalette.brandBlue,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (badge != null) ...[
                  const Spacer(),
                  badge!,
                ] else
                  const Spacer(),
                if (trailing != null) ...[const SizedBox(width: 12), trailing!],
              ],
            ),
          ),
          if (showDivider)
            const Divider(
              color: MapFirstPalette.border,
              thickness: 1,
              height: 1,
            ),
        ],
      ),
    );
  }
}

class MapFirstBottomNavBar extends StatelessWidget {
  const MapFirstBottomNavBar({
    super.key,
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.destinations,
  });

  static const overlayKey = Key('mapFirst.bottomNav.overlay');
  static const glassKey = Key('mapFirst.bottomNav.glass');
  static const double overlayClearance = 112;

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final List<MapFirstNavDestination> destinations;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      key: overlayKey,
      top: false,
      minimum: const EdgeInsets.fromLTRB(14, 0, 14, 10),
      child: Semantics(
        container: true,
        label: 'Navegacao principal',
        child: ClipRRect(
          borderRadius: BorderRadius.circular(30),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
            child: DecoratedBox(
              key: glassKey,
              decoration: BoxDecoration(
                color: VanRidesMapTokens.surface.withValues(alpha: 0.72),
                borderRadius: BorderRadius.circular(30),
                border: Border.all(
                  color: VanRidesMapTokens.border.withValues(alpha: 0.28),
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x59070C12),
                    blurRadius: 32,
                    offset: Offset(0, -10),
                  ),
                ],
              ),
              child: MapFirstBottomNavItems(
                selectedIndex: selectedIndex,
                onDestinationSelected: onDestinationSelected,
                destinations: destinations,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class MapFirstBottomNavItems extends StatelessWidget {
  const MapFirstBottomNavItems({
    super.key,
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.destinations,
  });

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final List<MapFirstNavDestination> destinations;

  static Key itemKey(int index) => Key('mapFirst.bottomNav.item.$index');

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(6),
      child: Row(
        children: [
          for (var index = 0; index < destinations.length; index++)
            Expanded(
              child: _MapFirstBottomNavItem(
                key: itemKey(index),
                destination: destinations[index],
                selected: selectedIndex == index,
                onTap: () => onDestinationSelected(index),
              ),
            ),
        ],
      ),
    );
  }
}

class _MapFirstBottomNavItem extends StatelessWidget {
  const _MapFirstBottomNavItem({
    super.key,
    required this.destination,
    required this.selected,
    required this.onTap,
  });

  final MapFirstNavDestination destination;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final motion = VanRidesMotionPolicy.of(context);
    final foreground =
        selected
            ? VanRidesMapTokens.backgroundDeep
            : VanRidesMapTokens.textMuted;
    final background =
        selected ? VanRidesMapTokens.blueTracking : Colors.transparent;

    return Semantics(
      button: true,
      selected: selected,
      label: destination.label,
      onTap: onTap,
      child: ExcludeSemantics(
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onTap,
          child: AnimatedContainer(
            duration: motion.duration(VanRidesMotionTokens.navigationSelection),
            curve: motion.curve(VanRidesMotionTokens.easeOutQuart),
            constraints: const BoxConstraints(minHeight: 52),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 7),
            decoration: BoxDecoration(
              color: background,
              borderRadius: BorderRadius.circular(22),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  selected ? destination.selectedIcon : destination.icon,
                  color: foreground,
                  size: 20,
                ),
                const SizedBox(height: 6),
                Text(
                  destination.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: foreground,
                    fontWeight: selected ? FontWeight.w900 : FontWeight.w700,
                    letterSpacing: 0,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class MapFirstMetricBentoCard extends StatelessWidget {
  const MapFirstMetricBentoCard({
    super.key,
    required this.title,
    required this.value,
    required this.icon,
    this.helper,
    this.accent = MapFirstPalette.brandBlue,
  });

  final String title;
  final String value;
  final IconData icon;
  final String? helper;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: MapFirstPalette.surface,
        border: Border.all(
          color: MapFirstPalette.border.withValues(alpha: 0.34),
        ),
        borderRadius: BorderRadius.circular(24),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66070C12),
            blurRadius: 28,
            offset: Offset(0, 14),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: accent),
          const SizedBox(height: 18),
          Text(
            title,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: MapFirstPalette.textMuted,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w800,
            ),
          ),
          if (helper != null) ...[
            const SizedBox(height: 8),
            Text(
              helper!,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: MapFirstPalette.textMuted,
                height: 1.4,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class MapFirstActionTile extends StatelessWidget {
  const MapFirstActionTile({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.trailingText,
    this.onTap,
    this.iconColor = MapFirstPalette.brandBlue,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final String? trailingText;
  final VoidCallback? onTap;
  final Color iconColor;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(24),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: iconColor.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(14),
              ),
              alignment: Alignment.center,
              child: Icon(icon, color: iconColor, size: 20),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: MapFirstPalette.textStrong,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle!,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: MapFirstPalette.textMuted,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            if (trailingText != null) ...[
              const SizedBox(width: 12),
              Text(
                trailingText!,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: MapFirstPalette.textMuted,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
            const SizedBox(width: 10),
            const Icon(
              Icons.chevron_right_rounded,
              color: MapFirstPalette.iconMuted,
            ),
          ],
        ),
      ),
    );
  }
}

class MapFirstEditorialStateView extends StatelessWidget {
  const MapFirstEditorialStateView({
    super.key,
    required this.title,
    required this.message,
    required this.primaryActionLabel,
    required this.onPrimaryAction,
    this.secondaryActionLabel,
    this.onSecondaryAction,
    this.issues,
    this.icon = Icons.route_outlined,
    this.iconColor = MapFirstPalette.brandBlue,
    this.showTopBar = true,
    this.compact = false,
  });

  final String title;
  final String message;
  final String primaryActionLabel;
  final VoidCallback? onPrimaryAction;
  final String? secondaryActionLabel;
  final VoidCallback? onSecondaryAction;
  final List<String>? issues;
  final IconData icon;
  final Color iconColor;
  final bool showTopBar;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final illustrationSize = compact ? 168.0 : 256.0;
    final iconBoxSize = compact ? 70.0 : 88.0;
    final iconSize = compact ? 34.0 : 40.0;
    final titleStyle =
        compact
            ? Theme.of(context).textTheme.headlineSmall
            : Theme.of(context).textTheme.headlineMedium;

    return Padding(
      padding:
          compact
              ? const EdgeInsets.fromLTRB(0, 8, 0, 18)
              : const EdgeInsets.fromLTRB(24, 24, 24, 140),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (showTopBar) ...[
            const MapFirstBrandedTopBar(
              showDivider: false,
              trailing: Icon(
                Icons.notifications_none_rounded,
                color: MapFirstPalette.iconMuted,
              ),
            ),
            const SizedBox(height: 24),
          ],
          Center(
            child: Container(
              width: illustrationSize,
              height: illustrationSize,
              decoration: BoxDecoration(
                color: MapFirstPalette.surface,
                borderRadius: BorderRadius.circular(28),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x66070C12),
                    blurRadius: 32,
                    offset: Offset(0, 12),
                  ),
                ],
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    width: iconBoxSize,
                    height: iconBoxSize,
                    decoration: BoxDecoration(
                      color: iconColor.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(28),
                    ),
                    alignment: Alignment.center,
                    child: Icon(icon, size: iconSize, color: iconColor),
                  ),
                  SizedBox(height: compact ? 12 : 16),
                  Container(
                    width: 52,
                    height: 4,
                    decoration: BoxDecoration(
                      color: MapFirstPalette.border,
                      borderRadius: BorderRadius.circular(99),
                    ),
                  ),
                ],
              ),
            ),
          ),
          SizedBox(height: compact ? 18 : 28),
          Text(
            title,
            textAlign: TextAlign.center,
            style: titleStyle?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.55,
            ),
          ),
          const SizedBox(height: 10),
          MapFirstPrimaryButton(
            label: primaryActionLabel,
            icon: Icons.settings_outlined,
            onPressed: onPrimaryAction,
            backgroundColor: MapFirstPalette.brandBlue,
          ),
          if (secondaryActionLabel != null) ...[
            const SizedBox(height: 10),
            Center(
              child: TextButton(
                onPressed: onSecondaryAction,
                child: Text(
                  secondaryActionLabel!,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: MapFirstPalette.brandBlue,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ],
          if (issues != null && issues!.isNotEmpty) ...[
            const SizedBox(height: 28),
            MapFirstSurfaceCard(
              color: MapFirstPalette.surfaceRaised,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 32,
                        height: 32,
                        decoration: const BoxDecoration(
                          color: MapFirstPalette.surface,
                          shape: BoxShape.circle,
                        ),
                        alignment: Alignment.center,
                        child: const Icon(
                          Icons.help_outline_rounded,
                          color: MapFirstPalette.brandBlue,
                          size: 18,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Text(
                        AppStrings.passageiro.problemasComuns,
                        style: Theme.of(
                          context,
                        ).textTheme.titleMedium?.copyWith(
                          color: MapFirstPalette.textStrong,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  for (final issue in issues!)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            margin: const EdgeInsets.only(top: 8),
                            width: 4,
                            height: 4,
                            decoration: const BoxDecoration(
                              color: MapFirstPalette.iconMuted,
                              shape: BoxShape.circle,
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              issue,
                              style: Theme.of(context).textTheme.bodyMedium
                                  ?.copyWith(color: MapFirstPalette.textMuted),
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}
