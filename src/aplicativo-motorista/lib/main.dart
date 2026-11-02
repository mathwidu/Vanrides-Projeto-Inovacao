import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'app/experience/experience_launch_screen.dart';
import 'app/session/app_session_user.dart';
import 'app/session/app_session_store.dart';
import 'app/theme/vanrides_theme.dart';
import 'features/deep_linking/data/deep_link_listener.dart';
import 'features/deep_linking/presentation/deep_link_navigation_handler.dart';

export 'features/auth/presentation/login_screen.dart';

SemanticsHandle? _webE2ESemanticsHandle;

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (kIsWeb && const bool.fromEnvironment('VANRIDES_WEB_E2E_SEMANTICS')) {
    // Force semantics in web E2E so Playwright can discover labels and actions.
    _webE2ESemanticsHandle ??= SemanticsBinding.instance.ensureSemantics();
  }
  await initializeDateFormatting('pt_BR', null);
  runApp(const ProviderScope(child: VanRidesApp()));
}

class VanRidesApp extends StatefulWidget {
  const VanRidesApp({super.key});

  static final navigatorKey = GlobalKey<NavigatorState>();

  @override
  State<VanRidesApp> createState() => _VanRidesAppState();
}

class _VanRidesAppState extends State<VanRidesApp> {
  late final AppSessionStore _sessionStore;
  late final DeepLinkListener _deepLinkListener;
  AppSessionUser? _cachedSession;

  @override
  void initState() {
    super.initState();
    _sessionStore = AppSessionStore();
    final handler = DeepLinkNavigationHandler(
      navigatorKey: VanRidesApp.navigatorKey,
      currentSession: _currentSession,
    );
    _deepLinkListener = DeepLinkListener(
      handler: handler,
      sessionStore: _sessionStore,
      onSessionResolved: (session) => _cachedSession = session,
    );
    unawaited(_hydrateSessionCache());
    unawaited(_deepLinkListener.start());
  }

  Future<void> _hydrateSessionCache() async {
    _cachedSession = await _sessionStore.readCurrent();
  }

  AppSessionUser? _currentSession() {
    return _cachedSession;
  }

  @override
  void dispose() {
    unawaited(_deepLinkListener.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: VanRidesApp.navigatorKey,
      title: 'VanRides',
      theme: VanRidesTheme.dark(),
      home: const ExperienceLaunchScreen(),
      debugShowCheckedModeBanner: false,
    );
  }
}
