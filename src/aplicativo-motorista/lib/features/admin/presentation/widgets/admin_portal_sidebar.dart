part of 'admin_portal_layout.dart';

class _AdminPortalSidebar extends StatelessWidget {
  const _AdminPortalSidebar({
    required this.selectedDestination,
    required this.onDestinationSelected,
    this.onLogout,
  });

  final AdminPortalDestination selectedDestination;
  final ValueChanged<AdminPortalDestination> onDestinationSelected;
  final VoidCallback? onLogout;

  @override
  Widget build(BuildContext context) {
    const operationDestinations = [
      AdminPortalDestination.dashboard,
      AdminPortalDestination.viagens,
      AdminPortalDestination.servicos,
    ];
    const peopleDestinations = [
      AdminPortalDestination.passageiros,
      AdminPortalDestination.motoristas,
      AdminPortalDestination.responsaveis,
    ];
    const accountDestinations = [
      AdminPortalDestination.suporte,
      AdminPortalDestination.conta,
    ];

    return Container(
      width: 256,
      decoration: const BoxDecoration(
        color: AdminPortalColors.sidebar,
        border: Border(right: BorderSide(color: AdminPortalColors.divider)),
      ),
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'VanRides',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                SizedBox(height: 2),
                Text(
                  'Portal da empresa',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          Expanded(
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                const _AdminSidebarSectionLabel('Operação'),
                for (final destination in operationDestinations)
                  _AdminSidebarItem(
                    destination: destination,
                    active: destination == selectedDestination,
                    onTap: () => onDestinationSelected(destination),
                  ),
                const SizedBox(height: 14),
                const _AdminSidebarSectionLabel('Pessoas'),
                for (final destination in peopleDestinations)
                  _AdminSidebarItem(
                    destination: destination,
                    active: destination == selectedDestination,
                    onTap: () => onDestinationSelected(destination),
                  ),
                const SizedBox(height: 14),
                const _AdminSidebarSectionLabel('Empresa'),
                for (final destination in accountDestinations)
                  _AdminSidebarItem(
                    destination: destination,
                    active: destination == selectedDestination,
                    onTap: () => onDestinationSelected(destination),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _AdminPortalAdvancedLinks(
            selectedDestination: selectedDestination,
            onDestinationSelected: onDestinationSelected,
          ),
          TextButton.icon(
            onPressed:
                onLogout ??
                () {
                  Navigator.of(context).pushAndRemoveUntil(
                    MaterialPageRoute(builder: (_) => const AdminLoginScreen()),
                    (route) => false,
                  );
                },
            icon: const Icon(Icons.logout),
            label: const Text('Sair'),
            style: TextButton.styleFrom(
              foregroundColor: AdminPortalColors.red,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminSidebarSectionLabel extends StatelessWidget {
  const _AdminSidebarSectionLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Text(
        label,
        style: const TextStyle(
          color: AdminPortalColors.textFaint,
          fontSize: 11,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.8,
        ),
      ),
    );
  }
}

class _AdminPortalAdvancedLinks extends StatelessWidget {
  const _AdminPortalAdvancedLinks({
    required this.selectedDestination,
    required this.onDestinationSelected,
  });

  final AdminPortalDestination selectedDestination;
  final ValueChanged<AdminPortalDestination> onDestinationSelected;

  @override
  Widget build(BuildContext context) {
    const advancedDestinations = [
      AdminPortalDestination.rotas,
      AdminPortalDestination.vinculos,
    ];

    return Container(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
      decoration: BoxDecoration(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: AdminPortalColors.outline.withValues(alpha: 0.42),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Avançado',
            style: TextStyle(
              color: AdminPortalColors.textFaint,
              fontSize: 11,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 6),
          for (final destination in advancedDestinations)
            _AdminAdvancedSidebarItem(
              destination: destination,
              active: destination == selectedDestination,
              onTap: () => onDestinationSelected(destination),
            ),
        ],
      ),
    );
  }
}

class _AdminAdvancedSidebarItem extends StatelessWidget {
  const _AdminAdvancedSidebarItem({
    required this.destination,
    required this.active,
    required this.onTap,
  });

  final AdminPortalDestination destination;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 6),
        child: Row(
          children: [
            Icon(
              destination.icon,
              size: 15,
              color:
                  active ? AdminPortalColors.blue : AdminPortalColors.textFaint,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                destination.label,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color:
                      active
                          ? AdminPortalColors.blue
                          : AdminPortalColors.textFaint,
                  fontSize: 12,
                  fontWeight: active ? FontWeight.w800 : FontWeight.w600,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AdminSidebarItem extends StatelessWidget {
  const _AdminSidebarItem({
    required this.destination,
    required this.active,
    required this.onTap,
  });

  final AdminPortalDestination destination;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color:
            active
                ? AdminPortalColors.blueSoft.withValues(alpha: 0.86)
                : Colors.transparent,
        borderRadius: BorderRadius.circular(999),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(999),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: Row(
              children: [
                Icon(
                  destination.icon,
                  size: 18,
                  color:
                      active
                          ? AdminPortalColors.blue
                          : AdminPortalColors.textMuted,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    destination.label,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color:
                          active
                              ? AdminPortalColors.blue
                              : AdminPortalColors.textMuted,
                      fontSize: 14,
                      fontWeight: active ? FontWeight.w800 : FontWeight.w500,
                    ),
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
