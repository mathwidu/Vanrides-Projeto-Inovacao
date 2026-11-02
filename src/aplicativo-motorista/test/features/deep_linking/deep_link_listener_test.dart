import 'dart:async';

import 'package:app_links_platform_interface/app_links_platform_interface.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/data/deep_link_listener.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/data/deep_link_resolver.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/domain/deep_link_models.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/presentation/deep_link_navigation_handler.dart';

class _FakeAppLinksPlatform extends AppLinksPlatform {
  _FakeAppLinksPlatform({this.initialUri});

  final Uri? initialUri;
  final StreamController<Uri> uriController = StreamController<Uri>.broadcast();

  @override
  Future<Uri?> getInitialLink() async => initialUri;

  @override
  Stream<Uri> get uriLinkStream => uriController.stream;

  @override
  Stream<String> get stringLinkStream =>
      uriController.stream.map((uri) => uri.toString());
}

class _RecordingDeepLinkNavigationHandler extends DeepLinkNavigationHandler {
  _RecordingDeepLinkNavigationHandler()
    : super(
        navigatorKey: GlobalKey<NavigatorState>(),
        currentSession: () => null,
      );

  DeepLinkResolution? lastResolution;

  @override
  void handle(DeepLinkResolution resolution) {
    lastResolution = resolution;
  }
}

const _session = AppSessionUser(
  userId: 42,
  displayName: 'Ana Passageira',
  email: 'ana@test.dev',
  password: 'senha123',
  role: AppUserRole.passenger,
  backendRoleLabel: 'Passageiro',
  accessToken: 'token.test',
);

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test(
    'listener resolve link inicial e repassa resolution correta ao handler',
    () async {
      final originalPlatform = AppLinksPlatform.instance;
      final fakePlatform = _FakeAppLinksPlatform(
        initialUri: Uri.parse('vanrides://viagem/42'),
      );
      final sessionStore = AppSessionStore();
      final handler = _RecordingDeepLinkNavigationHandler();
      final listener = DeepLinkListener(
        handler: handler,
        sessionStore: sessionStore,
      );

      addTearDown(() async {
        AppLinksPlatform.instance = originalPlatform;
        await listener.dispose();
        await fakePlatform.uriController.close();
      });

      await sessionStore.writeCurrent(_session);
      AppLinksPlatform.instance = fakePlatform;

      await listener.start();

      expect(handler.lastResolution, isA<DeepLinkAllowed>());
      final resolution = handler.lastResolution! as DeepLinkAllowed;
      expect(resolution.target, isA<ViagemDeepLink>());
      expect((resolution.target as ViagemDeepLink).tripId, 42);
    },
  );

  test(
    'listener ignora url web inicial para nao exibir snackbar falso',
    () async {
      final originalPlatform = AppLinksPlatform.instance;
      final fakePlatform = _FakeAppLinksPlatform(
        initialUri: Uri.parse('http://127.0.0.1:54111/'),
      );
      final sessionStore = AppSessionStore();
      final handler = _RecordingDeepLinkNavigationHandler();
      final listener = DeepLinkListener(
        handler: handler,
        sessionStore: sessionStore,
      );

      addTearDown(() async {
        AppLinksPlatform.instance = originalPlatform;
        await listener.dispose();
        await fakePlatform.uriController.close();
      });

      AppLinksPlatform.instance = fakePlatform;

      await listener.start();

      expect(handler.lastResolution, isNull);
    },
  );
}
