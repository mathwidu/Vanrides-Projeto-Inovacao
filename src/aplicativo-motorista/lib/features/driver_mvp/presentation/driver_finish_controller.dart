import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../data/driver_finish_intent.dart';
import '../data/driver_finish_platform.dart';

/// Owns one preview/action and discards replies from replaced or closed views.
class DriverFinishController extends ChangeNotifier {
  DriverFinishController({
    required DriverFinishIntent intent,
    required this.platform,
    required this.onFinished,
    bool foreground = true,
  }) : _intent = intent,
       _foreground = foreground;

  final DriverFinishPlatform platform;
  final Future<void> Function(DriverFinishIntent, bool) onFinished;
  DriverFinishIntent _intent;
  DriverFinishPreview? _preview;
  String? _error;
  bool _busy = false, _done = false, _closed = false;
  bool _foreground;
  int _operationEpoch = 0, _visibilityEpoch = 0;

  DriverFinishIntent get intent => _intent;
  DriverFinishPreview? get preview => _preview;
  String? get error => _error;
  bool get busy => _busy;
  bool get done => _done;

  void foreground(bool visible) {
    _foreground = visible;
    if (!visible) _visibilityEpoch++;
  }

  void replaceIntent(DriverFinishIntent next) {
    if (_closed || identical(next, _intent)) return;
    _operationEpoch++;
    _intent = next;
    _preview = null;
    _done = false;
    _busy = false;
    unawaited(load());
  }

  bool _current(DriverFinishIntent intent, int operation) =>
      !_closed && identical(intent, _intent) && operation == _operationEpoch;

  String? _renewalMessage(Object error) =>
      error is PlatformException && error.code == 'FINISH_REVIEW_REQUIRED'
          ? 'Este link venceu ou a viagem mudou. No WhatsApp, envie Quero finalizar a viagem para receber um novo link.'
          : null;

  Future<void> load() async {
    if (_closed || _busy) return;
    final intent = _intent;
    final operation = ++_operationEpoch;
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      final value = await platform.previewFinish(intent);
      if (!_current(intent, operation)) return;
      _preview = value;
    } catch (error) {
      if (_current(intent, operation)) {
        _error =
            _renewalMessage(error) ??
            'Não consegui conferir esta viagem. Atualize ou peça outro link no WhatsApp.';
      }
    } finally {
      if (_current(intent, operation)) {
        _busy = false;
        notifyListeners();
      }
    }
  }

  Future<void> finish() async {
    final intent = _intent, preview = _preview;
    if (_closed || _busy || !_foreground || preview == null || _done) return;
    final operation = ++_operationEpoch;
    final visibility = _visibilityEpoch;
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      final result = await platform.finishRun(intent);
      if (!_current(intent, operation)) return;
      if (!preview.accepts(result, intent)) {
        throw const FormatException('INVALID_FINISH_RECEIPT');
      }
      _done = true;
      notifyListeners();
      if (!_current(intent, operation)) return;
      await onFinished(intent, _foreground && visibility == _visibilityEpoch);
    } catch (error) {
      if (_current(intent, operation)) {
        _error =
            _renewalMessage(error) ??
            'Não consegui confirmar a finalização. Confira a parada no painel abaixo e tente confirmar novamente. Se necessário, peça outro link no WhatsApp.';
      }
    } finally {
      if (_current(intent, operation)) {
        _busy = false;
        notifyListeners();
      }
    }
  }

  @override
  void dispose() {
    _closed = true;
    _operationEpoch++;
    super.dispose();
  }
}
