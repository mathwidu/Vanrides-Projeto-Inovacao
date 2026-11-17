import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../data/driver_session_links.dart';
import '../data/driver_run_intent.dart';
import '../data/driver_finish_intent.dart';
import '../data/driver_session_platform.dart';
import '../data/driver_pairing_intent.dart';
import '../data/driver_pairing_link_platform.dart';

class DriverPairingController extends ChangeNotifier {
  DriverPairingController(
    this.session,
    this.links, {
    this.pairing = const NativeDriverPairingLinkPlatform(),
  });
  final DriverPairingLinkPlatform pairing;
  DriverPairingIntent? pairingIntent;
  DriverPairingReview? pairingReview;
  bool get requiresReplacement => pairingReview?.replacesDevice == true;
  bool get handoff => pairingIntent != null || snapshot?.handoff == true;
  final DriverSessionPlatform session;
  final DriverSessionLinks links;
  DriverSessionSnapshot? snapshot;
  DriverRunIntent? runIntent;
  DriverFinishIntent? finishIntent;
  int collectionRevision = 0;
  String? code;
  String? error;
  bool busy = false;
  bool fromLink = false;
  bool launchChecked = false;
  bool conversationReturnPending = false;
  bool openingConversation = false;
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
            runIntent != null ||
            finishIntent != null)) {
      await _reconcileReturn(initial != null && links.acceptsReturn(initial));
    }
  }

  void _acceptRun(Uri uri) {
    final host =
        links is DeviceSessionLinks
            ? (links as DeviceSessionLinks).returnHost
            : '';
    pairingIntent = DriverPairingIntent.parse(uri, host: host);
    pairingReview = null;
    finishIntent = DriverFinishIntent.parse(uri, host: host);
    runIntent = DriverRunIntent.parse(
      uri,
      host:
          links is DeviceSessionLinks
              ? (links as DeviceSessionLinks).returnHost
              : '',
    );
  }

  void restoreFinish(String id, int version) {
    if (_closed ||
        runIntent != null ||
        finishIntent != null ||
        pairingIntent != null) {
      return;
    }
    finishIntent = DriverFinishIntent.fromJournal(id, version);
    notifyListeners();
  }

  Future<void> runFinished(
    DriverFinishIntent finished,
    bool allowAutomaticReturn,
  ) async {
    if (_closed || !identical(finishIntent, finished)) return;
    finishIntent = null;
    collectionRevision++;
    conversationReturnPending = true;
    notifyListeners();
    if (allowAutomaticReturn) unawaited(returnToConversation());
  }

  Future<void> runStarted(
    DriverRunIntent started,
    bool trackingStarted,
    bool allowAutomaticReturn,
  ) async {
    // A newer link can arrive while the old native START is still awaiting its
    // reply. Only that reply's authorization may be consumed here.
    if (_closed || !identical(runIntent, started)) return;
    runIntent = null;
    conversationReturnPending = trackingStarted;
    notifyListeners();
    if (trackingStarted && allowAutomaticReturn) {
      // The collector's START has already completed. Opening another app must
      // not delay its status or keep the driver's stop control busy.
      unawaited(returnToConversation());
    }
  }

  Future<void> returnToConversation() async {
    if (_closed ||
        !_foreground ||
        openingConversation ||
        !conversationReturnPending ||
        runIntent != null ||
        finishIntent != null ||
        pairingIntent != null) {
      return;
    }
    final epoch = _visibilityEpoch;
    openingConversation = true;
    notifyListeners();
    var opened = false;
    try {
      if (links.hasConversation) opened = await links.openConversation();
    } catch (_) {
      // Collection is already running. A failed chat launch must not start it
      // again or change the device's authorization state.
    }
    if (_closed) return;
    openingConversation = false;
    if (_foreground &&
        epoch == _visibilityEpoch &&
        runIntent == null &&
        finishIntent == null &&
        pairingIntent == null) {
      conversationReturnPending = !opened;
    }
    notifyListeners();
  }

  void _return(Uri uri) {
    if (_closed) return;
    fromLink = true;
    _acceptRun(uri);
    conversationReturnPending = false;
    code = null;
    _visibilityEpoch++;
    notifyListeners();
    if ((links.acceptsReturn(uri) ||
            runIntent != null ||
            finishIntent != null) &&
        canReconcile) {
      unawaited(_reconcileReturn(links.acceptsReturn(uri)));
    }
  }

  String _pairingError(Object error) => switch (error) {
    PlatformException(code: 'PAIRING_ACTIVE_RUN') =>
      'Há uma viagem em andamento. Pare a localização e finalize a viagem antes de trocar o celular.',
    PlatformException(code: 'PAIRING_SESSION_CONFLICT') =>
      'Este celular já tem outro vínculo. Confira a conexão pelo WhatsApp antes de trocar.',
    PlatformException(code: 'PAIRING_REVIEW_REQUIRED') =>
      'Este link venceu ou mudou. No WhatsApp, envie Conectar meu celular para receber outro link.',
    _ =>
      'Não consegui concluir a conexão. Confira a rede e tente novamente. Se o link não funcionar, peça outro no WhatsApp.',
  };

  Future<void> connect({bool replace = false}) async {
    final intent = pairingIntent;
    if (_closed || busy || !_foreground || intent == null) return;
    final visibility = _visibilityEpoch;
    busy = true;
    error = null;
    _poll?.cancel();
    notifyListeners();
    bool current() => !_closed && identical(pairingIntent, intent);
    bool visible() =>
        current() && _foreground && visibility == _visibilityEpoch;
    try {
      final review = await pairing.prepare(intent);
      if (!current()) return;
      snapshot = review.session;
      pairingReview = review;
      if (!visible()) return;
      if (review.alreadyConnected) {
        pairingIntent = null;
        pairingReview = null;
        conversationReturnPending = true;
        unawaited(returnToConversation());
        return;
      }
      // Replacement is a separate explicit gesture after the authoritative review.
      if (review.replacesDevice && !replace) return;
      final next = await pairing.claim(intent);
      if (!current()) return;
      snapshot = next;
      pairingIntent = null;
      pairingReview = null;
      code = null;
      conversationReturnPending = true;
      if (_foreground && visibility == _visibilityEpoch) {
        unawaited(returnToConversation());
      }
    } catch (failure) {
      if (current()) {
        error = _pairingError(failure);
        try {
          final saved = await session.run(DriverSessionAction.getState);
          if (current()) snapshot = saved;
        } catch (_) {
          /* Preserve the current view. */
        }
      }
    } finally {
      if (!_closed) _finish();
    }
  }

  Future<void> continueConnection() => _reconcileReturn(true);

  Future<void> _reconcileReturn(bool explicitReturn) async {
    final wasPending = snapshot?.handoff == true;
    final visibility = _visibilityEpoch;
    await act(DriverSessionAction.syncSession);
    if (_closed ||
        !explicitReturn ||
        !wasPending ||
        !_foreground ||
        visibility != _visibilityEpoch ||
        error != null ||
        pairingIntent != null ||
        runIntent != null ||
        finishIntent != null ||
        !{
          DriverSessionState.ready,
          DriverSessionState.setupRequired,
        }.contains(snapshot?.state)) {
      return;
    }
    var consumed = false;
    try {
      consumed = await pairing.completeReturn();
    } catch (_) {
      /* Manual chat remains available. */
    }
    if (_closed ||
        pairingIntent != null ||
        runIntent != null ||
        finishIntent != null) {
      return;
    }
    if (consumed) snapshot = DriverSessionSnapshot(snapshot!.state);
    conversationReturnPending = true;
    notifyListeners();
    if (consumed && _foreground && visibility == _visibilityEpoch) {
      unawaited(returnToConversation());
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
              ? (e.code.startsWith('PAIRING_')
                  ? _pairingError(PlatformException(code: e.code))
                  : e.message)
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
    finishIntent = null;
    pairingIntent = null;
    pairingReview = null;
    _poll?.cancel();
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
