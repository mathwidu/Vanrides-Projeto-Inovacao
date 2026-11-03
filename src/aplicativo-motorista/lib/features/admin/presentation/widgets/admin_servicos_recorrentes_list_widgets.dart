import 'package:flutter/material.dart';

import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_portal_layout.dart';

class AdminServicosRecorrentesHeader extends StatelessWidget {
  const AdminServicosRecorrentesHeader({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 12),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: AdminPortalColors.divider)),
      ),
      child: const Row(
        children: [
          Expanded(flex: 3, child: _AdminServicosHeaderText('SERVIÇO')),
          Expanded(flex: 2, child: _AdminServicosHeaderText('MOTORISTA')),
          Expanded(flex: 2, child: _AdminServicosHeaderText('POLÍTICA')),
          Expanded(flex: 2, child: _AdminServicosHeaderText('DIAS')),
          SizedBox(
            width: 300,
            child: _AdminServicosHeaderText('PRÓXIMO PASSO'),
          ),
        ],
      ),
    );
  }
}

class AdminServicoRecorrenteRow extends StatelessWidget {
  const AdminServicoRecorrenteRow({
    super.key,
    required this.servico,
    required this.politicaPresencaLabel,
    required this.horarioPartidaLabel,
    required this.onOpen,
    required this.onEdit,
    required this.onDelete,
  });

  final AdminServicoRecorrenteRecord servico;
  final String politicaPresencaLabel;
  final String horarioPartidaLabel;
  final VoidCallback onOpen;
  final VoidCallback onEdit;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 18),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: AdminPortalColors.divider)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            flex: 3,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      servico.nome,
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    AdminPortalTag(
                      label: servico.tipoOperacaoLabel,
                      background: AdminPortalColors.blueSoft,
                      foreground: AdminPortalColors.blue,
                    ),
                    if (!servico.ativo)
                      const AdminPortalTag(
                        label: 'Inativo',
                        background: AdminPortalColors.redSoft,
                        foreground: AdminPortalColors.red,
                      ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  '${servico.quantidadePassageiros} passageiros elegíveis • $horarioPartidaLabel',
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 13,
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            flex: 2,
            child: Text(
              servico.motoristaPadrao?.nome ?? 'Escala por viagem',
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Expanded(
            flex: 2,
            child: Text(
              politicaPresencaLabel,
              style: const TextStyle(color: AdminPortalColors.textMuted),
            ),
          ),
          Expanded(
            flex: 2,
            child: Text(
              servico.diasOperacao.isEmpty
                  ? 'Sem dias'
                  : servico.diasOperacao.join(', '),
              style: const TextStyle(color: AdminPortalColors.textMuted),
            ),
          ),
          SizedBox(
            width: 300,
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _AdminServicoRowActionButton(
                  label: 'Abrir',
                  icon: Icons.arrow_forward_rounded,
                  tooltip: 'Abrir serviço',
                  onPressed: onOpen,
                ),
                _AdminServicoRowActionButton(
                  label: 'Editar',
                  icon: Icons.edit_outlined,
                  tooltip: 'Editar serviço',
                  onPressed: onEdit,
                ),
                _AdminServicoRowActionButton(
                  label: 'Inativar',
                  icon: Icons.block_outlined,
                  tooltip: 'Inativar serviço',
                  onPressed: servico.ativo ? onDelete : null,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class AdminServicosMotoristaAlert extends StatelessWidget {
  const AdminServicosMotoristaAlert({
    super.key,
    required this.semMotorista,
    required this.firstServicoName,
    required this.onReviewFirst,
  });

  final int semMotorista;
  final String firstServicoName;
  final VoidCallback onReviewFirst;

  @override
  Widget build(BuildContext context) {
    final plural = semMotorista > 1;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(
          color: AdminPortalColors.orange.withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: AdminPortalColors.orangeSoft,
              borderRadius: BorderRadius.circular(18),
            ),
            child: const Icon(
              Icons.assignment_ind_outlined,
              color: AdminPortalColors.orange,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  plural
                      ? '$semMotorista serviços sem motorista fixo'
                      : 'Serviço sem motorista fixo',
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Revise os serviços que precisam de escala antes de montar o dia.',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 13,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 16),
          AdminPortalSecondaryButton(
            label: 'Abrir $firstServicoName',
            icon: Icons.arrow_forward_rounded,
            onPressed: onReviewFirst,
          ),
        ],
      ),
    );
  }
}

class _AdminServicoRowActionButton extends StatelessWidget {
  const _AdminServicoRowActionButton({
    required this.label,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: TextButton.icon(
        onPressed: onPressed,
        icon: Icon(icon, size: 17),
        label: Text(label),
        style: TextButton.styleFrom(
          foregroundColor: AdminPortalColors.text,
          disabledForegroundColor: AdminPortalColors.textFaint,
          backgroundColor: AdminPortalColors.surfaceSoft,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: const BorderSide(color: AdminPortalColors.divider),
          ),
          textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800),
        ),
      ),
    );
  }
}

class AdminServicosRecorrentesEmptyState extends StatelessWidget {
  const AdminServicosRecorrentesEmptyState({super.key, required this.onCreate});

  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Nenhum serviço recorrente cadastrado',
            style: TextStyle(
              color: AdminPortalColors.text,
              fontSize: 22,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Crie a primeira linha para liberar passageiros e gerar viagens do dia com menos esforço operacional.',
            style: TextStyle(color: AdminPortalColors.textMuted),
          ),
          const SizedBox(height: 20),
          AdminPortalPrimaryButton(
            label: 'Criar primeiro serviço',
            icon: Icons.add,
            onPressed: onCreate,
          ),
        ],
      ),
    );
  }
}

class _AdminServicosHeaderText extends StatelessWidget {
  const _AdminServicosHeaderText(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        color: AdminPortalColors.textMuted,
        fontSize: 11,
        fontWeight: FontWeight.w800,
        letterSpacing: 1,
      ),
    );
  }
}
