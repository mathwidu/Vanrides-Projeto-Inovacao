import 'package:flutter/material.dart';
import 'package:table_calendar/table_calendar.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/services/auth_headers.dart';
import 'package:vanrides_frontend_dev/services/api_constants.dart';
import 'package:vanrides_frontend_dev/features/service_presence/data/service_presence_service.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/presence_today_list.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';

part 'confirmar_presenca_widgets.dart';

class ConfirmarPresencaScreen extends StatefulWidget {
  final int idPassageiro;
  final int? motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final http.Client? client;
  final ServicePresenceGateway? servicePresenceGateway;

  static const calendarKey = Key('passageiro.presencas.calendar');
  static const confirmarButtonKey = Key('passageiro.presencas.confirmar');
  static const removerButtonKey = Key('passageiro.presencas.remover');
  static const serviceListKey = Key('passageiro.presencas.servicos');
  static const noServicesTextKey = Key('passageiro.presencas.semServicos');

  const ConfirmarPresencaScreen({
    super.key,
    required this.idPassageiro,
    this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.client,
    this.servicePresenceGateway,
  });

  @override
  State<ConfirmarPresencaScreen> createState() =>
      _ConfirmarPresencaScreenState();
}

class _ConfirmarPresencaScreenState extends State<ConfirmarPresencaScreen> {
  ServicePresenceGateway? _servicePresenceGateway;
  Set<DateTime> _diasConfirmados = {};
  final Set<DateTime> _diasSelecionados = {};
  List<PassengerAvailableServiceRecord> _servicosDisponiveis = const [];
  bool _carregando = false;
  bool _carregandoServicos = false;
  bool _processando = false;

  bool get _usingServicePresence => _servicePresenceGateway != null;

  @override
  void initState() {
    super.initState();
    _servicePresenceGateway =
        widget.servicePresenceGateway ??
        (widget.client == null
            ? ServicePresenceService(
              email: widget.email,
              senha: widget.senha,
              accessToken: widget.accessToken,
            )
            : null);
    if (_usingServicePresence) {
      _carregarServicosDisponiveis();
    } else {
      _carregarPresencas();
    }
  }

  Future<void> _carregarServicosDisponiveis() async {
    final gateway = _servicePresenceGateway;
    if (gateway == null) {
      return;
    }

    setState(() => _carregandoServicos = true);
    try {
      final servicos = await gateway.fetchAvailableServices(
        passageiroId: widget.idPassageiro,
        data: DateTime.now(),
      );
      if (!mounted) return;
      setState(() {
        _servicosDisponiveis = servicos;
        _carregandoServicos = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _servicosDisponiveis = const [];
        _carregandoServicos = false;
      });
    }
  }

  Future<void> _carregarPresencas() async {
    setState(() => _carregando = true);
    final client = widget.client ?? http.Client();

    try {
      final creds = _getCredenciais();
      final url = Uri.parse(
        "$baseUrl/presencas/passageiro/${widget.idPassageiro}",
      );

      final response = await client.get(url, headers: {"Authorization": creds});

      if (response.statusCode == 200) {
        final List<dynamic> data = jsonDecode(response.body);
        _diasConfirmados =
            data.map<DateTime>((presenca) {
              final partes = presenca["dataPresenca"].split("-");
              return DateTime(
                int.parse(partes[0]),
                int.parse(partes[1]),
                int.parse(partes[2]),
              );
            }).toSet();
      } else {
        _mostrarMensagem(AppStrings.motorista.erroAoCarregarPresencas);
      }
    } catch (e) {
      _mostrarMensagem(AppStrings.passageiro.erroConexao(e));
    }

    setState(() => _carregando = false);
  }

