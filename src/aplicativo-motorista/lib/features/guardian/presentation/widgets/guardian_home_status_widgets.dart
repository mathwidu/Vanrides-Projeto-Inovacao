import 'package:flutter/material.dart';

import '../../../../l10n/app_strings.dart';
import '../../domain/guardian_linked_passageiro_record.dart';
import '../../domain/guardian_localizacao_snapshot.dart';
import '../../domain/guardian_presenca_record.dart';
import '../../../tracking/presentation/map_first_v2_components.dart';
import 'guardian_localizacao_widgets.dart';
import 'guardian_presencas_summary_widgets.dart';

class GuardianHomePassengerStatus {
  const GuardianHomePassengerStatus({
    this.presencaHoje,
    this.ultimaLocalizacao,
  });

  final GuardianPresencaRecord? presencaHoje;
  final GuardianLocalizacaoSnapshot? ultimaLocalizacao;
}

class GuardianHomeStatusWrap extends StatelessWidget {
  const GuardianHomeStatusWrap({
    super.key,
    required this.passageiro,
    required this.status,
    this.compact = false,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final GuardianHomePassengerStatus? status;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final summary = status ?? const GuardianHomePassengerStatus();
    final presenceConfirmed = summary.presencaHoje?.confirmado == true;
    final presenceLabel =
        presenceConfirmed
            ? AppStrings.guardian.presencaConfirmadaHoje
            : AppStrings.guardian.presencaPendenteHoje;
    final locationLabel = _locationLabel(summary, passageiro);
    final nextAction =
        presenceConfirmed
            ? AppStrings.guardian.acompanharLocalizacao
            : AppStrings.guardian.revisarPresenca;

    return Wrap(
      spacing: compact ? 8 : 10,
      runSpacing: compact ? 8 : 10,
      children: [
        GuardianPresenceStatusBadge(
          label: presenceLabel,
          confirmed: presenceConfirmed,
        ),
        GuardianLocationStatusBadge(
          label: locationLabel,
          live: summary.ultimaLocalizacao != null,
        ),
        _GuardianNextActionPill(label: nextAction),
      ],
    );
  }

  String _locationLabel(
    GuardianHomePassengerStatus status,
    GuardianLinkedPassageiroRecord passageiro,
  ) {
    final ultimaLocalizacao = status.ultimaLocalizacao;
    if (ultimaLocalizacao != null) {
      return AppStrings.guardian.ultimaPosicaoAs(
        _formatTime(ultimaLocalizacao.dataHora),
      );
    }
    if (passageiro.motoristaId == null) {
      return AppStrings.guardian.viagemDefineMotorista;
    }
    return AppStrings.guardian.aguardandoSinalDaVan;
  }

  String _formatTime(DateTime value) {
    final hour = value.hour.toString().padLeft(2, '0');
    final minute = value.minute.toString().padLeft(2, '0');
    return '$hour:$minute';
  }
}

class _GuardianNextActionPill extends StatelessWidget {
  const _GuardianNextActionPill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
      decoration: BoxDecoration(
        color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.78),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: MapFirstPalette.border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.touch_app_outlined,
            size: 17,
            color: MapFirstPalette.brandBlue,
          ),
          const SizedBox(width: 7),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 220),
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
