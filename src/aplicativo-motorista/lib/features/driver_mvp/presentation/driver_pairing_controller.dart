import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../data/driver_session_links.dart';
import '../data/driver_run_intent.dart';
import '../data/driver_session_platform.dart';

class DriverPairingController extends ChangeNotifier {
  DriverPairingController(this.session, this.links);
  final DriverSessionPlatform session;
  final DriverSessionLinks links;
  DriverSessionSnapshot? snapshot;
  DriverRunIntent? runIntent;
  String? code;
  String? error;
  bool busy = false;
  bool fromLink = false;
  bool launchChecked = false;
  bool _foreground = true;
  bool _closed = false;
  int _visibilityEpoch = 0;
  StreamSubscription<Uri>? _subscription;
  Timer? _poll;

  bool get canReconcile => switch (snapshot?.state) {
    DriverSessionState.pending ||
    DriverSessionState.requesting ||
    DriverSessionState.ready ||
    DriverSessionState.setupRequired ||
    DriverSessionState.revoking => true,
    _ => false,
  };

  Future<void> initialize() async {
    _subscription = links.incoming.listen(
      _return,
      onError: (Object _) {
        if (!_closed) {
          fromLink = true;
          notifyListeners();
        }
      },
    );
    Uri? initial;
    try {
      initial = await links.initial();
    } catch (_) {
      fromLink = true;
    }
    if (_closed) return;
    launchChecked = true;
    fromLink |= initial != null;
    if (initial != null) _acceptRun(initial);
    await act(DriverSessionAction.getState);
    if (canReconcile &&
        (initial == null ||
            links.acceptsReturn(initial) ||
            runIntent != null)) {
      await act(DriverSessionAction.syncSession);
    }
  }

  void _acceptRun(Uri uri) {
    runIntent = DriverRunIntent.parse(
      uri,
      host:
          links is DeviceSessionLinks
              ? (links as DeviceSessionLinks).returnHost
              : '',
    );
  }

  void runStarted() {
    runIntent = null;
    notifyListeners();
  }

  void _return(Uri uri) {
    if (_closed) return;
    fromLink = true;
    _acceptRun(uri);
    code = null;
    _visibilityEpoch++;
    notifyListeners();
    if ((links.acceptsReturn(uri) || runIntent != null) && canReconcile) {
      unawaited(act(DriverSessionAction.syncSession));
    }
  }

  void foreground(bool visible) {
    _foreground = visible;
    if (!visible) {
      _poll?.cancel();
      _visibilityEpoch++;
      if (code != null) {
        code = null;
        notifyListeners();
      }
    } else if (launchChecked) {
      if (fromLink) unawaited(_checkLauncherVisit(_visibilityEpoch));
      if (snapshot == null || canReconcile) unawaited(refresh());
    }
  }

  Future<void> _checkLauncherVisit(int epoch) async {
    final trusted = await links.isLauncherVisit();
    if (!_closed &&
        _foreground &&
        trusted &&
        fromLink &&
        epoch == _visibilityEpoch) {
      fromLink = false;
      notifyListeners();
    }
  }

  Future<void> refresh() => act(
    canReconcile
        ? DriverSessionAction.syncSession
        : DriverSessionAction.getState,
  );

  Future<void> act(
    DriverSessionAction action, {
    bool retryNetwork = true,
  }) async {
    if (_closed || busy || !_foreground) return;
    if (action == DriverSessionAction.beginPairing &&
        (!launchChecked || fromLink)) {
      return;
    }
    _poll?.cancel();
    busy = true;
    error = null;
    notifyListeners();
    var reconcileAfterNetwork = false;
    try {
      final next = await session.run(action);
      if (_closed) return;
      snapshot = next;
      if (next.state != DriverSessionState.pending) code = null;
    } catch (e) {
      code = null;
      error =
          e is DriverSessionFailure
              ? e.message
              : const DriverSessionFailure('UNAVAILABLE').message;
      // A native operation can commit its intent before the network fails.
      // Read the local journal without submitting the command a second time.
      if (!_closed && action != DriverSessionAction.getState) {
        try {
          snapshot = await session.run(DriverSessionAction.getState);
          reconcileAfterNetwork =
              retryNetwork &&
              e is DriverSessionFailure &&
              e.code == 'NETWORK' &&
              canReconcile;
        } catch (_) {
          snapshot = null;
        }
      }
    } finally {
      _finish();
      if (!_closed && _foreground && reconcileAfterNetwork) {
        _poll?.cancel();
        _poll = Timer(const Duration(seconds: 3), () {
          unawaited(act(DriverSessionAction.syncSession, retryNetwork: false));
        });
      }
    }
  }

  Future<void> reveal() async {
    if (_closed ||
        busy ||
        !_foreground ||
        snapshot?.state != DriverSessionState.pending) {
      return;
    }
    _poll?.cancel();
    busy = true;
    error = null;
    final epoch = _visibilityEpoch;
    notifyListeners();
    try {
      final result = await session.showPairingCode();
      if (!_closed && _foreground && epoch == _visibilityEpoch) {
        code = result;
        if (code == null) {
          error = 'O código expirou. Atualize o vínculo para continuar.';
        }
      }
    } catch (_) {
      error = 'Não foi possível mostrar o código. Atualize o vínculo.';
    } finally {
      _finish();
    }
  }

  void _finish() {
    if (_closed) return;
    busy = false;
    // Poll only while the driver can see the app and there is a request to confirm.
    if (_foreground &&
        error == null &&
        snapshot?.state == DriverSessionState.pending) {
      final expiry = snapshot?.expiresAt;
      final remaining = expiry?.difference(DateTime.now().toUtc());
      if (remaining != null && remaining <= Duration.zero) {
        code = null;
      } else {
        const interval = Duration(seconds: 6);
        _poll = Timer(
          remaining != null && remaining < interval ? remaining : interval,
          () {
            if (expiry != null && !DateTime.now().toUtc().isBefore(expiry)) {
              code = null;
            }
            unawaited(refresh());
          },
        );
      }
    }
    notifyListeners();
  }

  Future<bool> copy() async {
    final displayed = code;
    final epoch = _visibilityEpoch;
    if (displayed == null || !_foreground || busy) return false;
    try {
      final current = await session.showPairingCode();
      if (_closed ||
          !_foreground ||
          epoch != _visibilityEpoch ||
          current != displayed ||
          code != displayed) {
        return false;
      }
      await Clipboard.setData(ClipboardData(text: displayed));
      return true;
    } catch (_) {
      if (!_closed) {
        error = 'Não foi possível copiar o código.';
        notifyListeners();
      }
      return false;
    }
  }

  Future<void> openConversation() async {
    try {
      if (await links.openConversation()) return;
    } catch (_) {
      /* The manual return to the chat remains available. */
    }
    if (!_closed) {
      error = 'Abra o WhatsApp e volte à sua conversa com o VanRides.';
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _closed = true;
    code = null;
    runIntent = null;
    _poll?.cancel();
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
