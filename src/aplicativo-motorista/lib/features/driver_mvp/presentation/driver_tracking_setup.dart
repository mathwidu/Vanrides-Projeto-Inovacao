import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../data/driver_tracking_platform.dart';
import 'driver_tracking_diagnostics.dart';
import 'driver_tracking_view_state.dart';

/// The screen observes native collection; disposing it never starts/stops GPS.
class DriverTrackingSetup extends StatefulWidget {
  const DriverTrackingSetup({required this.platform, super.key});
  final DriverTrackingPlatform platform;

  @override
  State<DriverTrackingSetup> createState() => _DriverTrackingSetupState();
}

class _DriverTrackingSetupState extends State<DriverTrackingSetup>
    with WidgetsBindingObserver {
  final _baseUrl = TextEditingController(
    text: 'https://gps-spike.vanrides.matheusduarte.dev.br',
  );
  final _token = TextEditingController();
  final _intent = TextEditingController();
  final _view = ValueNotifier(const DriverTrackingViewState());
  DriverTrackingStatus get _status => _view.value.status;
  bool get _busy => _view.value.busy;
  bool get _starting => _view.value.starting;
  bool get _stopping => _view.value.stopping;
  bool get _editingCredential => _view.value.editingCredential;
  String? get _error => _view.value.error;
  Timer? _poll;
  bool _polling = false;
  int _operation = 0;

  void _update(
    DriverTrackingViewState Function(DriverTrackingViewState) change,
  ) {
    if (mounted) _view.value = change(_view.value);
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _observe();
  }

  void _observe() {
    _poll?.cancel();
    unawaited(_refresh());
    _poll = Timer.periodic(const Duration(seconds: 1), (_) => _refresh());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _observe();
    } else {
      _poll?.cancel();
    }
  }

  Future<void> _refresh() async {
    if (_polling || _busy || _stopping) return;
    _polling = true;
    final operation = _operation;
    try {
      final next = await widget.platform.getStatus();
      if (operation == _operation)
        _update((view) => view.copyWith(status: next));
    } catch (error) {
      if (mounted && operation == _operation) {
        _update((view) => view.copyWith(error: safeTrackingError(error)));
      }
    } finally {
      _polling = false;
    }
  }

  Future<void> _run(
    Future<DriverTrackingStatus> Function() action, {
    bool starting = false,
  }) async {
    if (_busy || _stopping) return;
    final operation = ++_operation;
    _update(
      (view) => view.copyWith(busy: true, starting: starting, clearError: true),
    );
    try {
      final next = await action();
      if (operation == _operation)
        _update((view) => view.copyWith(status: next));
    } catch (error) {
      if (mounted && operation == _operation) {
        _update((view) => view.copyWith(error: safeTrackingError(error)));
      }
    } finally {
      if (mounted && operation == _operation) {
        _update((view) => view.copyWith(busy: false, starting: false));
        unawaited(_refresh());
      }
    }
  }

  Future<void> _stop() async {
    if (_stopping) return;
    ++_operation; // Late activation cannot replace the stop result in the UI.
    _update(
      (view) => view.copyWith(
        stopping: true,
        starting: false,
        busy: false,
        clearError: true,
      ),
    );
    try {
      final next = await widget.platform.stop();
      _update((view) => view.copyWith(status: next));
    } catch (error) {
      _update((view) => view.copyWith(error: safeTrackingError(error)));
    } finally {
      if (mounted) {
        _update((view) => view.copyWith(stopping: false));
        unawaited(_refresh());
      }
    }
  }

  Future<void> _configure() {
    _update((view) => view.copyWith(editingCredential: true));
    return _run(() async {
      final baseUrl = _baseUrl.text.trim();
      final token = _token.text.trim();
      if (baseUrl.isEmpty || token.isEmpty) {
        throw PlatformException(code: 'CONFIG_REQUIRED');
      }
      try {
        final result = await widget.platform.configureCredential(
          baseUrl,
          token,
        );
        _update((view) => view.copyWith(editingCredential: false));
        return result;
      } finally {
        if (mounted) _token.clear();
      }
    });
  }

  @override
  void dispose() {
    _poll?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    _baseUrl.dispose();
    _token.dispose();
    _intent.dispose();
    _view.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      ValueListenableBuilder<DriverTrackingViewState>(
        valueListenable: _view,
        builder: (context, view, child) => _buildView(context),
      );

  Widget _buildView(BuildContext context) {
    final canEdit =
        !_status.isActive &&
        !_status.stopPending &&
        !_starting &&
        !_busy &&
        !_stopping;
    final configuring = !_status.credentialConfigured || _editingCredential;
    final permissionsReady =
        _status.permission == 'GRANTED' &&
        _status.notificationPermissionGranted;
    final stopPending = _stopping || _status.stopPending;
    final stopConfirmed =
        _status.state == 'STOPPED' &&
        !stopPending &&
        !_starting &&
        _error == null;
    final title =
        _starting
            ? 'Iniciando localização'
            : stopPending
            ? 'Encerrando localização'
            : _status.state == 'STOPPED' && _error != null
            ? 'Teste precisa de atenção'
            : trackingStateLabel(_status.state);
    return Scaffold(
      appBar: AppBar(title: const Text('VanRides Motorista')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(title, style: Theme.of(context).textTheme.headlineSmall),
                  const SizedBox(height: 12),
                  const Text('Teste de localização · etapa de preparação'),
                  const SizedBox(height: 8),
                  const Text(
                    'O cadastro e os comandos da rotina ficarão no WhatsApp. '
                    'Este teste verifica o envio de localização com o aplicativo em segundo plano.',
                  ),
                  const SizedBox(height: 16),
                  if (_error != null)
                    Semantics(
                      liveRegion: true,
                      child: Card(
                        color: Theme.of(context).colorScheme.errorContainer,
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Text(_error!),
                        ),
                      ),
                    ),
                  if (configuring) ...[
                    TextField(
                      controller: _baseUrl,
                      enabled: canEdit,
                      keyboardType: TextInputType.url,
                      autocorrect: false,
                      decoration: const InputDecoration(
                        labelText: 'Endereço do receptor de teste',
                      ),
                    ),
                    TextField(
                      controller: _token,
                      enabled: canEdit,
                      obscureText: true,
                      autocorrect: false,
                      enableSuggestions: false,
                      decoration: const InputDecoration(
                        labelText: 'Credencial de teste',
                      ),
                    ),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: canEdit ? _configure : null,
                      child: const Text('Configurar aparelho'),
                    ),
                  ] else ...[
                    Text(permissionLabel(_status.permission)),
                    const SizedBox(height: 8),
                    if (!permissionsReady) ...[
                      OutlinedButton(
                        onPressed:
                            canEdit
                                ? () => _run(widget.platform.requestPermission)
                                : null,
                        child: const Text('Permitir localização'),
                      ),
                      const Text(
                        'Precisamos de localização precisa durante o percurso. '
                        'No iPhone, habilite “Sempre”. No Android, permita a notificação para acompanhar e parar o teste.',
                      ),
                    ],
                    TextField(
                      controller: _intent,
                      enabled: canEdit,
                      autocorrect: false,
                      enableSuggestions: false,
                      decoration: const InputDecoration(
                        labelText: 'Código de início do teste',
                      ),
                    ),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed:
                          canEdit && permissionsReady
                              ? () => _run(() {
                                final intent = _intent.text.trim();
                                if (!RegExp(
                                  r'^[A-Za-z0-9_-]{1,80}$',
                                ).hasMatch(intent)) {
                                  throw PlatformException(
                                    code: 'INVALID_INTENT',
                                  );
                                }
                                return widget.platform.start(intent);
                              }, starting: true)
                              : null,
                      child: const Text('Iniciar teste'),
                    ),
                    OutlinedButton(
                      onPressed:
                          !stopPending && (_status.isActive || _starting)
                              ? _stop
                              : null,
                      child: Text(
                        stopPending
                            ? 'Encerramento solicitado'
                            : stopConfirmed
                            ? 'Localização interrompida'
                            : 'Parar localização',
                      ),
                    ),
                    if (stopPending || stopConfirmed)
                      Semantics(
                        key: const ValueKey('driver-stop-feedback'),
                        liveRegion: true,
                        child: Card(
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Text(
                              stopPending
                                  ? 'Aguardando confirmação do encerramento.'
                                  : 'O coletor confirmou o encerramento.',
                            ),
                          ),
                        ),
                      ),
                    TextButton(
                      onPressed:
                          canEdit
                              ? () => _run(widget.platform.syncStatus)
                              : null,
                      child: const Text('Verificar conexão'),
                    ),
                    if (_status.isActive)
                      Text(switch (_status.gpsState) {
                        'FRESH' => 'Posição recente recebida',
                        'STALE' => 'Sem posição recente no receptor',
                        _ => 'Aguardando posição válida',
                      }),
                    const SizedBox(height: 12),
                    ExpansionTile(
                      title: const Text('Detalhes do teste'),
                      childrenPadding: const EdgeInsets.all(12),
                      children: [
                        Text(
                          'Amostras confirmadas: ${_status.accepted}\n'
                          'Na fila: ${_status.queued}\nDuplicadas: ${_status.duplicates}\n'
                          'Rejeitadas: ${_status.rejected}\nDescartadas: ${_status.dropped}',
                        ),
                        if (_status.stoppedAt != null)
                          const Text('Parada registrada pelo coletor.'),
                        const SizedBox(height: 12),
                        DriverTrackingDiagnosticsPanel(status: _status),
                        if (canEdit)
                          TextButton(
                            onPressed:
                                () => _update(
                                  (view) =>
                                      view.copyWith(editingCredential: true),
                                ),
                            child: const Text('Usar nova credencial de teste'),
                          ),
                      ],
                    ),
                  ],
                  if (_busy) const LinearProgressIndicator(),
                  const SizedBox(height: 16),
                  const Text(
                    'Você pode interromper o compartilhamento a qualquer momento. '
                    'Sem renovação da autorização, o coletor deve encerrar em até cinco minutos. '
                    'Essa condição será verificada nos dois aparelhos durante o ensaio.',
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

String trackingStateLabel(String state) => switch (state) {
  'TRACKING' => 'Localização em andamento',
  'STARTING' => 'Iniciando localização',
  'STOPPED' => 'Localização encerrada',
  'READY' => 'Aparelho configurado',
  'ERROR' => 'Teste precisa de atenção',
  _ => 'Aplicativo instalado',
};

String permissionLabel(String permission) => switch (permission) {
  'GRANTED' => 'Localização autorizada',
  'APPROXIMATE' => 'Ative a localização precisa nos ajustes',
  'DENIED' => 'Localização não autorizada',
  _ => 'Permissão ainda não verificada',
};

String safeTrackingError(Object error) {
  final code = error is PlatformException ? error.code : '';
  return switch (code) {
    'CONFIG_REQUIRED' ||
    'INVALID_CREDENTIAL' ||
    'UNCONFIGURED' ||
    'NOT_CONFIGURED' ||
    'CREDENTIAL_REQUIRED' =>
      'Confira a credencial de teste. Ela pode ter expirado.',
    'HTTP_401' || 'HTTP_403' =>
      'A credencial foi recusada. Configure uma nova credencial de teste.',
    'INVALID_BASE_URL' ||
    'HOST_NOT_ALLOWED' ||
    'INVALID_URL' ||
    'INVALID_ENDPOINT' => 'Use o endereço autorizado do receptor de teste.',
    'PERMISSION_REQUIRED' ||
    'PERMISSION_DENIED' ||
    'LOCATION_PERMISSION' ||
    'PRECISE_LOCATION_REQUIRED' ||
    'BACKGROUND_PERMISSION_REQUIRED' =>
      'Permita a localização precisa para iniciar. No iPhone, escolha “Sempre” quando disponível.',
    'LOCATION_SERVICES_DISABLED' =>
      'Ative os serviços de localização do aparelho.',
    'INTENT_EXPIRED' ||
    'INVALID_INTENT' ||
    'NEW_INTENT_REQUIRED' ||
    'RECONCILIATION_REQUIRED' =>
      'O código de início não está válido. Solicite um novo código de teste.',
    'ACTIVE_RUN_EXISTS' ||
    'ALREADY_TRACKING' => 'Encerre o teste atual antes de iniciar outro.',
    'BUSY' => 'Aguarde a operação em andamento antes de tentar novamente.',
    'NETWORK_ERROR' || 'NETWORK_RETRY' || 'NETWORK_UNAVAILABLE' || 'TIMEOUT' =>
      'Não foi possível alcançar o receptor. Confira a conexão e tente novamente.',
    'NOTIFICATION_PERMISSION_REQUIRED' =>
      'Permita as notificações para acompanhar e parar a localização pelo Android.',
    'NOT_FOREGROUND' ||
    'FOREGROUND_REQUIRED' ||
    'UI_NOT_VISIBLE' => 'Mantenha esta tela aberta para iniciar o teste.',
    _ =>
      'Não foi possível concluir esta etapa. Verifique a conexão e as permissões. '
          'Se continuar, informe o resultado para a equipe.',
  };
}
