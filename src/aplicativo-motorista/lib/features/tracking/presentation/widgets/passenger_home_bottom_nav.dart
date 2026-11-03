import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

import '../map_first_v2_shell.dart';

class PassengerHomeBottomNav extends StatelessWidget {
  const PassengerHomeBottomNav({
    super.key,
    required this.selectedIndex,
    required this.onDestinationSelected,
  });

  static const double overlayClearance = MapFirstBottomNavBar.overlayClearance;

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;

  static List<MapFirstNavDestination> get destinations => [
    MapFirstNavDestination(
      icon: Icons.explore_outlined,
      selectedIcon: Icons.explore,
      label: AppStrings.passageiro.viagemTab,
    ),
    MapFirstNavDestination(
      icon: Icons.fact_check_outlined,
      selectedIcon: Icons.fact_check,
      label: AppStrings.passageiro.presencaTab,
    ),
    MapFirstNavDestination(
      icon: Icons.person_outline,
      selectedIcon: Icons.person,
      label: AppStrings.passageiro.contaTab,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return MapFirstBottomNavBar(
      selectedIndex: selectedIndex,
      onDestinationSelected: onDestinationSelected,
      destinations: destinations,
    );
  }
}

class PassengerHomeBottomNavContent extends StatelessWidget {
  const PassengerHomeBottomNavContent({
    super.key,
    required this.selectedIndex,
    required this.onDestinationSelected,
  });

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;

  @override
  Widget build(BuildContext context) {
    return MapFirstBottomNavItems(
      selectedIndex: selectedIndex,
      onDestinationSelected: onDestinationSelected,
      destinations: PassengerHomeBottomNav.destinations,
    );
  }
}
