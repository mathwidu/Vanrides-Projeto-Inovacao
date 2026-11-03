import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/foundation.dart';

import '../../../app/session/app_session_user.dart';
import '../../../app/session/app_session_store.dart';
import '../presentation/deep_link_navigation_handler.dart';
import 'deep_link_parser.dart';
import 'deep_link_resolver.dart';

/// Escuta URIs que chegam pelo SO e repassa para o handler de navegação.
/// Instanciar uma única vez no app shell.
class DeepLinkListener {
  DeepLinkListener({
    required DeepLinkNavigationHandler handler,
    required AppSessionStore sessionStore,
    AppLinks? appLinks,
    DeepLinkParser? parser,
    DeepLinkResolver? resolver,
    void Function(AppSessionUser? session)? onSessionResolved,
  }) : _handler = handler,
       _sessionStore = sessionStore,
       _appLinks = appLinks ?? AppLinks(),
       _parser = parser ?? DeepLinkParser(),
       _resolver = resolver ?? DeepLinkResolver(),
       _onSessionResolved = onSessionResolved;

  final DeepLinkNavigationHandler _handler;
  final AppSessionStore _sessionStore;
  final AppLinks _appLinks;
  final DeepLinkParser _parser;
  final DeepLinkResolver _resolver;
  final void Function(AppSessionUser? session)? _onSessionResolved;

  StreamSubscription<Uri>? _subscription;
  bool _started = false;

  /// Inicializa o listener. Resolve o link inicial (cold start) e
  /// começa a escutar links subsequentes (background → foreground).
  Future<void> start() async {
    if (_started) {
      return;
    }
    _started = true;

    try {
      final initial = await _appLinks.getInitialLink();
      if (initial != null) {
        await _dispatch(initial);
      }
    } catch (error, stackTrace) {
      _logError('erro ao obter link inicial: $error', stackTrace);
    }

    try {
      _subscription = _appLinks.uriLinkStream.listen(
        (uri) {
          unawaited(_dispatch(uri));
        },
        onError: (Object error, StackTrace stackTrace) {
          _logError('erro no stream: $error', stackTrace);
        },
      );
    } catch (error, stackTrace) {
      _logError('erro ao iniciar stream de deep link: $error', stackTrace);
    }
  }

  Future<void> _dispatch(Uri uri) async {
    try {
      if (uri.scheme != DeepLinkParser.scheme) {
        return;
      }
      final target = _parser.parse(uri);
      final session = await _sessionStore.readCurrent();
      _onSessionResolved?.call(session);
      final resolution = _resolver.resolve(target, session?.role);
      _handler.handle(resolution);
    } catch (error, stackTrace) {
      _logError('erro ao despachar URI $uri: $error', stackTrace);
    }
  }

  Future<void> dispose() async {
    try {
      await _subscription?.cancel();
    } catch (error, stackTrace) {
      _logError('erro ao cancelar stream de deep link: $error', stackTrace);
    } finally {
      _subscription = null;
      _started = false;
    }
  }

  void _logError(String message, StackTrace stackTrace) {
    debugPrint('[deep-link] $message');
    debugPrintStack(stackTrace: stackTrace);
  }
}
