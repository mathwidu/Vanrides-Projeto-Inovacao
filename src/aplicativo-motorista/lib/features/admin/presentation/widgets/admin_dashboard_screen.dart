import 'package:flutter/material.dart';

import '../../../../l10n/app_strings.dart';
import '../../data/admin_dashboard_service.dart';
import '../../domain/admin_dashboard_summary_record.dart';
import '../../domain/admin_portal_session.dart';
import 'admin_portal_layout.dart';

part 'admin_dashboard_status_widgets.dart';
part 'admin_dashboard_next_action_widgets.dart';
part 'admin_dashboard_side_panel_widgets.dart';

class AdminDashboardScreen extends StatefulWidget {
  const AdminDashboardScreen({
    super.key,
    required this.session,
    this.gateway,
    this.onOpenServicos,
    this.onOpenViagens,
    this.onOpenMotoristas,
    this.onOpenPassageiros,
  });

  final AdminPortalSession session;
  final AdminDashboardGateway? gateway;
  final VoidCallback? onOpenServicos;
  final VoidCallback? onOpenViagens;
  final VoidCallback? onOpenMotoristas;
  final VoidCallback? onOpenPassageiros;

  @override
  State<AdminDashboardScreen> createState() => _AdminDashboardScreenState();
}

class _AdminDashboardScreenState extends State<AdminDashboardScreen> {
  late final AdminDashboardGateway _gateway;

