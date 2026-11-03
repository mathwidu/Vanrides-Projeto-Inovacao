import 'package:flutter/material.dart';

import '../../../tracking/presentation/trip_live_map_panel.dart';
import '../../../tracking/presentation/trip_tracking_presenter.dart';
import '../../domain/admin_portal_session.dart';
import '../../domain/admin_viagem_detail_record.dart';
import 'admin_portal_layout.dart';
import 'admin_viagens_widgets.dart';

class AdminViagensTripMapSurface extends StatelessWidget {
  const AdminViagensTripMapSurface({
    super.key,
    required this.detail,
    required this.session,
    required this.mapRenderMode,
  });

  final AdminViagemDetailRecord detail;
  final AdminPortalSession session;
  final TripLiveMapRenderMode mapRenderMode;

  @override
  Widget build(BuildContext context) {
    final geometry = detail.rotaGeografica;
    final pointCount =
        geometry?.waypoints.where((item) => item.hasCoordinates).length ?? 0;
    final routeLabel = presentRouteGeometryLabel(geometry);
    final routeHelper = presentRouteGeometryHelper(geometry);
    final nextStop =
        geometry?.nextStop?.nome ??
        detail.proximaParada?.passageiro.nome ??
        'Sem próxima parada';
    final accessToken =
        session.accessToken.trim().isEmpty ? null : session.accessToken;

    return AdminPortalSurface(
      radius: 40,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                Icons.map_rounded,
                color: AdminPortalColors.blue,
                size: 28,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Mapa operacional',
                      style: TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      routeHelper,
                      style: const TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 13,
                        height: 1.45,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.end,
                children: [
                  AdminPortalTag(
                    label: routeLabel,
                    background: AdminPortalColors.blueSoft,
                    foreground: AdminPortalColors.blue,
                  ),
                  AdminPortalTag(
                    label: _publishedPointsLabel(pointCount),
                    background: AdminPortalColors.statusSurface,
                    foreground: AdminPortalColors.textMuted,
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 20),
          LayoutBuilder(
            builder: (context, constraints) {
              final map = TripLiveMapPanel(
                motoristaId: detail.motorista.id,
                email: session.email,
                senha: '',
                accessToken: accessToken,
                height: 340,
                renderMode: mapRenderMode,
                routeGeometry: geometry,
              );
              final summary = _AdminViagensMapSummary(
                detail: detail,
                nextStop: nextStop,
                pointCount: pointCount,
              );

              if (constraints.maxWidth < 900) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [map, const SizedBox(height: 18), summary],
                );
              }

              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(flex: 7, child: map),
                  const SizedBox(width: 20),
                  Expanded(flex: 4, child: summary),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _AdminViagensMapSummary extends StatelessWidget {
  const _AdminViagensMapSummary({
    required this.detail,
    required this.nextStop,
    required this.pointCount,
  });

  final AdminViagemDetailRecord detail;
  final String nextStop;
  final int pointCount;

  @override
  Widget build(BuildContext context) {
    final navigation = detail.rotaGeografica?.navegacao;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _AdminViagensSnapshotLine(
          label: 'Motorista',
          value: detail.motorista.nome,
        ),
        _AdminViagensSnapshotLine(label: 'Próxima parada', value: nextStop),
        _AdminViagensSnapshotLine(
          label: 'ETA próxima',
          value: '${detail.etaMinutosAteProximaParada} min',
        ),
        _AdminViagensSnapshotLine(
          label: 'Pontos publicados',
          value: _publishedPointsLabel(pointCount),
        ),
        _AdminViagensSnapshotLine(
          label: 'Navegação externa',
          value:
              navigation == null || !navigation.hasUrl
                  ? 'Não publicada'
                  : '${navigation.origem} para ${navigation.destino}',
        ),
      ],
    );
  }
}

String _publishedPointsLabel(int count) =>
    count == 1 ? '1 ponto publicado' : '$count pontos publicados';

class AdminViagensManifestSurface extends StatelessWidget {
  const AdminViagensManifestSurface({
    super.key,
    required this.paradas,
    required this.canEditPresence,
    required this.updatingPresenceParadaId,
    required this.onUpdatePresence,
  });

  final List<AdminViagemParadaRecord> paradas;
  final bool canEditPresence;
  final int? updatingPresenceParadaId;
  final Future<void> Function(AdminViagemParadaRecord parada, String status)
  onUpdatePresence;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 40,
      padding: EdgeInsets.zero,
      child: Column(
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(28, 24, 28, 20),
            child: Row(
              children: [
                Icon(Icons.fact_check_outlined, color: AdminPortalColors.blue),
                SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Lista da viagem',
                    style: TextStyle(
                      color: AdminPortalColors.text,
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (paradas.isEmpty)
            const Padding(
              padding: EdgeInsets.all(28),
              child: Text(
                'Nenhuma parada materializada nesta viagem.',
                style: TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 14,
                ),
              ),
            )
          else
            ...paradas.map(
              (parada) => _AdminViagensManifestStopRow(
                parada: parada,
                canEditPresence: canEditPresence,
                isUpdatingPresence: updatingPresenceParadaId == parada.id,
                onUpdatePresence: onUpdatePresence,
              ),
            ),
        ],
      ),
    );
  }
}

class _AdminViagensManifestStopRow extends StatelessWidget {
  const _AdminViagensManifestStopRow({
    required this.parada,
    required this.canEditPresence,
    required this.isUpdatingPresence,
    required this.onUpdatePresence,
  });

