import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../../tracking/presentation/map_first_v2_components.dart';
import '../../tracking/presentation/map_first_v2_shell.dart';
import '../domain/guardian_linked_passageiro_record.dart';

class GuardianPassageiroDetailScreen extends StatelessWidget {
  const GuardianPassageiroDetailScreen({super.key, required this.passageiro});

  final GuardianLinkedPassageiroRecord passageiro;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: MapFirstPalette.background,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 40),
          children: [
            Row(
              children: [
                MapFirstIconButton(
                  icon: Icons.arrow_back_rounded,
                  tooltip: AppStrings.passageiro.voltar,
                  onPressed: () => Navigator.of(context).pop(),
                ),
                const SizedBox(width: 12),
                Text(
                  AppStrings.guardian.passageiroVinculado,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),
            Center(
              child: MapFirstAvatar(
                label: passageiro.nome,
                size: 112,
                backgroundColor: MapFirstPalette.blue,
              ),
            ),
            const SizedBox(height: 20),
            Text(
              passageiro.nome,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _TagChip(
                    label:
                        passageiro.menorDeIdade
                            ? AppStrings.guardian.menorDeIdade
                            : AppStrings.guardian.passageiroAdulto,
                    color:
                        passageiro.menorDeIdade
                            ? MapFirstPalette.redSoft
                            : MapFirstPalette.blueSoft,
                  ),
                ),
                if ((passageiro.motoristaNome ?? '').isNotEmpty) ...[
                  const SizedBox(width: 8),
                  Expanded(
                    child: _TagChip(
                      label: AppStrings.guardian.motoristaAssociado(
                        passageiro.motoristaNome!,
                      ),
                      color: MapFirstPalette.greenSoft,
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: MapFirstMetricBentoCard(
                    title: AppStrings.guardian.status,
                    value:
                        passageiro.menorDeIdade
                            ? AppStrings.guardian.aluno
                            : AppStrings.guardian.passageiroAdulto,
                    icon: Icons.person_outline_rounded,
                    accent: const Color(0xFFD92E6B),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: MapFirstMetricBentoCard(
                    title: AppStrings.guardian.vinculo,
                    value: AppStrings.guardian.vinculoAtivo,
                    icon: Icons.link_rounded,
                    accent: MapFirstPalette.brandBlue,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            MapFirstSurfaceCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    AppStrings.guardian.dadosBasicos,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: MapFirstPalette.textStrong,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 14),
                  _DetailRow(
                    label: AppStrings.guardian.email,
                    value: passageiro.email,
                  ),
                  _DetailRow(
                    label: AppStrings.guardian.telefone,
                    value: passageiro.telefone,
                  ),
                  _DetailRow(
                    label: AppStrings.guardian.enderecoLabel,
                    value: passageiro.enderecoResumo,
                  ),
                  _DetailRow(
                    label: AppStrings.guardian.cep,
                    value: passageiro.cep,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            MapFirstSurfaceCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    AppStrings.guardian.contextoDaFamilia,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: MapFirstPalette.textStrong,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(AppStrings.guardian.contextoDaFamiliaDescricao),
                  const SizedBox(height: 16),
                  OutlinedButton.icon(
                    onPressed: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            AppStrings.guardian.abaPresencasDescricao,
                          ),
                        ),
                      );
                    },
                    icon: const Icon(Icons.fact_check_outlined),
                    label: Text(AppStrings.guardian.abrirAbaPresencas),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            AppStrings.guardian.abaLocalizacaoDescricao,
                          ),
                        ),
                      );
                    },
                    icon: const Icon(Icons.location_on_outlined),
                    label: Text(AppStrings.guardian.abrirAbaLocalizacao),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 90,
            child: Text(
              label,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: MapFirstPalette.textMuted,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value.isEmpty ? AppStrings.guardian.naoInformado : value,
            ),
          ),
        ],
      ),
    );
  }
}

class _TagChip extends StatelessWidget {
  const _TagChip({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: color,
        border: Border.all(color: MapFirstPalette.border),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text(
        label,
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: MapFirstPalette.textStrong,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
