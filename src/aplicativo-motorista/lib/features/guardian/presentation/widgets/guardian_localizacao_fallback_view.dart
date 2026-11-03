import 'package:flutter/material.dart';

import '../../../../l10n/app_strings.dart';
import '../../../tracking/presentation/map_first_v2_components.dart';
import '../../../tracking/presentation/map_first_v2_shell.dart';
import '../../domain/guardian_linked_passageiro_record.dart';
import 'guardian_localizacao_widgets.dart';

class GuardianLocalizacaoFallbackView extends StatelessWidget {
  const GuardianLocalizacaoFallbackView({
    super.key,
    required this.loadingPassageiros,
    required this.passageiros,
    required this.selectedPassageiro,
    required this.passageirosError,
    required this.trackingContent,
    required this.selectedNavIndex,
    required this.onRefresh,
    required this.onPassageiroSelected,
    required this.onDestinationSelected,
  });

  final bool loadingPassageiros;
  final List<GuardianLinkedPassageiroRecord> passageiros;
  final GuardianLinkedPassageiroRecord? selectedPassageiro;
  final String? passageirosError;
  final List<Widget> trackingContent;
  final int selectedNavIndex;
  final Future<void> Function() onRefresh;
  final ValueChanged<GuardianLinkedPassageiroRecord> onPassageiroSelected;
  final ValueChanged<int> onDestinationSelected;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        RefreshIndicator(
          onRefresh: onRefresh,
          child: ListView(
            key: const ValueKey('guardian.localizacao'),
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 132),
            children: [
              Text(
                AppStrings.guardian.rastreioDaViagem,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 12),
              Text(AppStrings.guardian.rastreioDescricao),
              const SizedBox(height: 20),
              if (loadingPassageiros)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 48),
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (passageirosError != null)
                MapFirstStateCard(
                  icon: Icons.warning_amber_outlined,
                  title: AppStrings.guardian.erroAoCarregarPassageiros,
                  message: passageirosError!,
                  actionLabel: AppStrings.guardian.tentarNovamente,
                  onAction: onRefresh,
                  color: MapFirstPalette.red,
                )
              else if (passageiros.isEmpty)
                MapFirstStateCard(
                  icon: Icons.people_outline,
                  title: AppStrings.guardian.nenhumPassageiroVinculado,
                  message:
                      AppStrings
                          .guardian
                          .nenhumPassageiroVinculadoRastreioDescricao,
                )
              else ...[
                Text(
                  AppStrings.guardian.selecionarPassageiro,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: passageiros
                      .map(
                        (passageiro) => ChoiceChip(
                          label: Text(passageiro.nome),
                          selected: selectedPassageiro?.id == passageiro.id,
                          onSelected: (_) => onPassageiroSelected(passageiro),
                        ),
                      )
                      .toList(growable: false),
                ),
                const SizedBox(height: 20),
                GuardianLocalizacaoSelectedPassageiroCard(
                  passageiro: selectedPassageiro!,
                ),
                const SizedBox(height: 20),
                ...trackingContent,
              ],
            ],
          ),
        ),
        Align(
          alignment: Alignment.bottomCenter,
          child: MapFirstBottomNavBar(
            selectedIndex: selectedNavIndex,
            onDestinationSelected: onDestinationSelected,
            destinations: [
              MapFirstNavDestination(
                icon: Icons.home_outlined,
                selectedIcon: Icons.home,
                label: AppStrings.guardian.inicio,
              ),
              MapFirstNavDestination(
                icon: Icons.fact_check_outlined,
                selectedIcon: Icons.fact_check,
                label: AppStrings.guardian.presencas,
              ),
              MapFirstNavDestination(
                icon: Icons.location_on_outlined,
                selectedIcon: Icons.location_on,
                label: AppStrings.guardian.localizacao,
              ),
              MapFirstNavDestination(
                icon: Icons.person_outline,
                selectedIcon: Icons.person,
                label: AppStrings.guardian.conta,
              ),
            ],
          ),
        ),
      ],
    );
  }
}