  final AdminViagemParadaRecord parada;
  final bool canEditPresence;
  final bool isUpdatingPresence;
  final Future<void> Function(AdminViagemParadaRecord parada, String status)
  onUpdatePresence;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        border: Border(top: BorderSide(color: Color(0x14C2C6D6))),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 18),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            alignment: Alignment.center,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              color: AdminPortalColors.surfaceSoft,
            ),
            child: Text(
              parada.ordem.toString().padLeft(2, '0'),
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  parada.passageiro.nome,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  parada.proximaParada
                      ? 'Próxima parada • ETA ${parada.etaMinutos ?? 0} min'
                      : 'ETA ${parada.etaMinutos ?? 0} min • ${adminViagensFormatStopStatus(parada.status)}',
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          _AdminViagensPresenceTag(parada: parada),
          if (canEditPresence) ...[
            const SizedBox(width: 8),
            if (isUpdatingPresence)
              const SizedBox(
                width: 32,
                height: 32,
                child: Padding(
                  padding: EdgeInsets.all(6),
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              )
            else
              PopupMenuButton<String>(
                tooltip: 'Corrigir presença',
                icon: const Icon(Icons.more_vert_rounded),
                onSelected: (status) => onUpdatePresence(parada, status),
                itemBuilder:
                    (context) => const [
                      PopupMenuItem(value: 'CONFIRMED', child: Text('Vai')),
                      PopupMenuItem(value: 'ABSENT', child: Text('Ausente')),
                      PopupMenuItem(
                        value: 'CANCELED_BY_COMPANY',
                        child: Text('Cancelado pela empresa'),
                      ),
                    ],
              ),
          ],
        ],
      ),
    );
  }
}

class _AdminViagensPresenceTag extends StatelessWidget {
  const _AdminViagensPresenceTag({required this.parada});

  final AdminViagemParadaRecord parada;

