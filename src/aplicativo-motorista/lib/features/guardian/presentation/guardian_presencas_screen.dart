import 'package:flutter/material.dart';
import 'package:table_calendar/table_calendar.dart';

import '../../../l10n/app_strings.dart';
import '../../service_presence/data/service_presence_service.dart';
import '../../service_presence/domain/passenger_available_service_record.dart';
import '../../tracking/presentation/map_first_v2_components.dart';
import '../../tracking/presentation/map_first_v2_shell.dart';
import '../data/guardian_passageiro_service.dart';
import '../data/guardian_presenca_service.dart';
import '../domain/guardian_experience_exception.dart';
import '../domain/guardian_linked_passageiro_record.dart';
import '../domain/guardian_presenca_record.dart';
import '../domain/guardian_session.dart';
import 'widgets/guardian_presencas_summary_widgets.dart';

class GuardianPresencasScreen extends StatefulWidget {
  const GuardianPresencasScreen({
    super.key,
    required this.session,
    this.passageiroGateway,
    this.presencaGateway,
    this.servicePresenceGateway,
  });

  static const calendarKey = Key('guardian.presencas.calendar');
  static const confirmarButtonKey = Key('guardian.presencas.confirmar');
  static const removerButtonKey = Key('guardian.presencas.remover');

  final GuardianSession session;
  final GuardianPassageiroGateway? passageiroGateway;
  final GuardianPresencaGateway? presencaGateway;
  final ServicePresenceGateway? servicePresenceGateway;

  @override
  State<GuardianPresencasScreen> createState() =>
      _GuardianPresencasScreenState();
}

class _GuardianPresencasScreenState extends State<GuardianPresencasScreen> {
  late final GuardianPassageiroGateway _passageiroGateway;
  late final GuardianPresencaGateway _presencaGateway;
  ServicePresenceGateway? _servicePresenceGateway;

  bool _loadingPassageiros = true;
  bool _loadingPresencas = false;
  bool _loadingServicos = false;
  bool _processing = false;
  String? _errorMessage;
  List<GuardianLinkedPassageiroRecord> _passageiros = const [];
  List<GuardianPresencaRecord> _presencas = const [];
  List<PassengerAvailableServiceRecord> _servicosDisponiveis = const [];
  int? _selectedPassageiroId;
  final Set<DateTime> _diasSelecionados = <DateTime>{};

  bool get _usingServicePresence => _servicePresenceGateway != null;

  @override
  void initState() {
    super.initState();
    _passageiroGateway =
        widget.passageiroGateway ??
        GuardianPassageiroService(session: widget.session);
    _presencaGateway =
        widget.presencaGateway ??
        GuardianPresencaService(session: widget.session);
    _servicePresenceGateway =
        widget.servicePresenceGateway ??
        (widget.presencaGateway == null
            ? ServicePresenceService(
              email: widget.session.email,
              senha: '',
              accessToken: widget.session.accessToken,
            )
            : null);
    _loadPassageiros();
  }

