import 'package:flutter/material.dart';

import '../../domain/admin_rota_base_record.dart';
import 'admin_portal_layout.dart';

class AdminRotasLegacyNotice extends StatelessWidget {
  const AdminRotasLegacyNotice({super.key});

  @override
  Widget build(BuildContext context) {
    return const AdminPortalSurface(
      radius: 32,
      color: AdminPortalColors.orangeSoft,
      padding: EdgeInsets.all(22),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline_rounded, color: AdminPortalColors.orange),
          SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Fluxo legado de rota-base',
                  style: TextStyle(
                    color: Color(0xFF2F1500),
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                SizedBox(height: 8),
                Text(
                  'Serviços recorrentes concentram direito de uso, presença e geração diária. Rotas-base ficam para compatibilidade, destino técnico e estados publicados de viagens antigas.',
                  style: TextStyle(
                    color: Color(0xFF6E3900),
                    fontSize: 14,
                    height: 1.55,
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

class AdminRotasDeleteButton extends StatelessWidget {
  const AdminRotasDeleteButton({super.key, this.onDelete});

  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onDelete,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: AdminPortalColors.red.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(999),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.delete_outline, size: 18, color: AdminPortalColors.red),
            SizedBox(width: 8),
            Text(
              'Excluir',
              style: TextStyle(
                color: AdminPortalColors.red,
                fontWeight: FontWeight.w700,
                fontSize: 14,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class AdminRotaListCard extends StatelessWidget {
  const AdminRotaListCard({
    super.key,
    required this.rota,
    required this.onOpen,
  });

  final AdminRotaBaseRecord rota;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 48,
      padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 28),
      child: Row(
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: const BoxDecoration(
              color: AdminPortalColors.blueSoft,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.alt_route_rounded,
              color: AdminPortalColors.blue,
              size: 28,
            ),
          ),
          const SizedBox(width: 28),
          Expanded(
            child: Wrap(
              runSpacing: 16,
              spacing: 28,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                SizedBox(
                  width: 250,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        rota.nome,
                        style: const TextStyle(
                          color: AdminPortalColors.text,
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                          height: 1.35,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        rota.diasOperacao.isEmpty
                            ? 'Sem agenda operacional definida'
                            : rota.diasOperacao.join(' • '),
                        style: const TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
                AdminRotasRouteMetaColumn(
                  label: 'MOTORISTA',
                  value: rota.motorista.nome,
                ),
                AdminRotasRouteMetaColumn(
                  label: 'PARADAS',
                  value: rota.quantidadeParadas.toString().padLeft(2, '0'),
                  helper: rota.diasOperacao.join(' • '),
                ),
                if (rota.horarioPartida != null)
                  AdminRotasRouteMetaColumn(
                    label: 'HORÁRIO',
                    value: rota.horarioPartida!,
                  ),
                if (rota.hasDestinoFinal)
                  AdminRotasRouteMetaColumn(
                    label: 'DESTINO',
                    value: rota.destinoFinalNome ?? 'Destino definido',
                    helper: rota.destinoFinalEndereco,
                  ),
              ],
            ),
          ),
          const SizedBox(width: 24),
          Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Text(
                'AUTO-GERAÇÃO',
                style: TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              AdminPortalTag(
                label: rota.geracaoAutomaticaAtiva ? 'ATIVO' : 'OFF',
                background: AdminPortalColors.orangeSoft,
                foreground: const Color(0xFF2F1500),
              ),
            ],
          ),
          const SizedBox(width: 20),
          InkWell(
            onTap: onOpen,
            borderRadius: BorderRadius.circular(999),
            child: Container(
              width: 48,
              height: 48,
              decoration: const BoxDecoration(
                color: Color(0xFFEAE8E2),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.chevron_right_rounded),
            ),
          ),
        ],
      ),
    );
  }
}

class AdminRotasRouteMetaColumn extends StatelessWidget {
  const AdminRotasRouteMetaColumn({
    super.key,
    required this.label,
    required this.value,
    this.helper,
  });

  final String label;
  final String value;
  final String? helper;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.only(left: 24),
      decoration: const BoxDecoration(
        border: Border(left: BorderSide(color: Color(0x33C2C6D6))),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 14,
              fontWeight: FontWeight.w700,
            ),
          ),
          if (helper != null && helper!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              helper!,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 10,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class AdminRotasStatCard extends StatelessWidget {
  const AdminRotasStatCard({
    super.key,
    required this.eyebrow,
    required this.child,
  });

  final String eyebrow;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 32,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            eyebrow,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 12,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.2,
            ),
          ),
          const SizedBox(height: 18),
          child,
        ],
      ),
    );
  }
}

