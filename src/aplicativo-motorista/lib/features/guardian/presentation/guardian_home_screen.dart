import 'package:flutter/material.dart';

import '../../../app/widgets/vanrides_liquid_surface.dart';
import '../../../l10n/app_strings.dart';
import '../data/guardian_localizacao_service.dart';
import '../data/guardian_passageiro_service.dart';
import '../data/guardian_presenca_service.dart';
import '../domain/guardian_experience_exception.dart';
import '../domain/guardian_linked_passageiro_record.dart';
import '../domain/guardian_localizacao_snapshot.dart';
import '../domain/guardian_presenca_record.dart';
import '../domain/guardian_session.dart';
import '../../tracking/presentation/map_first_v2_components.dart';
import '../../tracking/presentation/map_first_v2_shell.dart';
import 'guardian_passageiro_detail_screen.dart';
import 'widgets/guardian_home_status_widgets.dart';

class GuardianHomeScreen extends StatefulWidget {
  const GuardianHomeScreen({
    super.key,
    required this.session,
    this.gateway,
    this.presencaGateway,
    this.localizacaoGateway,
  });

  final GuardianSession session;
  final GuardianPassageiroGateway? gateway;
  final GuardianPresencaGateway? presencaGateway;
  final GuardianLocalizacaoGateway? localizacaoGateway;

  @override
  State<GuardianHomeScreen> createState() => _GuardianHomeScreenState();
}

