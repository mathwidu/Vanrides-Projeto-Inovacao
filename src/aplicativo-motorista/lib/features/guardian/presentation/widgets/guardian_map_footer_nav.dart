import 'package:flutter/material.dart';

import '../../../../app/theme/vanrides_motion.dart';
import '../../../../l10n/app_strings.dart';
import '../../../tracking/presentation/widgets/vanrides_map_tokens.dart';

class GuardianBottomNavContent extends StatelessWidget {
  const GuardianBottomNavContent({
    super.key,
    required this.selectedIndex,
    required this.onDestinationSelected,
  });

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(6),
      child: Row(
        children: [
          _NavItem(
            index: 0,
            icon: Icons.home_outlined,
            selectedIcon: Icons.home,
            label: AppStrings.guardian.inicio,
            selected: selectedIndex == 0,
            onTap: () => onDestinationSelected(0),
          ),
          _NavItem(
            index: 1,
            icon: Icons.fact_check_outlined,
            selectedIcon: Icons.fact_check,
            label: AppStrings.guardian.presencas,
            selected: selectedIndex == 1,
            onTap: () => onDestinationSelected(1),
          ),
          _NavItem(
            index: 2,
            icon: Icons.location_on_outlined,
            selectedIcon: Icons.location_on,
            label: AppStrings.guardian.localizacao,
            selected: selectedIndex == 2,
            onTap: () => onDestinationSelected(2),
          ),
          _NavItem(
            index: 3,
            icon: Icons.person_outline,
            selectedIcon: Icons.person,
            label: AppStrings.guardian.conta,
            selected: selectedIndex == 3,
            onTap: () => onDestinationSelected(3),
          ),
        ],
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    required this.index,
    required this.icon,
    required this.selectedIcon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final int index;
  final IconData icon;
  final IconData selectedIcon;
  final String label;
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
    return Expanded(
      child: Semantics(
        button: true,
        selected: selected,
        label: label,
        onTap: onTap,
        child: ExcludeSemantics(
          child: InkWell(
            key: Key('guardian.map.footerNav.$index'),
            borderRadius: BorderRadius.circular(20),
            onTap: onTap,
            child: AnimatedContainer(
              duration: motion.duration(
                VanRidesMotionTokens.navigationSelection,
              ),
              curve: motion.curve(VanRidesMotionTokens.easeOutQuart),
              constraints: const BoxConstraints(minHeight: 52),
              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 7),
              decoration: BoxDecoration(
                color: background,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    selected ? selectedIcon : icon,
                    color: foreground,
                    size: 20,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    label,
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
      ),
    );
  }
}
