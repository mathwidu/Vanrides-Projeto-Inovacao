import 'package:flutter/material.dart';

import '../../domain/admin_viagem_record.dart';
import 'admin_viagens_day_status_panel.dart';
import 'admin_viagens_maintenance_guide.dart';
import 'admin_viagens_manifest_status_widgets.dart';
import 'admin_portal_layout.dart';
import 'admin_viagens_status_widgets.dart';

class AdminViagensOperationalListSection extends StatelessWidget {
  const AdminViagensOperationalListSection({
    super.key,
    required this.viagens,
    required this.inProgressCount,
    required this.completedCount,
    required this.isGeneratingAutomatic,
    required this.onGenerateAutomatic,
    required this.onCreateManual,
    required this.onOpenDetail,
    this.generationResultPanel,
  });

  final List<AdminViagemRecord> viagens;
  final int inProgressCount;
  final int completedCount;
  final bool isGeneratingAutomatic;
  final VoidCallback onGenerateAutomatic;
  final VoidCallback onCreateManual;
  final ValueChanged<int> onOpenDetail;
  final Widget? generationResultPanel;

  @override
  Widget build(BuildContext context) {
    final reviewCount =
        viagens
            .where(
              (viagem) =>
                  viagem.manifesto.isPending || viagem.manifesto.isBlocked,
            )
            .length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AdminViagensDayStatusPanel(
          viagensCount: viagens.length,
          inProgressCount: inProgressCount,
          completedCount: completedCount,
          reviewCount: reviewCount,
          isGeneratingAutomatic: isGeneratingAutomatic,
          onGenerateAutomatic: onGenerateAutomatic,
          onCreateManual: onCreateManual,
        ),
        const SizedBox(height: 20),
        if (generationResultPanel != null) ...[
          generationResultPanel!,
          const SizedBox(height: 24),
        ],
        if (viagens.isNotEmpty) ...[
          AdminViagensMaintenanceGuide(viagensCount: viagens.length),
          const SizedBox(height: 24),
        ],
        AdminPortalSurface(
          radius: 48,
          padding: EdgeInsets.zero,
          child: Column(
            children: [
              const Padding(
                padding: EdgeInsets.fromLTRB(32, 24, 32, 24),
                child: Row(
                  children: [
                    Icon(
                      Icons.calendar_view_day_rounded,
                      color: AdminPortalColors.blue,
                    ),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Viagens do dia',
                        style: TextStyle(
                          color: AdminPortalColors.text,
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    Flexible(
                      child: Text(
                        'Abra uma viagem para trocar motorista, revisar paradas e acompanhar execução.',
                        textAlign: TextAlign.right,
                        style: TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 13,
                          height: 1.35,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                color: AdminPortalColors.surfaceSoft.withValues(alpha: 0.72),
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: const Row(
                  children: [
                    AdminViagensTableHeaderCell(
                      flex: 2,
                      label: 'DATA &\nHORÁRIO',
                    ),
                    AdminViagensTableHeaderCell(flex: 3, label: 'SERVIÇO'),
                    AdminViagensTableHeaderCell(flex: 2, label: 'MOTORISTA'),
                    AdminViagensTableHeaderCell(
                      flex: 2,
                      label: 'PARADAS',
                      alignEnd: true,
                    ),
                    AdminViagensTableHeaderCell(flex: 2, label: 'STATUS'),
                    AdminViagensTableHeaderCell(
                      flex: 2,
                      label: 'PRÓXIMO PASSO',
                      alignEnd: true,
                    ),
                  ],
                ),
              ),
              if (viagens.isEmpty)
                const _AdminViagensEmptyState()
              else
                ...viagens.map(
                  (viagem) => AdminViagensTripRow(
                    viagem: viagem,
                    onView: () => onOpenDetail(viagem.id),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.fromLTRB(32, 24, 32, 24),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Exibindo ${viagens.isEmpty ? 0 : 1}-${viagens.length} de ${viagens.length} viagens registradas',
                        style: const TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                    const Icon(
                      Icons.check_circle_outline_rounded,
                      size: 18,
                      color: AdminPortalColors.green,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _AdminViagensEmptyState extends StatelessWidget {
  const _AdminViagensEmptyState();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(32),
      child: Column(
        children: [
          Text(
            'Nenhuma viagem encontrada com o filtro atual.',
            style: TextStyle(
              color: AdminPortalColors.text,
              fontSize: 20,
              fontWeight: FontWeight.w800,
            ),
          ),
          SizedBox(height: 8),
          Text(
            'Monte o dia para trazer as viagens recorrentes. Use Exceção do dia só para algo fora do plano.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AdminPortalColors.textMuted, fontSize: 15),
          ),
        ],
      ),
    );
  }
}

class AdminViagensTripRow extends StatelessWidget {
  const AdminViagensTripRow({
    super.key,
    required this.viagem,
    required this.onView,
  });

  final AdminViagemRecord viagem;
  final VoidCallback onView;

  @override
  Widget build(BuildContext context) {
    final needsManifestReview =
        viagem.manifesto.isPending || viagem.manifesto.isBlocked;
    final actionLabel = needsManifestReview ? 'Revisar lista' : 'Abrir';
    final actionIcon =
        needsManifestReview
            ? Icons.sync_problem_rounded
            : Icons.visibility_outlined;
    final actionTooltip =
        '${needsManifestReview ? 'Revisar lista' : 'Abrir viagem'} ${viagem.nomeOperacional}';

    return Container(
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: Color(0x14C2C6D6))),
      ),
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Row(
        children: [
          _AdminViagensTripInfoCell(
            flex: 2,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    adminViagensFormatLongDate(viagem.dataOperacao),
                    style: const TextStyle(
                      color: AdminPortalColors.text,
                      fontSize: 14,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    viagem.horarioPartida ?? '—',
                    style: const TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
          ),
          _AdminViagensTripInfoCell(
            flex: 3,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: AdminViagensTripServiceSummary(viagem: viagem),
            ),
          ),
          _AdminViagensTripInfoCell(
            flex: 2,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 16,
                    backgroundColor: AdminPortalColors.surfaceSoft,
                    child: Text(
                      adminViagensInitial(viagem.motorista.nome),
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      viagem.motorista.nome,
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          _AdminViagensTripInfoCell(
            flex: 2,
            child: Align(
              alignment: Alignment.centerRight,
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 32),
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
                decoration: BoxDecoration(
                  color: AdminPortalColors.surfaceSoft,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  '${viagem.quantidadeParadas.toString().padLeft(2, '0')} paradas',
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ),
          _AdminViagensTripInfoCell(
            flex: 2,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: AdminViagensStatusTag(status: viagem.status),
            ),
          ),
          _AdminViagensTripInfoCell(
            flex: 2,
            child: Align(
              alignment: Alignment.centerRight,
              child: Padding(
                padding: const EdgeInsets.only(right: 24),
                child: Tooltip(
                  message: actionTooltip,
                  child: OutlinedButton.icon(
                    key: Key('admin.viagens.openTrip.${viagem.id}'),
                    onPressed: onView,
                    icon: Icon(actionIcon, size: 18),
                    label: Text(actionLabel),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AdminPortalColors.blue,
                      backgroundColor: AdminPortalColors.surfaceSoft.withValues(
                        alpha: 0.74,
                      ),
                      side: BorderSide(
                        color: AdminPortalColors.glassHighlight.withValues(
                          alpha: 0.68,
                        ),
                      ),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 10,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(999),
                      ),
                      textStyle: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminViagensTripInfoCell extends StatelessWidget {
  const _AdminViagensTripInfoCell({required this.flex, required this.child});

  final int flex;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Expanded(flex: flex, child: child);
  }
}

class AdminViagensTableHeaderCell extends StatelessWidget {
  const AdminViagensTableHeaderCell({
    super.key,
    required this.flex,
    required this.label,
    this.alignEnd = false,
  });

  final int flex;
  final String label;
  final bool alignEnd;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      flex: flex,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Text(
          label,
          textAlign: alignEnd ? TextAlign.right : TextAlign.left,
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 11,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.1,
          ),
        ),
      ),
    );
  }
}

class AdminViagensDateRangeChip extends StatelessWidget {
  const AdminViagensDateRangeChip({
    super.key,
    required this.date,
    required this.onPressed,
  });

  final DateTime? date;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        side: const BorderSide(color: Color(0x33C2C6D6)),
        backgroundColor: AdminPortalColors.surfaceSoft.withValues(alpha: 0.74),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
      ),
      icon: const Icon(
        Icons.calendar_today_outlined,
        color: AdminPortalColors.textMuted,
        size: 18,
      ),
      label: Text(
        date == null ? 'Filtrar por data' : adminViagensFormatLongDate(date!),
        style: const TextStyle(
          color: AdminPortalColors.text,
          fontSize: 14,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

String adminViagensFormatLongDate(DateTime value) {
  const months = [
    'Jan',
    'Fev',
    'Mar',
    'Abr',
    'Mai',
    'Jun',
    'Jul',
    'Ago',
    'Set',
    'Out',
    'Nov',
    'Dez',
  ];
  final day = value.day.toString().padLeft(2, '0');
  final month = months[value.month - 1];
  return '$day $month ${value.year}';
}

String adminViagensFormatShortDate(DateTime value) {
  final day = value.day.toString().padLeft(2, '0');
  final month = value.month.toString().padLeft(2, '0');
  return '$day/$month/${value.year}';
}

String adminViagensInitial(String value) {
  final trimmed = value.trim();
  if (trimmed.isEmpty) return '?';
  return trimmed.substring(0, 1).toUpperCase();
}

String adminViagensFormatFontePlanejamento(String value) {
  return switch (value.toUpperCase()) {
    'SERVICO_RECORRENTE' => 'Serviço recorrente',
    'ROTA_BASE_LEGADA' => 'Rota-base legada',
    _ => value,
  };
}

String adminViagensFormatStopStatus(String value) {
  return switch (value.toUpperCase()) {
    'PENDING' => 'pendente',
    'COMPLETED' => 'concluída',
    'SKIPPED' => 'pulada',
    _ => value.toLowerCase(),
  };
}