class _GuardianHomeScreenState extends State<GuardianHomeScreen> {
  late final GuardianPassageiroGateway _gateway;
  late final GuardianPresencaGateway _presencaGateway;
  late final GuardianLocalizacaoGateway _localizacaoGateway;
  bool _loading = true;
  String? _errorMessage;
  List<GuardianLinkedPassageiroRecord> _records = const [];
  Map<int, GuardianHomePassengerStatus> _statusByPassengerId = const {};

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ?? GuardianPassageiroService(session: widget.session);
    _presencaGateway =
        widget.presencaGateway ??
        GuardianPresencaService(session: widget.session);
    _localizacaoGateway =
        widget.localizacaoGateway ??
        GuardianLocalizacaoService(session: widget.session);
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _errorMessage = null;
    });

    try {
      final records = await _gateway.fetchLinkedPassageiros();
      final statusByPassengerId = await _loadPassengerStatuses(records);
      if (!mounted) {
        return;
      }
      setState(() {
        _records = records;
        _statusByPassengerId = statusByPassengerId;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loading = false;
        _errorMessage =
            error is GuardianExperienceException
                ? error.message
                : AppStrings
                    .guardian
                    .naoFoiPossivelCarregarPassageirosVinculados;
      });
    }
  }

  Future<Map<int, GuardianHomePassengerStatus>> _loadPassengerStatuses(
    List<GuardianLinkedPassageiroRecord> records,
  ) async {
    final entries = await Future.wait(
      records.map((passageiro) async {
        final presenca = await _safeFetchTodayPresence(passageiro.id);
        final localizacao = await _safeFetchLatestLocation(passageiro);
        return MapEntry(
          passageiro.id,
          GuardianHomePassengerStatus(
            presencaHoje: presenca,
            ultimaLocalizacao: localizacao,
          ),
        );
      }),
    );

    return Map<int, GuardianHomePassengerStatus>.fromEntries(entries);
  }

  Future<GuardianPresencaRecord?> _safeFetchTodayPresence(
    int passageiroId,
  ) async {
    try {
      final records = await _presencaGateway.fetchPresencasByPassageiro(
        passageiroId,
      );
      for (final record in records.reversed) {
        if (_isSameDay(record.dataPresenca, DateTime.now())) {
          return record;
        }
      }
    } catch (_) {
      // A home continua util mesmo se a presenca falhar; a aba propria mostra o erro.
    }

    return null;
  }

  Future<GuardianLocalizacaoSnapshot?> _safeFetchLatestLocation(
    GuardianLinkedPassageiroRecord passageiro,
  ) async {
    final motoristaId = passageiro.motoristaId;
    if (motoristaId == null) {
      return null;
    }

    try {
      final historico = await _localizacaoGateway.fetchHistoricoPorMotorista(
        motoristaId,
      );
      return historico.isEmpty ? null : historico.first;
    } catch (_) {
      // Localizacao indisponivel nao deve bloquear a home de acompanhamento.
      return null;
    }
  }

  bool _isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  @override
  Widget build(BuildContext context) {
    final data = _records;
    final focusPassageiro = data.isEmpty ? null : data.first;

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        key: const ValueKey('guardian.home'),
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 132),
        children: [
          Text(
            AppStrings.guardian.dashboardBadge,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: MapFirstPalette.brandBlue,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            AppStrings.guardian.bemVindo(widget.session.guardianName),
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 18),
          if (_loading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (_errorMessage != null)
            MapFirstEditorialStateView(
              title: AppStrings.guardian.erroDeRede,
              message: _errorMessage!,
              icon: Icons.wifi_off_rounded,
              iconColor: MapFirstPalette.red,
              primaryActionLabel: AppStrings.guardian.atualizar,
              onPrimaryAction: _load,
              showTopBar: false,
              compact: true,
              issues: [
                AppStrings.guardian.listaNaoSincronizada,
                AppStrings.guardian.conexaoFraca,
                AppStrings.guardian.apiProcessandoAtualizacao,
              ],
            )
          else if (data.isEmpty)
            MapFirstEditorialStateView(
              title: AppStrings.guardian.nenhumPassageiroVinculado,
              message: AppStrings.guardian.nenhumPassageiroVinculadoDescricao,
              primaryActionLabel: AppStrings.guardian.atualizar,
              onPrimaryAction: _load,
              icon: Icons.people_outline_rounded,
              showTopBar: false,
              compact: true,
            )
          else ...[
            if (focusPassageiro != null)
              _GuardianFamilyFocusCard(
                passageiro: focusPassageiro,
                status: _statusByPassengerId[focusPassageiro.id],
                passengerCount: data.length,
                minorCount: data.where((item) => item.menorDeIdade).length,
                onTap:
                    () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder:
                            (_) => GuardianPassageiroDetailScreen(
                              passageiro: focusPassageiro,
                            ),
                      ),
                    ),
              ),
            const SizedBox(height: 24),
            Row(
              children: [
                Text(
                  AppStrings.guardian.passageirosDaFamilia,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const Spacer(),
                IconButton(
                  onPressed: _load,
                  tooltip: AppStrings.guardian.atualizar,
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
            const SizedBox(height: 12),
            ...data.map(
              (passageiro) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _LinkedPassageiroCard(
                  passageiro: passageiro,
                  status: _statusByPassengerId[passageiro.id],
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder:
                            (_) => GuardianPassageiroDetailScreen(
                              passageiro: passageiro,
                            ),
                      ),
                    );
                  },
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _GuardianFamilyFocusCard extends StatelessWidget {
  const _GuardianFamilyFocusCard({
    required this.passageiro,
    required this.status,
    required this.passengerCount,
    required this.minorCount,
    required this.onTap,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final GuardianHomePassengerStatus? status;
  final int passengerCount;
  final int minorCount;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final motorista = passageiro.motoristaNome;

    return VanRidesLiquidSurface.sheet(
      semanticLabel: 'Resumo da familia',
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(26),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                MapFirstAvatar(
                  label: passageiro.nome,
                  size: 54,
                  backgroundColor: MapFirstPalette.brandBlue,
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Passageiro foco',
                        style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: MapFirstPalette.brandBlue,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        passageiro.nome,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          color: MapFirstPalette.textStrong,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(
                  Icons.chevron_right_rounded,
                  color: MapFirstPalette.textMuted,
                ),
              ],
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _GuardianFocusPill(
                  icon: Icons.people_alt_outlined,
                  label: AppStrings.guardian.passageirosVinculados,
                  value: passengerCount.toString(),
                ),
                _GuardianFocusPill(
                  icon: Icons.family_restroom_outlined,
                  label: AppStrings.guardian.menoresDeIdade,
                  value: minorCount.toString(),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              AppStrings.guardian.acompanhamentoHoje,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: MapFirstPalette.textMuted,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 10),
            GuardianHomeStatusWrap(
              passageiro: passageiro,
              status: status,
              compact: true,
            ),
            const SizedBox(height: 14),
            Text(
              motorista?.isNotEmpty == true
                  ? AppStrings.guardian.motoristaAssociado(motorista!)
                  : AppStrings.guardian.vinculoAtivo,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: MapFirstPalette.textMuted,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _GuardianFocusPill extends StatelessWidget {
  const _GuardianFocusPill({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(
        color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.62),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: MapFirstPalette.border.withValues(alpha: 0.7),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 17, color: MapFirstPalette.brandBlue),
          const SizedBox(width: 8),
          Text(
            value,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(width: 5),
          Text(
            label,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: MapFirstPalette.textMuted,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _LinkedPassageiroCard extends StatelessWidget {
  const _LinkedPassageiroCard({
    required this.passageiro,
    required this.status,
    required this.onTap,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final GuardianHomePassengerStatus? status;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    const statusColor = MapFirstPalette.brandBlue;
    return VanRidesLiquidSurface.card(
      child: InkWell(
        key: ValueKey('guardian.home.passageiro.${passageiro.id}'),
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.person_pin_circle_rounded, color: statusColor),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        passageiro.nome,
                        style: Theme.of(
                          context,
                        ).textTheme.titleMedium?.copyWith(
                          color: MapFirstPalette.textStrong,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 6),
                      if ((passageiro.motoristaNome ?? '').isNotEmpty)
                        Text(
                          AppStrings.guardian.motoristaAssociado(
                            passageiro.motoristaNome!,
                          ),
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: MapFirstPalette.textMuted),
                        ),
                      if (passageiro.enderecoResumo.isNotEmpty)
                        Text(
                          passageiro.enderecoResumo,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: MapFirstPalette.textMuted),
                        ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    AppStrings.guardian.vinculoAtivo,
                    style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: statusColor,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            GuardianHomeStatusWrap(passageiro: passageiro, status: status),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.78),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: MapFirstPalette.border),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.place_outlined,
                    color: MapFirstPalette.brandBlue,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      passageiro.enderecoResumo.isEmpty
                          ? AppStrings.guardian.enderecoNaoInformado
                          : passageiro.enderecoResumo,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: MapFirstPalette.textStrong,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: onTap,
                    child: Text(AppStrings.guardian.detalhes),
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