  bool _ehMesmoDia(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  void _alternarSelecao(DateTime dia) {
    final jaSelecionado = _diasSelecionados.any((d) => _ehMesmoDia(d, dia));

    if (jaSelecionado) {
      _diasSelecionados.removeWhere((d) => _ehMesmoDia(d, dia));
    } else {
      if (_diasSelecionados.length >= 14) {
        _mostrarMensagem(AppStrings.guardian.limiteDiasSelecionados);
        return;
      }
      _diasSelecionados.add(dia);
    }

    setState(() {});
  }

  Future<void> _confirmarSelecionados() async {
    if (_diasSelecionados.isEmpty) return;

    setState(() => _processando = true);
    final client = widget.client ?? http.Client();

    try {
      final creds = _getCredenciais();
      final url = Uri.parse("$baseUrl/presencas");

      int sucesso = 0, duplicadas = 0, erros = 0;

      for (final dia in _diasSelecionados) {
        final jaConfirmado = _diasConfirmados.any((d) => _ehMesmoDia(d, dia));
        if (jaConfirmado) {
          duplicadas++;
          continue;
        }

        final body = {
          "passageiro": {"id": widget.idPassageiro},
          "dataPresenca": dia.toIso8601String().split("T")[0],
          "confirmado": true,
        };

        final response = await client.post(
          url,
          headers: {"Content-Type": "application/json", "Authorization": creds},
          body: jsonEncode(body),
        );

        if (response.statusCode == 200) {
          _diasConfirmados.add(dia);
          sucesso++;
        } else {
          erros++;
        }
      }

      _diasSelecionados.clear();
      setState(() {});

      _mostrarMensagem(
        AppStrings.passageiro.presencasResumo(sucesso, duplicadas, erros),
      );
    } catch (e) {
      _mostrarMensagem(AppStrings.passageiro.erroPadrao(e));
    }

    setState(() => _processando = false);
  }

  Future<void> _removerSelecionados() async {
    if (_diasSelecionados.isEmpty) return;

    setState(() => _processando = true);
    final client = widget.client ?? http.Client();

    try {
      final creds = _getCredenciais();
      final urlBusca = Uri.parse("$baseUrl/presencas");

      final responseBusca = await client.get(
        urlBusca,
        headers: {"Authorization": creds},
      );

      if (responseBusca.statusCode != 200) {
        _mostrarMensagem(AppStrings.passageiro.erroAoBuscarPresencas);
        setState(() => _processando = false);
        return;
      }

      final List<dynamic> data = jsonDecode(responseBusca.body);
      int removidas = 0, ignoradas = 0, erros = 0;

      for (final dia in _diasSelecionados) {
        final diaStr = dia.toIso8601String().split("T")[0];
        final item = data.firstWhere(
          (p) =>
              p["dataPresenca"] == diaStr &&
              p["passageiro"]["id"] == widget.idPassageiro,
          orElse: () => null,
        );

        if (item == null) {
          ignoradas++;
          continue;
        }

        final idPresenca = item["id"];
        final urlDelete = Uri.parse("$baseUrl/presencas/$idPresenca");

        final responseDelete = await client.delete(
          urlDelete,
          headers: {"Authorization": creds},
        );

        if (responseDelete.statusCode == 204) {
          _diasConfirmados.removeWhere((d) => _ehMesmoDia(d, dia));
          removidas++;
        } else {
          erros++;
        }
      }

      _diasSelecionados.clear();
      setState(() {});

      _mostrarMensagem(
        AppStrings.passageiro.remocoesResumo(removidas, ignoradas, erros),
      );
    } catch (e) {
      _mostrarMensagem(AppStrings.passageiro.erroPadrao(e));
    }

    setState(() => _processando = false);
  }

  Future<void> _marcarPresencaServico(
    PassengerAvailableServiceRecord service,
    String status,
  ) async {
    final gateway = _servicePresenceGateway;
    if (gateway == null) return;

    setState(() => _processando = true);
    try {
      await gateway.setPresenceStatus(
        service: service,
        passageiroId: widget.idPassageiro,
        status: status,
      );
      await _carregarServicosDisponiveis();
      _mostrarMensagem(
        status == 'CONFIRMED'
            ? 'Presença confirmada para ${service.nome}.'
            : 'Ausência registrada para ${service.nome}.',
      );
    } catch (error) {
      _mostrarMensagem(
        error is ServicePresenceException
            ? error.message
            : 'Não foi possível atualizar a presença do serviço.',
      );
    }
    if (mounted) {
      setState(() => _processando = false);
    }
  }

  Future<void> _limparPresencaServico(
    PassengerAvailableServiceRecord service,
  ) async {
    final gateway = _servicePresenceGateway;
    final presencaId = service.presencaId;
    if (gateway == null || presencaId == null) return;

    setState(() => _processando = true);
    try {
      await gateway.clearPresence(presencaId);
      await _carregarServicosDisponiveis();
      _mostrarMensagem('Decisão removida para ${service.nome}.');
    } catch (error) {
      _mostrarMensagem(
        error is ServicePresenceException
            ? error.message
            : 'Não foi possível limpar a decisão de presença.',
      );
    }
    if (mounted) {
      setState(() => _processando = false);
    }
  }

  String _getCredenciais() {
    return buildAuthorizationHeader(
      accessToken: widget.accessToken,
      email: widget.email,
      senha: widget.senha,
    );
  }

  void _mostrarMensagem(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    final hoje = DateTime.now();
    final usingServicePresence = _usingServicePresence;

    final darkTheme = Theme.of(context).copyWith(
      colorScheme: const ColorScheme.dark(
        primary: VanRidesDesignTokens.blueTracking,
        secondary: VanRidesDesignTokens.greenAction,
        surface: VanRidesDesignTokens.backgroundDeep,
        surfaceContainerHighest: VanRidesDesignTokens.surface,
        surfaceContainerHigh: VanRidesDesignTokens.surfaceRaised,
        onSurface: VanRidesDesignTokens.textPrimary,
        onSurfaceVariant: VanRidesDesignTokens.textSecondary,
      ),
      snackBarTheme: const SnackBarThemeData(
        backgroundColor: VanRidesDesignTokens.surfaceRaised,
        contentTextStyle: TextStyle(color: VanRidesDesignTokens.textPrimary),
      ),
    );

    return Theme(
      data: darkTheme,
      child: Scaffold(
        backgroundColor: VanRidesDesignTokens.mapBase,
        appBar: AppBar(
          title: Text(AppStrings.guardian.presencas),
          backgroundColor: Colors.transparent,
          foregroundColor: MapFirstPalette.textStrong,
          elevation: 0,
          surfaceTintColor: Colors.transparent,
        ),
        body: DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                VanRidesDesignTokens.mapBase,
                VanRidesDesignTokens.surface.withValues(alpha: 0.88),
                VanRidesDesignTokens.backgroundDeep.withValues(alpha: 0.94),
              ],
              stops: const [0, 0.5, 1],
            ),
          ),
          child:
              _carregando || _carregandoServicos
                  ? const Center(
                    child: CircularProgressIndicator(
                      color: MapFirstPalette.brandBlue,
                    ),
                  )
                  : usingServicePresence
                  ? _ServicePresenceBody(
                    services: _servicosDisponiveis,
                    processing: _processando,
                    onConfirm:
                        (service) =>
                            _marcarPresencaServico(service, 'CONFIRMED'),
                    onAbsent:
                        (service) => _marcarPresencaServico(service, 'ABSENT'),
                    onClear: _limparPresencaServico,
                  )
                  : _LegacyCalendarPresenceBody(
                    today: hoje,
                    confirmedDays: _diasConfirmados,
                    selectedDays: _diasSelecionados,
                    processing: _processando,
                    isSameDay: _ehMesmoDia,
                    onDaySelected: _alternarSelecao,
                    onConfirm: _confirmarSelecionados,
                    onRemove: _removerSelecionados,
                  ),
        ),
      ),
    );
  }
}
