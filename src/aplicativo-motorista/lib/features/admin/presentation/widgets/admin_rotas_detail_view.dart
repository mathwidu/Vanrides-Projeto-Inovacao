import 'package:flutter/material.dart';

import '../../domain/admin_rota_base_record.dart';
import 'admin_portal_layout.dart';
import 'admin_rotas_widgets.dart';

class AdminRotaDetailView extends StatelessWidget {
  const AdminRotaDetailView({
    super.key,
    required this.rota,
    required this.isSubmitting,
    required this.onBack,
    required this.onEdit,
    required this.onDelete,
  });

  final AdminRotaBaseRecord rota;
  final bool isSubmitting;
  final VoidCallback onBack;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return AdminPortalPage(
      breadcrumb: 'Rotas-base • Detalhe',
      title: rota.nome,
      description:
          'Referência operacional usada para orientar viagens, destino final e dias de circulação.',
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AdminPortalSecondaryButton(
            label: 'Voltar para lista',
            icon: Icons.arrow_back,
            onPressed: onBack,
          ),
          const SizedBox(width: 12),
          AdminPortalSecondaryButton(
            label: 'Editar',
            icon: Icons.edit_outlined,
            onPressed: isSubmitting ? null : onEdit,
          ),
          const SizedBox(width: 12),
          AdminRotasDeleteButton(onDelete: isSubmitting ? null : onDelete),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            flex: 7,
            child: Column(
              children: [
                AdminPortalSurface(
                  radius: 48,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Informações da rota-base',
                        style: TextStyle(
                          color: AdminPortalColors.text,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 24),
                      AdminRotasDetailInfoTile(
                        label: 'Motorista responsável',
                        value: rota.motorista.nome,
                        helper: rota.motorista.telefone,
                      ),
                      const Divider(
                        height: 32,
                        color: AdminPortalColors.divider,
                      ),
                      AdminRotasDetailInfoTile(
                        label: 'Paradas vinculadas',
                        value:
                            '${rota.quantidadeParadas} passageiros na fila operacional',
                        helper:
                            'Quantidade consolidada para acompanhar a fila operacional.',
                      ),
                      const Divider(
                        height: 32,
                        color: AdminPortalColors.divider,
                      ),
                      AdminRotasDetailInfoTile(
                        label: 'Dias de operação',
                        value:
                            rota.diasOperacao.isEmpty
                                ? 'Nenhum dia configurado'
                                : rota.diasOperacao.join(' • '),
                        helper:
                            rota.geracaoAutomaticaAtiva
                                ? 'Geração automática ligada para essa rota-base.'
                                : 'Geração automática desligada.',
                      ),
                      if (rota.horarioPartida != null) ...[
                        const Divider(
                          height: 32,
                          color: AdminPortalColors.divider,
                        ),
                        AdminRotasDetailInfoTile(
                          label: 'Horário de Partida',
                          value: rota.horarioPartida!,
                          helper:
                              'Horário configurado para o início da operação desta rota-base.',
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                AdminPortalSurface(
                  radius: 48,
                  color: AdminPortalColors.navy,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      AdminPortalTag(
                        label:
                            rota.hasDestinoFinal
                                ? 'DESTINO FINAL'
                                : 'SEM DESTINO',
                        background: const Color(0x1FFFFFFF),
                        foreground: AdminPortalColors.textMuted,
                      ),
                      const SizedBox(height: 16),
                      Text(
                        rota.hasDestinoFinal
                            ? (rota.destinoFinalNome ?? 'Destino definido')
                            : 'Sem destino final configurado',
                        style: const TextStyle(
                          color: AdminPortalColors.text,
                          fontSize: 28,
                          fontWeight: FontWeight.w800,
                          height: 1.2,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        rota.hasDestinoFinal
                            ? (rota.destinoFinalEndereco ??
                                'Lat: ${rota.destinoFinalLatitude?.toStringAsFixed(5)} · Lng: ${rota.destinoFinalLongitude?.toStringAsFixed(5)}')
                            : 'A navegação do motorista usa a última parada da fila como destino. Defina um destino final explícito ao criar ou editar a rota.',
                        style: const TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 15,
                          height: 1.6,
                        ),
                      ),
                      const SizedBox(height: 24),
                      AdminRotasDarkMetricRow(
                        leftLabel: 'Status da rota',
                        leftValue: rota.ativo ? 'ATIVA' : 'INATIVA',
                        rightLabel: 'Auto-geração',
                        rightValue:
                            rota.geracaoAutomaticaAtiva ? 'ATIVO' : 'OFF',
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 24),
          Expanded(
            flex: 5,
            child: Column(
              children: [
                AdminPortalSurface(
                  radius: 40,
                  color: AdminPortalColors.elevatedSurface,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Indicadores rápidos',
                        style: TextStyle(
                          color: AdminPortalColors.text,
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 20),
                      AdminPortalMetricCard(
                        icon: Icons.groups_rounded,
                        iconBackground: AdminPortalColors.surface,
                        value: rota.quantidadeParadas.toString().padLeft(
                          2,
                          '0',
                        ),
                        label: 'Paradas vinculadas',
                      ),
                      const SizedBox(height: 16),
                      AdminPortalMetricCard(
                        icon: Icons.route_rounded,
                        iconBackground: AdminPortalColors.surface,
                        value: rota.diasOperacao.length.toString().padLeft(
                          2,
                          '0',
                        ),
                        label: 'Dias ativos',
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                AdminPortalSurface(
                  radius: 40,
                  color: AdminPortalColors.orangeSoft,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Row(
                        children: [
                          Icon(
                            Icons.info_outline_rounded,
                            color: AdminPortalColors.warningText,
                          ),
                          SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              'Destino planejado da rota',
                              style: TextStyle(
                                color: AdminPortalColors.text,
                                fontSize: 18,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Text(
                        rota.hasDestinoFinal
                            ? 'O destino final já está salvo na rota-base. Ao criar a viagem, esse ponto entra no estado publicado do dia; com coordenadas, ele também vira o destino da rota calculada.'
                            : 'Sem destino final explícito, a viagem ainda pode ser gerada, mas a roteirização usa a última parada como fallback até a empresa definir um ponto final.',
                        style: const TextStyle(
                          color: AdminPortalColors.warningText,
                          fontSize: 14,
                          height: 1.6,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
