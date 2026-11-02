import 'dart:async';

import 'package:flutter/material.dart';

import '../data/driver_tracking_platform.dart';
import '../data/driver_run_intent.dart';

/// Reads the collector itself, including a session already running during an update.
class DriverCollectionPanel extends StatefulWidget {
  const DriverCollectionPanel({
    required this.platform,
    required this.paired,
    this.intent,
    this.onStarted,
    super.key,
  });
  final DriverTrackingPlatform platform;
  final bool paired;
  final DriverRunIntent? intent;
  final VoidCallback? onStarted;

  @override
  State<DriverCollectionPanel> createState() => _DriverCollectionPanelState();
}

class _DriverCollectionPanelState extends State<DriverCollectionPanel>
    with WidgetsBindingObserver {
  final _view =
      ValueNotifier<({DriverTrackingStatus? status, bool busy, String? error})>(
        (status: null, busy: false, error: null),
      );
  bool _foreground = true;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_update(_refresh));
  }

  Future<DriverTrackingStatus> _refresh() =>
      widget.paired
          ? widget.platform.syncStatus()
          : widget.platform.getStatus();

  @override
  void didUpdateWidget(covariant DriverCollectionPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.paired && widget.paired) unawaited(_update(_refresh));
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    _timer?.cancel();
    if (_foreground) unawaited(_update(_refresh));
  }

  Future<void> _update(Future<DriverTrackingStatus> Function() call) async {
    final previous = _view.value;
    if (previous.busy || !mounted) return;
    _view.value = (status: previous.status, busy: true, error: null);
    _timer?.cancel();
    DriverTrackingStatus? status = previous.status;
    String? error;
    try {
      status = await call();
    } catch (_) {
      error = 'Não foi possível verificar a localização. Tente novamente.';
    }
    if (!mounted) return;
    _view.value = (status: status, busy: false, error: error);
    if (_foreground &&
        error == null &&
        (status?.isActive == true || status?.stopPending == true)) {
      _timer = Timer(
        const Duration(seconds: 6),
        () => unawaited(_update(widget.platform.getStatus)),
      );
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    _view.dispose();
    super.dispose();
  }

  String _label(DriverTrackingStatus? s) {
    if (s == null) return 'Consultando o estado deste celular';
    if (s.stopPending) return 'Parada solicitada. Aguardando confirmação.';
    return switch (s.state) {
      'STARTING' => 'Aguardando a primeira posição',
      'TRACKING' when s.gpsState == 'FRESH' => 'Posição recente recebida',
      'TRACKING' when s.gpsState == 'CLOCK_SKEW' =>
        'Confira a data e a hora automáticas do celular',
      'TRACKING' when s.gpsState == 'STALE' =>
        'Sem posição recente no receptor',
      'TRACKING' => 'Coleta em andamento, aguardando confirmação de posição',
      'STOPPED' => 'Localização interrompida',
      'UNCONFIGURED' || 'READY' => 'Coleta parada',
      _ => 'Estado da coleta indisponível',
    };
  }

  @override
  Widget build(BuildContext context) => ValueListenableBuilder(
    valueListenable: _view,
    builder: (context, view, _) {
      final s = view.status;
      final canStop =
          s != null && (s.isActive || s.stopPending || s.state == 'ERROR');
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Compartilhamento de localização',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 6),
              Semantics(liveRegion: true, child: Text(view.error ?? _label(s))),
              if (widget.paired && widget.intent != null && !canStop)
                FilledButton(
                  onPressed:
                      view.busy
                          ? null
                          : () => _update(() async {
                            final platform = widget.platform;
                            if (platform is! DriverRunPlatform) {
                              throw StateError('UNAVAILABLE');
                            }
                            final next = await (platform as DriverRunPlatform)
                                .startRun(widget.intent!);
                            if (next.isActive) widget.onStarted?.call();
                            return next;
                          }),
                  child: const Text('Iniciar localização desta saída'),
                ),
              if (widget.paired && widget.intent == null && !canStop)
                const Text(
                  'Para iniciar ou retomar, abra o link da saída enviado pelo VanRides no WhatsApp.',
                ),
              if (canStop)
                OutlinedButton(
                  onPressed:
                      view.busy ? null : () => _update(widget.platform.stop),
                  child: const Text('Parar localização'),
                ),
              if (widget.paired &&
                  s != null &&
                  !canStop &&
                  (s.permission != 'GRANTED' ||
                      !s.notificationPermissionGranted))
                TextButton(
                  onPressed:
                      view.busy
                          ? null
                          : () => _update(widget.platform.requestPermission),
                  child: const Text('Permitir localização no percurso'),
                ),
              if (view.error != null)
                TextButton(
                  onPressed:
                      view.busy
                          ? null
                          : () => _update(widget.platform.getStatus),
                  child: const Text('Verificar localização'),
                ),
            ],
          ),
        ),
      );
    },
  );
}