  @override
  Widget build(BuildContext context) {
    final normalized = parada.presencaStatus.toUpperCase();
    final config = switch (normalized) {
      String s when s.contains('CONFIRMED') => (
        label: 'VAI',
        bg: AdminPortalColors.greenSoft,
        fg: AdminPortalColors.successText,
      ),
      String s when s.contains('ABSENT') => (
        label: 'AUSENTE',
        bg: AdminPortalColors.redSoft,
        fg: AdminPortalColors.red,
      ),
      String s when s.contains('CANCELED') => (
        label: 'CANCELADO',
        bg: AdminPortalColors.redSoft,
        fg: AdminPortalColors.red,
      ),
      _ => (
        label: parada.confirmadoPresenca ? 'INCLUÍDO' : 'SEM DECISÃO',
        bg: AdminPortalColors.blueSoft,
        fg: AdminPortalColors.blue,
      ),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: BoxDecoration(
        color: config.bg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        config.label,
        style: TextStyle(
          color: config.fg,
          fontSize: 11,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class AdminViagensSnapshotSurface extends StatelessWidget {
  const AdminViagensSnapshotSurface({super.key, required this.detail});

  final AdminViagemDetailRecord detail;

  @override
  Widget build(BuildContext context) {
    final destino = detail.destinoFinal;
    final manifest = _AdminViagensTripManifestSummary.from(detail.paradas);
    return AdminPortalSurface(
      radius: 40,
      color: AdminPortalColors.elevatedSurface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Estado publicado',
            style: TextStyle(
              color: AdminPortalColors.text,
              fontSize: 22,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              AdminPortalTag(
                label: manifest.totalLabel,
                background: AdminPortalColors.surfaceSoft,
                foreground: AdminPortalColors.textMuted,
              ),
              AdminPortalTag(
                label: manifest.confirmedLabel,
                background: AdminPortalColors.greenSoft,
                foreground: AdminPortalColors.green,
              ),
              if (manifest.absent > 0)
                AdminPortalTag(
                  label: manifest.absentLabel,
                  background: AdminPortalColors.redSoft,
                  foreground: AdminPortalColors.red,
                ),
              if (manifest.pending > 0)
                AdminPortalTag(
                  label: manifest.pendingLabel,
                  background: AdminPortalColors.blueSoft,
                  foreground: AdminPortalColors.blue,
                ),
            ],
          ),
          const SizedBox(height: 18),
          _AdminViagensSnapshotLine(
            label: 'Lista',
            value: manifest.description,
          ),
          _AdminViagensSnapshotLine(
            label: 'Operação',
            value:
                detail.snapshotOperacional
                    ? 'Estado publicado do dia'
                    : 'Ligada ao planejamento base',
          ),
          _AdminViagensSnapshotLine(
            label: 'Serviço',
            value:
                detail.servicoId == null
                    ? 'Sem serviço recorrente'
                    : '#${detail.servicoId} • ${detail.servicoNome ?? detail.nomeOperacional}',
          ),
          _AdminViagensSnapshotLine(
            label: 'Rota-base',
            value:
                detail.rotaBaseId == null
                    ? 'Não vinculada'
                    : '#${detail.rotaBaseId} • ${detail.rotaNome}',
          ),
          _AdminViagensSnapshotLine(
            label: 'Tipo',
            value: detail.tipoOperacao ?? 'Não definido',
          ),
          _AdminViagensSnapshotLine(
            label: 'Destino',
            value:
                destino == null || !destino.hasContent
                    ? 'Não publicado'
                    : destino.label,
          ),
          _AdminViagensSnapshotLine(
            label: 'Versão da rota',
            value: detail.rotaVersao.toString(),
          ),
          _AdminViagensSnapshotLine(
            label: 'Fonte',
            value: adminViagensFormatFontePlanejamento(
              detail.fontePlanejamento,
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminViagensSnapshotLine extends StatelessWidget {
  const _AdminViagensSnapshotLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 112,
            child: Text(
              label,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontSize: 14,
                fontWeight: FontWeight.w700,
                height: 1.4,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminViagensTripManifestSummary {
  const _AdminViagensTripManifestSummary({
    required this.total,
    required this.confirmed,
    required this.absent,
    required this.pending,
  });

  final int total;
  final int confirmed;
  final int absent;
  final int pending;

  String get totalLabel => '$total ${total == 1 ? 'parada' : 'paradas'}';
  String get confirmedLabel => 'Vai: $confirmed';
  String get absentLabel => 'Ausente: $absent';
  String get pendingLabel => 'Sem decisão: $pending';

  String get description {
    if (total == 0) {
      return 'Nenhuma parada publicada para esta viagem.';
    }
    final parts = <String>[
      '$confirmed confirmados',
      if (absent > 0) '$absent ausentes',
      if (pending > 0) '$pending sem decisão',
    ];
    return 'Lista publicada com ${parts.join(', ')}.';
  }

  factory _AdminViagensTripManifestSummary.from(
    List<AdminViagemParadaRecord> paradas,
  ) {
    var confirmed = 0;
    var absent = 0;
    var pending = 0;

    for (final parada in paradas) {
      final status = parada.presencaStatus.toUpperCase();
      if (status.contains('CONFIRMED')) {
        confirmed++;
      } else if (status.contains('ABSENT') || status.contains('CANCELED')) {
        absent++;
      } else {
        pending++;
      }
    }

    return _AdminViagensTripManifestSummary(
      total: paradas.length,
      confirmed: confirmed,
      absent: absent,
      pending: pending,
    );
  }
}
