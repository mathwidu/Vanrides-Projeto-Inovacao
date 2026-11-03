import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/account_profile_screen.dart';
import 'package:vanrides_frontend_dev/features/support/data/support_ticket_service.dart';
import 'package:vanrides_frontend_dev/features/support/presentation/support_ticket_account_section.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class PassengerHomeAccountTab extends StatelessWidget {
  const PassengerHomeAccountTab({
    super.key,
    required this.nome,
    required this.email,
    this.enabled = false,
    this.accountProfileGateway,
    this.supportTicketGateway,
    required this.trip,
    required this.dayTrips,
    required this.onLogout,
  });

  final String nome;
  final String email;
  final bool enabled;
  final AccountProfileGateway? accountProfileGateway;
  final SupportTicketGateway? supportTicketGateway;
  final TripTrackingRecord? trip;
  final List<TripTrackingRecord> dayTrips;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    return AccountProfileScreen(
      surfaceKey: const Key('passenger.account.darkSurface'),
      editButtonKey: const Key('passenger.account.editProfileButton'),
      logoutButtonKey: const Key('passenger.account.logout.darkButton'),
      fallbackName: nome,
      fallbackEmail: email,
      roleFallback: AppStrings.passageiro.passenger,
      avatarFallback: AppStrings.passageiro.avatarFallback,
      roleIcon: Icons.directions_walk_rounded,
      enabled: enabled,
      accountProfileGateway: accountProfileGateway,
      roleSectionsBuilder:
          (context, profile) => [
            _PassengerAccountRoleSection(profile: profile, trip: trip),
            SupportTicketAccountSection(
              gateway: supportTicketGateway,
              enabled: enabled,
            ),
          ],
      onLogout: onLogout,
    );
  }
}

class _PassengerAccountRoleSection extends StatelessWidget {
  const _PassengerAccountRoleSection({
    required this.profile,
    required this.trip,
  });

  final AccountProfile? profile;
  final TripTrackingRecord? trip;

  @override
  Widget build(BuildContext context) {
    final stop = trip?.paradaDoPassageiro;
    final hasProfile = profile != null;
    final companyName = profile?.empresa?.nome;
    final hasCompany = companyName != null;
    final isMinor = profile?.menorDeIdade == true;
    final knowsResponsibility = profile?.menorDeIdade != null;

    return AccountProfileInfoCard(
      children: [
        AccountProfileInfoRow(
          icon: Icons.route_outlined,
          title: 'Jornada',
          value:
              !hasProfile
                  ? 'Dados do login em uso'
                  : hasCompany
                  ? companyName
                  : 'Empresa não vinculada',
          helper:
              !hasProfile
                  ? 'Quando o perfil real carregar, o vínculo aparece aqui.'
                  : hasCompany
                  ? 'A operação da sua van aparece na aba Viagem.'
                  : 'Procure a empresa para liberar viagens no app.',
          actionRequired: hasProfile && !hasCompany,
        ),
        AccountProfileInfoRow(
          icon: Icons.location_on_outlined,
          title: 'Parada de hoje',
          value:
              stop == null ? 'Aguardando rota do dia' : 'Parada ${stop.ordem}',
          helper:
              stop == null
                  ? 'Quando a empresa publicar a viagem, sua parada aparece no mapa.'
                  : 'Confira a posição da van na aba Viagem.',
          actionRequired: stop == null,
        ),
        AccountProfileInfoRow(
          icon: Icons.family_restroom_outlined,
          title: 'Responsabilidade',
          value:
              knowsResponsibility
                  ? isMinor
                      ? 'Menor de idade'
                      : 'Passageiro adulto'
                  : 'Aguardando perfil',
          helper:
              knowsResponsibility && isMinor
                  ? 'Responsáveis vinculados acompanham presença e localização.'
                  : 'A data de nascimento ajuda a definir vínculos de responsável.',
          actionRequired: !knowsResponsibility,
        ),
      ],
    );
  }
}
