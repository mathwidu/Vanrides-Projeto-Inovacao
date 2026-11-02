import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../tracking/presentation/trip_live_map_panel.dart';
import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_portal_session.dart';
import '../../domain/admin_viagem_detail_record.dart';
import 'admin_portal_layout.dart';
import 'admin_viagens_detail_widgets.dart';
import 'admin_viagens_widgets.dart';

class AdminViagemDetailView extends StatefulWidget {
  const AdminViagemDetailView({
    super.key,
    required this.detail,
    required this.session,
    required this.motoristas,
    required this.isUpdatingMotorista,
    required this.isSyncingManifest,
    required this.isCancellingTrip,
    required this.updatingPresenceParadaId,
    required this.mapRenderMode,
    required this.onBack,
    required this.onRefresh,
    required this.onChangeMotorista,
    required this.onSyncManifest,
    required this.onCancelTrip,
    required this.onUpdatePresence,
  });

  final AdminViagemDetailRecord detail;
  final AdminPortalSession session;
  final List<AdminMotoristaRecord> motoristas;
  final bool isUpdatingMotorista;
  final bool isSyncingManifest;
  final bool isCancellingTrip;
  final int? updatingPresenceParadaId;
  final TripLiveMapRenderMode mapRenderMode;
  final VoidCallback onBack;
  final Future<void> Function() onRefresh;
  final ValueChanged<int> onChangeMotorista;
  final Future<void> Function() onSyncManifest;
  final Future<void> Function() onCancelTrip;
  final Future<void> Function(AdminViagemParadaRecord parada, String status)
  onUpdatePresence;

  @override
  State<AdminViagemDetailView> createState() => _AdminViagemDetailViewState();
}

class _AdminViagemDetailViewState extends State<AdminViagemDetailView> {
  late int _selectedMotoristaId;

  @override
  void initState() {
    super.initState();
    _selectedMotoristaId = widget.detail.motorista.id;
  }