  AdminDashboardSummaryRecord _summary = AdminDashboardSummaryRecord.zeros();
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ?? AdminDashboardPortalService(session: widget.session);
    _loadSummary();
  }

  Future<void> _loadSummary() async {
    try {
      final summary = await _gateway.fetchSummary();
      if (!mounted) return;
      setState(() {
        _summary = summary;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.admin.erroCarregarDashboard)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = _summary;

    return AdminPortalPage(
      title: 'Dashboard Operacional',
      description: 'Bem-vindo de volta. Aqui está o resumo da frota hoje.',
      child: LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth >= 1280;
          final metricWidth =
              constraints.maxWidth >= 1500
                  ? (constraints.maxWidth - 72) / 4
                  : constraints.maxWidth >= 1050
                  ? (constraints.maxWidth - 48) / 3
                  : constraints.maxWidth >= 720
                  ? (constraints.maxWidth - 24) / 2
                  : constraints.maxWidth;

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 24,
                runSpacing: 24,
                children: [
                  _DashboardMetricCardFrame(
                    width: metricWidth,
                    child: AdminPortalMetricCard(
                      label: 'Motoristas ativos',
                      value: _isLoading ? '—' : s.motoristasAtivos.toString(),
                      icon: Icons.directions_bus_outlined,
                      trailingTag:
                          s.motoristasAtivos == 0 && !_isLoading
                              ? const AdminPortalTag(
                                label: 'SEM CADASTRO',
                                background: AdminPortalColors.surfaceSoft,
                                foreground: AdminPortalColors.textMuted,
                              )
                              : null,
                      subtitle:
                          _isLoading
                              ? 'Carregando...'
                              : 'Motoristas com conta ativa na empresa',
                    ),
                  ),
                  _DashboardMetricCardFrame(
                    width: metricWidth,
                    child: AdminPortalMetricCard(
                      label: 'Presenças por serviço',
                      value:
                          _isLoading
                              ? '—'
                              : s.presencasServicoHoje.confirmadas.toString(),
                      icon: Icons.groups_outlined,
                      trailingTag:
                          s.presencasServicoHoje.totalDecisoes == 0 &&
                                  !_isLoading
                              ? const AdminPortalTag(
                                label: 'SEM DECISÃO',
                                background: AdminPortalColors.surfaceSoft,
                                foreground: AdminPortalColors.textMuted,
                              )
                              : null,
                      subtitle:
                          _isLoading
                              ? 'Carregando...'
                              : '${s.presencasServicoHoje.ausentes} ausências · '
                                  '${s.presencasServicoHoje.totalDecisoes} decisões',
                    ),
                  ),
                  _DashboardMetricCardFrame(
                    width: metricWidth,
                    child: AdminPortalMetricCard(
                      label: 'Viagens hoje',
                      value: _isLoading ? '—' : s.viagensHoje.total.toString(),
                      icon: Icons.route_outlined,
                      iconBackground: AdminPortalColors.orangeSoft,
                      trailingTag:
                          s.viagensHoje.emAndamento > 0
                              ? AdminPortalTag(
                                label:
                                    '${s.viagensHoje.emAndamento} EM ANDAMENTO',
                                background: AdminPortalColors.greenSoft,
                                foreground: AdminPortalColors.green,
                              )
                              : null,
                      subtitle:
                          _isLoading
                              ? 'Carregando...'
                              : s.viagensHoje.total == 0
                              ? 'Nenhuma viagem registrada hoje'
                              : '${s.viagensHoje.concluidas} concluídas · '
                                  '${s.viagensHoje.planejadas} planejadas',
                    ),
                  ),
                  _DashboardMetricCardFrame(
                    width: metricWidth,
                    child: AdminPortalMetricCard(
                      label: 'Serviços ativos',
                      value:
                          _isLoading
                              ? '—'
                              : s.servicosRecorrentes.ativos.toString(),
                      icon: Icons.alt_route_outlined,
                      trailingTag:
                          s.servicosRecorrentes.semMotorista > 0 && !_isLoading
                              ? AdminPortalTag(
                                label:
                                    '${s.servicosRecorrentes.semMotorista} SEM MOTORISTA',
                                background: AdminPortalColors.orangeSoft,
                                foreground: AdminPortalColors.orange,
                              )
                              : s.servicosRecorrentes.ativos == 0 && !_isLoading
                              ? const AdminPortalTag(
                                label: 'SEM SERVIÇOS',
                                background: AdminPortalColors.surfaceSoft,
                                foreground: AdminPortalColors.textMuted,
                              )
                              : null,
                      subtitle:
                          _isLoading
                              ? 'Carregando...'
                              : '${s.servicosRecorrentes.automaticos} com geração automática',
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 32),
              if (wide)
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      flex: 8,
                      child: _DashboardRoutePanel(
                        summary: s,
                        isLoading: _isLoading,
                        onOpenServicos: widget.onOpenServicos,
                        onOpenViagens: widget.onOpenViagens,
                        onOpenMotoristas: widget.onOpenMotoristas,
                        onOpenPassageiros: widget.onOpenPassageiros,
                      ),
                    ),
                    const SizedBox(width: 24),
                    Expanded(
                      flex: 4,
                      child: _DashboardSidePanel(
                        summary: s,
                        isLoading: _isLoading,
                        onOpenServicos: widget.onOpenServicos,
                        onOpenViagens: widget.onOpenViagens,
                        onOpenMotoristas: widget.onOpenMotoristas,
                        onOpenPassageiros: widget.onOpenPassageiros,
                      ),
                    ),
                  ],
                )
              else
                Column(
                  children: [
                    _DashboardRoutePanel(
                      summary: s,
                      isLoading: _isLoading,
                      onOpenServicos: widget.onOpenServicos,
                      onOpenViagens: widget.onOpenViagens,
                      onOpenMotoristas: widget.onOpenMotoristas,
                      onOpenPassageiros: widget.onOpenPassageiros,
                    ),
                    const SizedBox(height: 24),
                    _DashboardSidePanel(
                      summary: s,
                      isLoading: _isLoading,
                      onOpenServicos: widget.onOpenServicos,
                      onOpenViagens: widget.onOpenViagens,
                      onOpenMotoristas: widget.onOpenMotoristas,
                      onOpenPassageiros: widget.onOpenPassageiros,
                    ),
                  ],
                ),
            ],
          );
        },
      ),
    );
  }
}

class _DashboardMetricCardFrame extends StatelessWidget {
  const _DashboardMetricCardFrame({required this.width, required this.child});

  final double width;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SizedBox(width: width, child: child);
  }
}

class _DashboardRoutePanel extends StatelessWidget {
  const _DashboardRoutePanel({
    required this.summary,
    required this.isLoading,
    this.onOpenServicos,
    this.onOpenViagens,
    this.onOpenMotoristas,
    this.onOpenPassageiros,
  });

  final AdminDashboardSummaryRecord summary;
  final bool isLoading;
  final VoidCallback? onOpenServicos;
  final VoidCallback? onOpenViagens;
  final VoidCallback? onOpenMotoristas;
  final VoidCallback? onOpenPassageiros;

