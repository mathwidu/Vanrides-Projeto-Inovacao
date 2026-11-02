import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';

const _session = AppSessionUser(
  userId: 42,
  displayName: 'Ana Passageira',
  email: 'ana@test.dev',
  password: 'senha123',
  role: AppUserRole.passenger,
  backendRoleLabel: 'Passageiro',
  motoristaId: 7,
  accessToken: 'token.test',
  companyId: 99,
  refreshToken: 'refresh.test',
  companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
);

void main() {
  late AppSessionStore store;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    store = AppSessionStore();
  });

  test(
    'readCurrent faz roundtrip da sessão bearer sem senha persistida',
    () async {
      await store.writeCurrent(_session);

      final restored = await store.readCurrent();

      expect(restored, isNotNull);
      expect(restored!.userId, _session.userId);
      expect(restored.displayName, _session.displayName);
      expect(restored.email, _session.email);
      expect(restored.password, isEmpty);
      expect(restored.role, _session.role);
      expect(restored.backendRoleLabel, _session.backendRoleLabel);
      expect(restored.motoristaId, _session.motoristaId);
      expect(restored.accessToken, _session.accessToken);
      expect(restored.companyId, _session.companyId);
      expect(restored.refreshToken, _session.refreshToken);
      expect(restored.companyLinkStatus, _session.companyLinkStatus);
    },
  );

  test(
    'writeCurrent persiste sessão sem senha bruta no contrato normal',
    () async {
      await store.writeCurrent(_session);

      final prefs = await SharedPreferences.getInstance();

      expect(prefs.getInt('session.userId'), 42);
      expect(prefs.getString('session.displayName'), 'Ana Passageira');
      expect(prefs.getString('session.email'), 'ana@test.dev');
      expect(prefs.getString('session.password'), isNull);
      expect(prefs.getString('session.tipoUsuario'), 'Passageiro');
      expect(prefs.getString('session.role'), 'passenger');
      expect(prefs.getInt('session.motoristaId'), 7);
      expect(prefs.getString('session.accessToken'), 'token.test');
      expect(prefs.getInt('session.companyId'), 99);
      expect(prefs.getString('session.refreshToken'), 'refresh.test');
      expect(prefs.getString('session.companyLinkStatus'), 'PENDING_APPROVAL');
    },
  );

  test(
    'readCurrent restaura sessão bearer mesmo sem senha persistida',
    () async {
      SharedPreferences.setMockInitialValues({
        'session.userId': 42,
        'session.displayName': 'Ana Passageira',
        'session.email': 'ana@test.dev',
        'session.tipoUsuario': 'Passageiro',
        'session.accessToken': 'token.test',
        'session.refreshToken': 'refresh.test',
      });

      final restored = await store.readCurrent();

      expect(restored, isNotNull);
      expect(restored!.email, 'ana@test.dev');
      expect(restored.password, isEmpty);
      expect(restored.accessToken, 'token.test');
      expect(restored.refreshToken, 'refresh.test');
    },
  );

  test(
    'writeCurrent remove chaves opcionais quando a nova sessão não possui valores',
    () async {
      await store.writeCurrent(_session);

      await store.writeCurrent(
        _session.copyWith(
          clearMotoristaId: true,
          accessToken: '',
          clearCompanyId: true,
          refreshToken: '',
          companyLinkStatus: AppCompanyLinkStatus.companyLinked,
        ),
      );

      final prefs = await SharedPreferences.getInstance();

      expect(prefs.getInt('session.motoristaId'), isNull);
      expect(prefs.getString('session.accessToken'), isNull);
      expect(prefs.getInt('session.companyId'), isNull);
      expect(prefs.getString('session.refreshToken'), isNull);
      expect(prefs.getString('session.companyLinkStatus'), 'COMPANY_LINKED');
    },
  );

  test('readCurrent retorna null sem accessToken', () async {
    SharedPreferences.setMockInitialValues({
      'session.userId': 42,
      'session.displayName': 'Ana Passageira',
      'session.email': 'ana@test.dev',
      'session.password': 'senha123',
      'session.tipoUsuario': 'Passageiro',
    });

    final restored = await store.readCurrent();

    expect(restored, isNull);
  });

  test(
    'readCurrent usa fallback legado e companyId do token quando necessario',
    () async {
      SharedPreferences.setMockInitialValues({
        'session.userId': 24,
        'session.displayName': 'Motorista Antigo',
        'email': 'motorista@vanrides.dev',
        'senha': 'senha-antiga',
        'session.role': 'motorista',
        'session.accessToken': _jwtWithCompanyId(77),
        'session.companyLinkStatus': 'COMPANY_LINKED',
      });

      final restored = await store.readCurrent();

      expect(restored, isNotNull);
      expect(restored!.email, 'motorista@vanrides.dev');
      expect(restored.password, 'senha-antiga');
      expect(restored.role, AppUserRole.driver);
      expect(restored.backendRoleLabel, 'motorista');
      expect(restored.companyId, 77);

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('session.password'), isNull);
      expect(prefs.getString('senha'), isNull);

      final migrated = await store.readCurrent();
      expect(migrated, isNotNull);
      expect(migrated!.password, isEmpty);
    },
  );

  test(
    'clear remove todas as chaves de sessão e preserva login lembrado',
    () async {
      await store.writeCurrent(_session);
      await store.writeRememberedLogin(
        enabled: true,
        email: 'lembrar@vanrides.dev',
        password: 'senha-segura',
      );

      await store.clear();

      final restored = await store.readCurrent();
      final prefs = await SharedPreferences.getInstance();

      expect(restored, isNull);
      expect(prefs.getString('session.accessToken'), isNull);
      expect(prefs.getString('session.tipoUsuario'), isNull);
      expect(prefs.getInt('session.userId'), isNull);
      expect(prefs.getString('session.displayName'), isNull);
      expect(prefs.getString('session.email'), isNull);
      expect(prefs.getString('session.password'), isNull);
      expect(prefs.getString('session.role'), isNull);
      expect(prefs.getInt('session.motoristaId'), isNull);
      expect(prefs.getInt('session.companyId'), isNull);
      expect(prefs.getString('session.refreshToken'), isNull);
      expect(prefs.getString('session.companyLinkStatus'), isNull);
      expect(prefs.getBool('lembrar'), isTrue);
      expect(prefs.getString('email'), 'lembrar@vanrides.dev');
      expect(prefs.getString('senha'), isNull);
    },
  );

  test(
    'writeRememberedLogin salva email lembrado sem senha bruta nova',
    () async {
      await store.writeRememberedLogin(
        enabled: true,
        email: 'lembrar@vanrides.dev',
        password: 'senha-segura',
      );

      final remembered = await store.readRememberedLogin();
      final prefs = await SharedPreferences.getInstance();

      expect(remembered, isNotNull);
      expect(remembered!.email, 'lembrar@vanrides.dev');
      expect(remembered.password, isEmpty);
      expect(prefs.getBool('lembrar'), isTrue);
      expect(prefs.getString('email'), 'lembrar@vanrides.dev');
      expect(prefs.getString('senha'), isNull);
    },
  );

  test(
    'readRememberedLogin retorna senha legada uma vez e remove do storage',
    () async {
      SharedPreferences.setMockInitialValues({
        'lembrar': true,
        'email': 'legado@vanrides.dev',
        'senha': 'senha-legada',
      });

      final remembered = await store.readRememberedLogin();
      final prefs = await SharedPreferences.getInstance();

      expect(remembered, isNotNull);
      expect(remembered!.email, 'legado@vanrides.dev');
      expect(remembered.password, 'senha-legada');
      expect(prefs.getString('senha'), isNull);
    },
  );

  test('writeRememberedLogin desabilitado limpa credenciais legadas', () async {
    await store.writeRememberedLogin(
      enabled: true,
      email: 'lembrar@vanrides.dev',
      password: 'senha-segura',
    );

    await store.writeRememberedLogin(enabled: false, email: '', password: '');

    final remembered = await store.readRememberedLogin();
    final prefs = await SharedPreferences.getInstance();

    expect(remembered, isNull);
    expect(prefs.getBool('lembrar'), isNull);
    expect(prefs.getString('email'), isNull);
    expect(prefs.getString('senha'), isNull);
  });
}

String _jwtWithCompanyId(int companyId) {
  String encodePart(Map<String, Object?> value) {
    return base64Url.encode(utf8.encode(jsonEncode(value))).replaceAll('=', '');
  }

  return '${encodePart({'alg': 'HS256', 'typ': 'JWT'})}.${encodePart({'companyId': companyId})}.signature';
}