class AdminRotasDarkMetricRow extends StatelessWidget {
  const AdminRotasDarkMetricRow({
    super.key,
    required this.leftLabel,
    required this.leftValue,
    required this.rightLabel,
    required this.rightValue,
  });

  final String leftLabel;
  final String leftValue;
  final String rightLabel;
  final String rightValue;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                leftLabel,
                style: const TextStyle(
                  color: Colors.white54,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                leftValue,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                rightLabel,
                style: const TextStyle(
                  color: Colors.white54,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                rightValue,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class AdminRotasDarkInfoLine extends StatelessWidget {
  const AdminRotasDarkInfoLine({
    super.key,
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: Colors.white54,
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          value,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 17,
            fontWeight: FontWeight.w700,
            height: 1.4,
          ),
        ),
      ],
    );
  }
}

class AdminRotasDetailInfoTile extends StatelessWidget {
  const AdminRotasDetailInfoTile({
    super.key,
    required this.label,
    required this.value,
    required this.helper,
  });

  final String label;
  final String value;
  final String helper;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 12,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.1,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          value,
          style: const TextStyle(
            color: AdminPortalColors.text,
            fontSize: 20,
            fontWeight: FontWeight.w800,
            height: 1.35,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          helper,
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 14,
            height: 1.6,
          ),
        ),
      ],
    );
  }
}

class AdminRotasPaginationFooter extends StatelessWidget {
  const AdminRotasPaginationFooter({super.key, required this.summary});

  final String summary;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            summary,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 12,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
        const Row(
          children: [
            AdminRotasPageCircle(label: '1', selected: true),
            SizedBox(width: 8),
            AdminRotasPageCircle(label: '2'),
            SizedBox(width: 8),
            AdminRotasPageCircle(label: '3'),
          ],
        ),
      ],
    );
  }
}

class AdminRotasPageCircle extends StatelessWidget {
  const AdminRotasPageCircle({
    super.key,
    required this.label,
    this.selected = false,
  });

  final String label;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: selected ? AdminPortalColors.blue : Colors.transparent,
        border: selected ? null : Border.all(color: const Color(0x33C2C6D6)),
      ),
      alignment: Alignment.center,
      child: Text(
        label,
        style: TextStyle(
          color: selected ? Colors.white : AdminPortalColors.textMuted,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class AdminRotasFieldLabel extends StatelessWidget {
  const AdminRotasFieldLabel({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: const TextStyle(
        color: AdminPortalColors.textMuted,
        fontSize: 14,
        fontWeight: FontWeight.w700,
      ),
    );
  }
}

class AdminRotasErrorState extends StatelessWidget {
  const AdminRotasErrorState({
    super.key,
    required this.message,
    required this.onRetry,
  });

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return AdminPortalPage(
      title: 'Rotas-base legadas',
      description: 'Não foi possível carregar a área legada de rotas-base.',
      child: AdminPortalSurface(
        radius: 40,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Falha ao carregar rotas',
              style: TextStyle(
                color: AdminPortalColors.text,
                fontSize: 24,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              message,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 15,
              ),
            ),
            const SizedBox(height: 24),
            AdminPortalPrimaryButton(
              label: 'Tentar novamente',
              icon: Icons.refresh_rounded,
              onPressed: () => onRetry(),
            ),
          ],
        ),
      ),
    );
  }
}