  Future<void> _loadPassageiros() async {
    setState(() {
      _loadingPassageiros = true;
      _errorMessage = null;
    });

    try {
      final passageiros = await _passageiroGateway.fetchLinkedPassageiros();
      if (!mounted) {
        return;
      }
      final selected =
          passageiros.any((item) => item.id == _selectedPassageiroId)
              ? _selectedPassageiroId
              : (passageiros.isNotEmpty ? passageiros.first.id : null);
      setState(() {
        _passageiros = passageiros;
        _selectedPassageiroId = selected;
        _loadingPassageiros = false;
      });
      if (selected != null) {
        if (_usingServicePresence) {
          await _loadServicosFor(selected);
        } else {
          await _loadPresencasFor(selected);
        }
      } else {
        setState(() {
          _presencas = const [];
          _servicosDisponiveis = const [];
          _diasSelecionados.clear();
        });
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loadingPassageiros = false;
        _errorMessage =
            error is GuardianExperienceException
                ? error.message
                : AppStrings
                    .guardian
                    .naoFoiPossivelCarregarPassageirosVinculados;
      });
    }
  }

  Future<void> _loadPresencasFor(int passageiroId) async {
    setState(() {
      _loadingPresencas = true;
      _errorMessage = null;
      _servicosDisponiveis = const [];
      _diasSelecionados.clear();
    });

    try {
      final presencas = await _presencaGateway.fetchPresencasByPassageiro(
        passageiroId,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _presencas = presencas;
        _loadingPresencas = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loadingPresencas = false;
        _errorMessage =
            error is GuardianExperienceException
                ? error.message
                : AppStrings.guardian.naoFoiPossivelCarregarPresencas;
      });
    }
  }

  Future<void> _loadServicosFor(int passageiroId) async {
    final gateway = _servicePresenceGateway;
    if (gateway == null) {
      setState(() => _servicosDisponiveis = const []);
      return;
    }

    setState(() {
      _loadingServicos = true;
      _errorMessage = null;
      _presencas = const [];
      _diasSelecionados.clear();
    });
    try {
      final servicos = await gateway.fetchAvailableServices(
        passageiroId: passageiroId,
        data: DateTime.now(),
      );
      if (!mounted) return;
      setState(() {
        _servicosDisponiveis = servicos;
        _loadingServicos = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _servicosDisponiveis = const [];
        _loadingServicos = false;
      });
    }
  }

  GuardianLinkedPassageiroRecord? get _selectedPassageiro {
    final id = _selectedPassageiroId;
    if (id == null) {
      return null;
    }
    for (final passageiro in _passageiros) {
      if (passageiro.id == id) {
        return passageiro;
      }
    }
    return null;
  }

  bool _sameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  bool _isConfirmed(DateTime day) {
    return _presencas.any((record) => _sameDay(record.dataPresenca, day));
  }

  void _toggleDay(DateTime day) {
    final selected = _diasSelecionados.any((value) => _sameDay(value, day));
    setState(() {
      if (selected) {
        _diasSelecionados.removeWhere((value) => _sameDay(value, day));
      } else {
        if (_diasSelecionados.length >= 14) {
          _showMessage(AppStrings.guardian.limiteDiasSelecionados);
          return;
        }
        _diasSelecionados.add(day);
      }
    });
  }

  Future<void> _confirmarSelecionados() async {
    final passageiro = _selectedPassageiro;
    if (passageiro == null || _diasSelecionados.isEmpty) {
      return;
    }

    setState(() => _processing = true);
    var confirmadas = 0;
    var duplicadas = 0;
    var erros = 0;

    final datas = _diasSelecionados.toList(growable: false);
    for (final dia in datas) {
      if (_isConfirmed(dia)) {
        duplicadas++;
        continue;
      }
      try {
        await _presencaGateway.confirmarPresenca(
          passageiroId: passageiro.id,
          dataPresenca: dia,
        );
        confirmadas++;
      } catch (_) {
        erros++;
      }
    }

    await _loadPresencasFor(passageiro.id);
    if (!mounted) {
      return;
    }
    setState(() => _processing = false);
    _showMessage(
      AppStrings.guardian.resumoConfirmacao(confirmadas, duplicadas, erros),
    );
  }

  Future<void> _removerSelecionados() async {
    final passageiro = _selectedPassageiro;
    if (passageiro == null || _diasSelecionados.isEmpty) {
      return;
    }

    setState(() => _processing = true);
    var removidas = 0;
    var ignoradas = 0;
    var erros = 0;

    final datas = _diasSelecionados.toList(growable: false);
    for (final dia in datas) {
      final presenca = _presencas.cast<GuardianPresencaRecord?>().firstWhere(
        (item) => item != null && _sameDay(item.dataPresenca, dia),
        orElse: () => null,
      );
      if (presenca == null) {
        ignoradas++;
        continue;
      }
      try {
        await _presencaGateway.removerPresenca(presenca.id);
        removidas++;
      } catch (_) {
        erros++;
      }
    }

    await _loadPresencasFor(passageiro.id);
    if (!mounted) {
      return;
    }
    setState(() => _processing = false);
    _showMessage(
      AppStrings.guardian.resumoRemocao(removidas, ignoradas, erros),
    );
  }

  Future<void> _marcarPresencaServico(
    PassengerAvailableServiceRecord service,
    int passageiroId,
    String status,
  ) async {
    final gateway = _servicePresenceGateway;
    if (gateway == null) return;

    setState(() => _processing = true);
    try {
      await gateway.setPresenceStatus(
        service: service,
        passageiroId: passageiroId,
        status: status,
      );
      await _loadServicosFor(passageiroId);
      _showMessage(
        status == 'CONFIRMED'
            ? 'Presença confirmada para ${service.nome}.'
            : 'Ausência registrada para ${service.nome}.',
      );
    } catch (error) {
      _showMessage(
        error is ServicePresenceException
            ? error.message
            : 'Não foi possível atualizar a presença do serviço.',
      );
    }
    if (mounted) {
      setState(() => _processing = false);
    }
  }

  Future<void> _limparPresencaServico(
    PassengerAvailableServiceRecord service,
  ) async {
    final gateway = _servicePresenceGateway;
    final passageiro = _selectedPassageiro;
    final presencaId = service.presencaId;
    if (gateway == null || passageiro == null || presencaId == null) return;

    setState(() => _processing = true);
    try {
      await gateway.clearPresence(presencaId);
      await _loadServicosFor(passageiro.id);
      _showMessage('Decisão removida para ${service.nome}.');
    } catch (error) {
      _showMessage(
        error is ServicePresenceException
            ? error.message
            : 'Não foi possível limpar a decisão de presença.',
      );
    }
    if (mounted) {
      setState(() => _processing = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final passageiro = _selectedPassageiro;
    final usingServicePresence = _usingServicePresence;
    final confirmedCount =
        usingServicePresence
            ? _servicosDisponiveis
                .where((service) => service.isConfirmed)
                .length
            : _presencas.length;
    final selectionMetricTitle =
        usingServicePresence
            ? AppStrings.guardian.servicosDeHoje
            : AppStrings.guardian.diasSelecionados;
    final selectionMetricValue =
        usingServicePresence
            ? _servicosDisponiveis.length.toString()
            : _diasSelecionados.length.toString();
    final passengerDriverLabel =
        passageiro == null
            ? null
            : passageiro.motoristaNome?.isNotEmpty == true
            ? AppStrings.guardian.motoristaAssociadoDetalhe(
              passageiro.motoristaNome!,
            )
            : AppStrings.guardian.semMotoristaAssociado;
    final passengerAgeLabel =
        passageiro == null
            ? null
            : passageiro.menorDeIdade
            ? AppStrings.guardian.passageiroMenorDeIdade
            : AppStrings.guardian.passageiroAdulto;

    return RefreshIndicator(
      onRefresh: _loadPassageiros,
      child: ListView(
        key: const ValueKey('guardian.presencas'),
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(24, 18, 24, 168),
        children: [
          Text(
            AppStrings.guardian.presencasDoResponsavel,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            AppStrings.guardian.presencasDescricao,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.35,
            ),
          ),
          const SizedBox(height: 16),
          if (_loadingPassageiros)
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
              onPrimaryAction: () => _loadPassageiros(),
            )
          else if (_passageiros.isEmpty)
            MapFirstEditorialStateView(
              title: AppStrings.guardian.nenhumPassageiroVinculado,
              message: AppStrings.guardian.aindaNaoHaPassageiros,
              primaryActionLabel: AppStrings.guardian.atualizar,
              onPrimaryAction: null,
              icon: Icons.people_outline_rounded,
            )
          else ...[
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                GuardianPresenceMetricPill(
                  title: AppStrings.guardian.comAcesso,
                  value: _passageiros.length.toString(),
                  icon: Icons.people_alt_outlined,
                  color: MapFirstPalette.brandBlue,
                ),
                GuardianPresenceMetricPill(
                  title: AppStrings.guardian.confirmadas,
                  value: confirmedCount.toString(),
                  icon: Icons.fact_check_outlined,
                  color: MapFirstPalette.green,
                ),
                GuardianPresenceMetricPill(
                  title: selectionMetricTitle,
                  value: selectionMetricValue,
                  icon: Icons.event_available_outlined,
                  color: MapFirstPalette.brandBlue,
                ),
              ],
            ),
            if (_passageiros.length > 1) ...[
              const SizedBox(height: 22),
              Text(
                AppStrings.guardian.selecionarPassageiro,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: MapFirstPalette.textStrong,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _passageiros
                    .map(
                      (item) => ChoiceChip(
                        key: ValueKey(
                          'guardian.presencas.passageiro.${item.id}',
                        ),
                        label: Text(item.nome),
                        selected: item.id == _selectedPassageiroId,
                        onSelected: (_) {
                          setState(() => _selectedPassageiroId = item.id);
                          if (usingServicePresence) {
                            _loadServicosFor(item.id);
                          } else {
                            _loadPresencasFor(item.id);
                          }
                        },
                      ),
                    )
                    .toList(growable: false),
              ),
            ],
            const SizedBox(height: 20),
            if (passageiro != null && !usingServicePresence)
              GuardianPresenceSelectedPassengerCard(
                passageiro: passageiro,
                driverLabel: passengerDriverLabel!,
                ageLabel: passengerAgeLabel!,
              ),
            const SizedBox(height: 20),
            if (usingServicePresence && passageiro != null)
              GuardianPresenceServiceArea(
                passageiro: passageiro,
                driverLabel: passengerDriverLabel!,
                ageLabel: passengerAgeLabel!,
                services: _servicosDisponiveis,
                loading: _loadingServicos,
                processing: _processing,
                onConfirm:
                    (service) => _marcarPresencaServico(
                      service,
                      passageiro.id,
                      'CONFIRMED',
                    ),
                onAbsent:
                    (service) => _marcarPresencaServico(
                      service,
                      passageiro.id,
                      'ABSENT',
                    ),
                onClear: _limparPresencaServico,
                onRefresh: () => _loadServicosFor(passageiro.id),
              )
            else if (_loadingPresencas)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 40),
                child: Center(child: CircularProgressIndicator()),
              )
            else ...[
              MapFirstSurfaceCard(
                child: TableCalendar(
                  key: GuardianPresencasScreen.calendarKey,
                  firstDay: DateTime.now().subtract(const Duration(days: 30)),
                  lastDay: DateTime.now().add(const Duration(days: 60)),
                  focusedDay: DateTime.now(),
                  locale: 'pt_BR',
                  headerStyle: const HeaderStyle(
                    formatButtonVisible: false,
                    titleCentered: true,
                  ),
                  calendarStyle: CalendarStyle(
                    todayDecoration: BoxDecoration(
                      color: MapFirstPalette.brandBlue.withValues(alpha: 0.18),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    defaultDecoration: BoxDecoration(
                      color: Colors.transparent,
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                  selectedDayPredicate:
                      (day) => _diasSelecionados.any(
                        (value) => _sameDay(value, day),
                      ),
                  onDaySelected: (selectedDay, _) => _toggleDay(selectedDay),
                  calendarBuilders: CalendarBuilders(
                    defaultBuilder: (context, day, _) {
                      final confirmed = _isConfirmed(day);
                      final selected = _diasSelecionados.any(
                        (value) => _sameDay(value, day),
                      );
                      Color? backgroundColor;
                      if (confirmed) {
                        backgroundColor = MapFirstPalette.green;
                      } else if (selected) {
                        backgroundColor = MapFirstPalette.brandBlue;
                      }

                      return Container(
                        margin: const EdgeInsets.all(4),
                        decoration: BoxDecoration(
                          color: backgroundColor,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          '${day.day}',
                          style: TextStyle(
                            color:
                                backgroundColor != null ? Colors.white : null,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  FilledButton.icon(
                    key: GuardianPresencasScreen.confirmarButtonKey,
                    onPressed: _processing ? null : _confirmarSelecionados,
                    icon:
                        _processing
                            ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                color: Colors.white,
                                strokeWidth: 2,
                              ),
                            )
                            : const Icon(Icons.check),
                    label: Text(AppStrings.guardian.confirmarSelecionados),
                  ),
                  OutlinedButton.icon(
                    key: GuardianPresencasScreen.removerButtonKey,
                    onPressed: _processing ? null : _removerSelecionados,
                    icon: const Icon(Icons.delete_outline),
                    label: Text(AppStrings.guardian.removerSelecionados),
                  ),
                ],
              ),
            ],
          ],
        ],
      ),
    );
  }
}