  @override
  void didUpdateWidget(covariant AdminViagemDetailView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.detail.id != widget.detail.id ||
        oldWidget.detail.motorista.id != widget.detail.motorista.id) {
      _selectedMotoristaId = widget.detail.motorista.id;
    }
  }

  @override
  Widget build(BuildContext context) {
    final detail = widget.detail;
    final destino = detail.destinoFinal;
    final proxima = detail.proximaParada;
    final motoristas = _motoristasComAtual();
    final canSubmitMotorista =
        detail.canChangeMotorista &&
        !widget.isUpdatingMotorista &&
        _selectedMotoristaId != detail.motorista.id;

    return AdminPortalPage(
      breadcrumb: 'Viagens • Detalhe',
      title: detail.nomeOperacional,
      description: 'Lista, estado publicado e motorista desta viagem.',
      trailing: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 860),
        child: Wrap(
          alignment: WrapAlignment.end,
          spacing: 12,
          runSpacing: 12,
          children: [
            AdminPortalSecondaryButton(
              label: 'Voltar',
              icon: Icons.arrow_back_rounded,
              onPressed: widget.onBack,
            ),
            AdminPortalSecondaryButton(
              label: 'Copiar link',
              icon: Icons.link_rounded,
              onPressed: _copyTripLink,
            ),
            if (detail.canSyncManifest) ...[
              AdminPortalSecondaryButton(
                label:
                    widget.isSyncingManifest
                        ? 'Sincronizando...'
                        : 'Sincronizar lista',
                icon: Icons.sync_rounded,
                onPressed:
                    widget.isSyncingManifest ? null : widget.onSyncManifest,
              ),
            ],
            if (detail.canCancel) ...[
              OutlinedButton.icon(
                onPressed: widget.isCancellingTrip ? null : widget.onCancelTrip,
                icon: const Icon(Icons.block_rounded, size: 18),
                label: Text(
                  widget.isCancellingTrip ? 'Cancelando...' : 'Cancelar viagem',
                ),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AdminPortalColors.red,
                  side: const BorderSide(color: AdminPortalColors.red),
                  backgroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 16,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(999),
                  ),
                  textStyle: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 16,
                  ),
                ),
              ),
            ],
            AdminPortalPrimaryButton(
              label: 'Atualizar',
              icon: Icons.refresh_rounded,
              onPressed: widget.onRefresh,
            ),
          ],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                flex: 7,
                child: AdminPortalSurface(
                  radius: 40,
                  color: AdminPortalColors.navy,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          AdminViagensStatusTag(status: detail.status),
                          AdminPortalTag(
                            label: adminViagensFormatFontePlanejamento(
                              detail.fontePlanejamento,
                            ),
                            background: const Color(0x1FFFFFFF),
                            foreground: Colors.white70,
                          ),
                          if (detail.snapshotOperacional)
                            const AdminPortalTag(
                              label: 'PUBLICADO NO DIA',
                              background: Color(0x1FFFFFFF),
                              foreground: Colors.white70,
                            ),
                        ],
                      ),
                      const SizedBox(height: 20),
                      Text(
                        detail.nomeOperacional,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 32,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      if (detail.servicoId != null) ...[
                        const SizedBox(height: 10),
                        Text(
                          detail.canSyncManifest
                              ? 'A lista vem do serviço recorrente. Sincronize aqui quando passageiro ou presença mudar antes do início.'
                              : 'A viagem já começou ou foi encerrada. A lista não sincroniza automaticamente para proteger a execução publicada.',
                          style: const TextStyle(
                            color: Colors.white70,
                            fontSize: 13,
                            height: 1.45,
                          ),
                        ),
                      ],
                      const SizedBox(height: 24),
                      Row(
                        children: [
                          Expanded(
                            child: AdminViagensDarkInfoLine(
                              label: 'Data e horário',
                              value:
                                  '${adminViagensFormatLongDate(detail.dataOperacao)} • ${detail.horarioPartida ?? '--:--'}',
                            ),
                          ),
                          const SizedBox(width: 24),
                          Expanded(
                            child: AdminViagensDarkInfoLine(
                              label: 'Motorista operacional',
                              value: detail.motorista.nome,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 20),
                      Row(
                        children: [
                          Expanded(
                            child: AdminViagensDarkInfoLine(
                              label: 'Próxima parada',
                              value:
                                  proxima == null
                                      ? 'Sem parada pendente'
                                      : '#${proxima.ordem} ${proxima.passageiro.nome}',
                            ),
                          ),
                          const SizedBox(width: 24),
                          Expanded(
                            child: AdminViagensDarkInfoLine(
                              label: 'Destino final',
                              value:
                                  destino == null || !destino.hasContent
                                      ? 'Destino final não publicado'
                                      : destino.label,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 24),
                      AdminViagensDarkMetricRow(
                        leftLabel: 'PARADAS',
                        leftValue: detail.quantidadeParadas.toString(),
                        rightLabel: 'ETA PRÓXIMA',
                        rightValue: '${detail.etaMinutosAteProximaParada} min',
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 24),
              Expanded(
                flex: 5,
                child: AdminPortalSurface(
                  radius: 40,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Motorista desta viagem',
                        style: TextStyle(
                          color: AdminPortalColors.text,
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'A troca abaixo altera só a viagem do dia. O serviço recorrente continua com seu motorista padrão.',
                        style: TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 13,
                          height: 1.5,
                        ),
                      ),
                      const SizedBox(height: 20),
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 28,
                            backgroundColor: AdminPortalColors.blueSoft,
                            child: Text(
                              adminViagensInitial(detail.motorista.nome),
                              style: const TextStyle(
                                color: AdminPortalColors.blue,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  detail.motorista.nome,
                                  style: const TextStyle(
                                    color: AdminPortalColors.text,
                                    fontSize: 18,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  detail.motorista.telefone,
                                  style: const TextStyle(
                                    color: AdminPortalColors.textMuted,
                                    fontSize: 13,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 24),
                      DropdownButtonFormField<int>(
                        initialValue: _selectedMotoristaId,
                        isExpanded: true,
                        decoration: adminViagensInputDecoration(
                          hintText: 'Selecionar motorista operacional...',
                        ),
                        items: motoristas
                            .map(
                              (motorista) => DropdownMenuItem<int>(
                                value: motorista.id,
                                child: Text(
                                  motorista.nome,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            )
                            .toList(growable: false),
                        onChanged:
                            detail.canChangeMotorista
                                ? (value) {
                                  if (value == null) return;
                                  setState(() => _selectedMotoristaId = value);
                                }
                                : null,
                      ),
                      const SizedBox(height: 12),
                      AdminPortalPrimaryButton(
                        label:
                            widget.isUpdatingMotorista
                                ? 'Trocando...'
                                : 'Trocar motorista',
                        icon: Icons.swap_horiz_rounded,
                        onPressed:
                            canSubmitMotorista
                                ? () => widget.onChangeMotorista(
                                  _selectedMotoristaId,
                                )
                                : null,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        detail.canChangeMotorista
                            ? 'Disponível enquanto a viagem está planejada.'
                            : 'Viagem iniciada ou encerrada: a troca fica bloqueada para proteger a execução em andamento.',
                        style: const TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 12,
                          height: 1.5,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          AdminViagensTripMapSurface(
            detail: detail,
            session: widget.session,
            mapRenderMode: widget.mapRenderMode,
          ),
          const SizedBox(height: 24),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                flex: 7,
                child: AdminViagensManifestSurface(
                  paradas: detail.paradas,
                  canEditPresence: detail.servicoId != null,
                  updatingPresenceParadaId: widget.updatingPresenceParadaId,
                  onUpdatePresence: widget.onUpdatePresence,
                ),
              ),
              const SizedBox(width: 24),
              Expanded(
                flex: 5,
                child: AdminViagensSnapshotSurface(detail: detail),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _copyTripLink() async {
    await Clipboard.setData(
      ClipboardData(text: _tripDeepLink(widget.detail.id)),
    );
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Link da viagem copiado.')));
  }

  List<AdminMotoristaRecord> _motoristasComAtual() {
    final exists = widget.motoristas.any(
      (motorista) => motorista.id == widget.detail.motorista.id,
    );
    if (exists) return widget.motoristas;
    return [widget.detail.motorista, ...widget.motoristas];
  }
}

String _tripDeepLink(int tripId) => 'vanrides://viagem/$tripId';