  @override
  Widget build(BuildContext context) {
    final services = summary.servicosRecorrentes;
    final trips = summary.viagensHoje;
    final presences = summary.presencasServicoHoje;
    final nextActions = _DashboardNextAction.resolveAll(
      summary: summary,
      isLoading: isLoading,
      onOpenServicos: onOpenServicos,
      onOpenViagens: onOpenViagens,
      onOpenMotoristas: onOpenMotoristas,
      onOpenPassageiros: onOpenPassageiros,
    );

    return AdminPortalSurface(
      radius: 40,
      padding: const EdgeInsets.all(32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 12,
            runSpacing: 12,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: const Text(
                  'Operação de hoje',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 28,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              AdminPortalPrimaryButton(
                label: 'Abrir viagens',
                icon: Icons.auto_awesome_motion_outlined,
                onPressed: onOpenViagens,
              ),
              AdminPortalSecondaryButton(
                label: 'Abrir serviços',
                icon: Icons.route_outlined,
                onPressed: onOpenServicos,
              ),
            ],
          ),
          const SizedBox(height: 24),
          _DashboardTodayReadinessPanel(summary: summary, isLoading: isLoading),
          const SizedBox(height: 18),
          _DashboardNextActionPanel(actions: nextActions),
          const SizedBox(height: 18),
          _RouteStatusRow(
            title: 'Serviços recorrentes',
            subtitle:
                isLoading
                    ? 'Carregando linhas operacionais...'
                    : '${services.ativos} ativos • ${services.automaticos} automáticos',
            capacityLabel:
                isLoading
                    ? '—'
                    : services.semMotorista == 0
                    ? 'Escala padrão definida'
                    : '${services.semMotorista} exigem escala diária',
            stateLabel:
                isLoading
                    ? 'CARREGANDO'
                    : services.ativos == 0
                    ? 'CADASTRAR'
                    : services.semMotorista > 0
                    ? 'ESCALA PENDENTE'
                    : 'PRONTO',
            stateBackground:
                services.ativos == 0 || services.semMotorista > 0
                    ? AdminPortalColors.orangeSoft
                    : AdminPortalColors.greenSoft,
            stateColor:
                services.ativos == 0 || services.semMotorista > 0
                    ? AdminPortalColors.orange
                    : AdminPortalColors.green,
            accent: AdminPortalColors.blue,
            actionLabel: 'Abrir serviços',
            onTap: onOpenServicos,
          ),
          const SizedBox(height: 14),
          _RouteStatusRow(
            title: 'Viagens do dia',
            subtitle:
                isLoading
                    ? 'Carregando viagens materializadas...'
                    : '${trips.total} viagens • ${trips.planejadas} planejadas • ${trips.emAndamento} em andamento',
            capacityLabel:
                isLoading
                    ? '—'
                    : trips.total == 0
                    ? 'Use Montar viagens'
                    : '${trips.concluidas} concluídas',
            stateLabel:
                isLoading
                    ? 'CARREGANDO'
                    : trips.emAndamento > 0
                    ? 'EM EXECUÇÃO'
                    : trips.total == 0
                    ? 'NÃO MONTADO'
                    : 'PUBLICADO',
            stateBackground:
                trips.emAndamento > 0
                    ? AdminPortalColors.greenSoft
                    : trips.total == 0
                    ? AdminPortalColors.orangeSoft
                    : AdminPortalColors.blueSoft,
            stateColor:
                trips.emAndamento > 0
                    ? AdminPortalColors.green
                    : trips.total == 0
                    ? AdminPortalColors.orange
                    : AdminPortalColors.blue,
            accent: AdminPortalColors.orange,
            actionLabel: 'Abrir viagens',
            onTap: onOpenViagens,
          ),
          const SizedBox(height: 14),
          _RouteStatusRow(
            title: 'Decisões de presença',
            subtitle:
                isLoading
                    ? 'Carregando presença por serviço...'
                    : '${presences.confirmadas} confirmadas • ${presences.ausentes} ausências',
            capacityLabel:
                isLoading
                    ? '—'
                    : '${presences.totalDecisoes} decisões registradas',
            stateLabel:
                isLoading
                    ? 'CARREGANDO'
                    : presences.totalDecisoes == 0
                    ? 'SEM DECISÕES'
                    : 'ATUALIZADO',
            stateBackground:
                presences.totalDecisoes == 0
                    ? AdminPortalColors.surfaceSoft
                    : AdminPortalColors.greenSoft,
            stateColor:
                presences.totalDecisoes == 0
                    ? AdminPortalColors.textMuted
                    : AdminPortalColors.green,
            accent: AdminPortalColors.green,
            actionLabel: 'Ver viagens',
            onTap: onOpenViagens,
          ),
        ],
      ),
    );
  }
}
