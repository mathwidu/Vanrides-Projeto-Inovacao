import 'dart:async';
import 'package:flutter/material.dart';
import '../data/driver_finish_intent.dart';
import '../data/driver_finish_platform.dart';
import 'driver_finish_controller.dart';

/// Only the explicit action's native receipt can complete this journey.
class DriverFinishPanel extends StatefulWidget {
  const DriverFinishPanel({
    required this.intent,
    required this.platform,
    required this.onFinished,
    super.key,
  });
  final DriverFinishIntent intent;
  final DriverFinishPlatform platform;
  final Future<void> Function(DriverFinishIntent, bool) onFinished;
  @override
  State<DriverFinishPanel> createState() => _DriverFinishPanelState();
}

class _DriverFinishPanelState extends State<DriverFinishPanel>
    with WidgetsBindingObserver {
  late DriverFinishController _controller;
  bool _foreground = true;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _createController();
  }

  void _createController() {
    _controller = DriverFinishController(
      intent: widget.intent,
      platform: widget.platform,
      foreground: _foreground,
      onFinished: (intent, automatic) async {
        if (mounted) await widget.onFinished(intent, automatic);
      },
    );
    unawaited(_controller.load());
  }

  @override
  void didUpdateWidget(covariant DriverFinishPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.platform, widget.platform)) {
      _controller.dispose();
      _createController();
    } else {
      _controller.replaceIntent(widget.intent);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    _controller.foreground(_foreground);
  }

  @override
  void dispose() {
    _controller.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _controller,
    builder: (context, _) => _content(context),
  );

  Widget _content(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            _controller.intent.restored
                ? 'Finalização pendente'
                : 'Finalizar viagem',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          if (_controller.preview != null) Text(_controller.preview!.label),
          if (_controller.error != null)
            Semantics(liveRegion: true, child: Text(_controller.error!)),
          if (_controller.busy)
            const Text(
              'Conferindo a finalização. Você pode parar a localização no painel abaixo.',
            ),
          if (_controller.done)
            const Text('Viagem finalizada e localização desligada.'),
          if (_controller.preview != null && !_controller.done)
            FilledButton(
              onPressed: _controller.busy ? null : _controller.finish,
              child: Text(
                _controller.intent.restored
                    ? 'Tentar finalizar novamente'
                    : 'Finalizar viagem e parar localização',
              ),
            ),
          if (_controller.preview == null && !_controller.busy)
            TextButton(
              onPressed: _controller.load,
              child: const Text('Conferir viagem'),
            ),
        ],
      ),
    ),
  );
}
