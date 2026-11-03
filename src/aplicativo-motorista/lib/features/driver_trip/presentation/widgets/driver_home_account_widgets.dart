import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/account_profile_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverAccountTab extends StatelessWidget {
  const DriverAccountTab({
    super.key,
    required this.nome,
    required this.email,
    this.enabled = false,
    this.accountProfileGateway,
    required this.trip,
    required this.dayTrips,
    required this.onLogout,
  });

  final String nome;
  final String email;
  final bool enabled;
  final AccountProfileGateway? accountProfileGateway;
  final DriverTripRecord? trip;
  final List<DriverTripRecord> dayTrips;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    return AccountProfileScreen(
      surfaceKey: const Key('driver.account.darkSurface'),
      editButtonKey: const Key('driver.account.editProfileButton'),
      logoutButtonKey: const Key('driver.account.logout.darkButton'),
      fallbackName: nome,
      fallbackEmail: email,
      roleFallback: AppStrings.motorista.motoristaOperacional,
      avatarFallback: AppStrings.motorista.avatarFallback,
      roleIcon: Icons.directions_bus_rounded,
      enabled: enabled,
      accountProfileGateway: accountProfileGateway,
      roleSectionsBuilder:
          (context, profile) => [
            _DriverAccountRoleSection(profile: profile, trip: trip),
          ],
      onLogout: onLogout,
    );
  }
}

class _DriverAccountRoleSection extends StatelessWidget {
  const _DriverAccountRoleSection({required this.profile, required this.trip});

  final AccountProfile? profile;
  final DriverTripRecord? trip;

  @override
  Widget build(BuildContext context) {
    final hasProfile = profile != null;
    final hasCompany = profile?.empresa != null;
    final hasTrip = trip != null;

    return AccountProfileInfoCard(
      children: [
        AccountProfileInfoRow(
          icon: Icons.apartment_outlined,
          title: 'Empresa operacional',
          value:
              !hasProfile
                  ? 'Dados do login em uso'
                  : profile?.empresa?.nome ?? 'Aguardando vínculo',
          helper:
              !hasProfile
                  ? 'Quando o perfil real carregar, o vínculo aparece aqui.'
                  : hasCompany
                  ? 'Você está liberado para operar viagens desta empresa.'
                  : 'Entre por convite ou solicite vínculo com uma empresa.',
          actionRequired: hasProfile && !hasCompany,
        ),
        AccountProfileInfoRow(
          icon: Icons.directions_bus_filled_outlined,
          title: 'Próxima operação',
          value:
              hasTrip
                  ? '${trip!.horarioPartida} • ${trip!.tipoOperacao}'
                  : 'Sem viagem publicada',
          helper:
              hasTrip
                  ? 'Use a aba Viagem para compartilhar localização e acompanhar paradas.'
                  : 'Quando a empresa publicar uma viagem, ela aparece na tela inicial.',
          actionRequired: !hasTrip,
        ),
      ],
    );
  }
}
