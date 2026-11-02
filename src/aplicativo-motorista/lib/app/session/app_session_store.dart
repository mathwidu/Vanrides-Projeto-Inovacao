import 'package:shared_preferences/shared_preferences.dart';

import '../../services/jwt_claims.dart';
import 'app_company_link_status.dart';
import 'app_session_user.dart';
import 'app_user_role.dart';

class RememberedLoginCredentials {
  const RememberedLoginCredentials({
    required this.email,
    required this.password,
  });

  final String email;
  final String password;
}

class AppLocalPreferenceStore {
  const AppLocalPreferenceStore();

  Future<String?> readString(String key) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(key);
  }

  Future<void> writeString(String key, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(key, value);
  }

  Future<void> remove(String key) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(key);
  }
}

class AppSessionStore {
  static const String _rememberLoginKey = 'lembrar';
  static const String _legacyEmailKey = 'email';
  static const String _legacyPasswordKey = 'senha';

  static const String _userIdKey = 'session.userId';
  static const String _displayNameKey = 'session.displayName';
  static const String _emailKey = 'session.email';
  static const String _passwordKey = 'session.password';
  static const String _backendRoleLabelKey = 'session.tipoUsuario';
  static const String _roleKey = 'session.role';
  static const String _motoristaIdKey = 'session.motoristaId';
  static const String _accessTokenKey = 'session.accessToken';
  static const String _companyIdKey = 'session.companyId';
  static const String _refreshTokenKey = 'session.refreshToken';
  static const String _companyLinkStatusKey = 'session.companyLinkStatus';
  static const String _pendingDriverInviteTokenKey =
      'driverOnboarding.pendingInviteToken';
  static const List<String> _sessionKeys = [
    _userIdKey,
    _displayNameKey,
    _emailKey,
    _passwordKey,
    _backendRoleLabelKey,
    _roleKey,
    _motoristaIdKey,
    _accessTokenKey,
    _companyIdKey,
    _refreshTokenKey,
    _companyLinkStatusKey,
  ];

  Future<AppSessionUser?> readCurrent() async {
    final prefs = await SharedPreferences.getInstance();
    final accessToken = _readNonEmptyString(prefs, _accessTokenKey);
    final backendRoleLabel =
        _readNonEmptyString(prefs, _backendRoleLabelKey) ??
        _readNonEmptyString(prefs, _roleKey);

    if (accessToken == null || backendRoleLabel == null) {
      return null;
    }

    final userId = prefs.getInt(_userIdKey);
    final email =
        _readNonEmptyString(prefs, _emailKey) ??
        _readNonEmptyString(prefs, _legacyEmailKey);
    final migratedPassword =
        _readNonEmptyString(prefs, _passwordKey) ??
        _readNonEmptyString(prefs, _legacyPasswordKey);

    if (userId == null || email == null) {
      return null;
    }

    if (migratedPassword != null) {
      await prefs.remove(_passwordKey);
      await prefs.remove(_legacyPasswordKey);
    }

    final companyId =
        prefs.getInt(_companyIdKey) ??
        extractCompanyIdFromAccessToken(accessToken);

    return AppSessionUser(
      userId: userId,
      displayName: prefs.getString(_displayNameKey) ?? '',
      email: email,
      password: migratedPassword ?? '',
      role: AppUserRole.fromBackendLabel(backendRoleLabel),
      backendRoleLabel: backendRoleLabel,
      motoristaId:
          prefs.containsKey(_motoristaIdKey)
              ? prefs.getInt(_motoristaIdKey)
              : null,
      accessToken: accessToken,
      companyId: companyId,
      refreshToken: prefs.getString(_refreshTokenKey),
      companyLinkStatus: AppCompanyLinkStatus.fromBackendValue(
        prefs.getString(_companyLinkStatusKey),
      ),
    );
  }

  Future<void> writeCurrent(AppSessionUser user) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_userIdKey, user.userId);
    await prefs.setString(_displayNameKey, user.displayName);
    await prefs.setString(_emailKey, user.email);
    await prefs.remove(_passwordKey);
    await prefs.setString(_backendRoleLabelKey, user.backendRoleLabel);
    await prefs.setString(_roleKey, user.role.name);
    if (user.motoristaId != null) {
      await prefs.setInt(_motoristaIdKey, user.motoristaId!);
    } else {
      await prefs.remove(_motoristaIdKey);
    }
    await _writeOptionalString(prefs, _accessTokenKey, user.accessToken);
    if (user.companyId != null) {
      await prefs.setInt(_companyIdKey, user.companyId!);
    } else {
      await prefs.remove(_companyIdKey);
    }
    await _writeOptionalString(prefs, _refreshTokenKey, user.refreshToken);
    final companyLinkStatus = switch (user.companyLinkStatus) {
      AppCompanyLinkStatus.noCompany => 'NO_COMPANY',
      AppCompanyLinkStatus.pendingApproval => 'PENDING_APPROVAL',
      AppCompanyLinkStatus.companyLinked => 'COMPANY_LINKED',
    };
    await prefs.setString(_companyLinkStatusKey, companyLinkStatus);
  }

  Future<RememberedLoginCredentials?> readRememberedLogin() async {
    final prefs = await SharedPreferences.getInstance();
    final lembrar = prefs.getBool(_rememberLoginKey) ?? false;
    if (!lembrar) {
      return null;
    }

    final legacyPassword = prefs.getString(_legacyPasswordKey) ?? '';
    if (legacyPassword.isNotEmpty) {
      await prefs.remove(_legacyPasswordKey);
    }

    return RememberedLoginCredentials(
      email: prefs.getString(_legacyEmailKey) ?? '',
      password: legacyPassword,
    );
  }

  Future<void> writeRememberedLogin({
    required bool enabled,
    required String email,
    required String password,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    if (enabled) {
      await prefs.setBool(_rememberLoginKey, true);
      await prefs.setString(_legacyEmailKey, email);
      await prefs.remove(_legacyPasswordKey);
      return;
    }

    await prefs.remove(_rememberLoginKey);
    await prefs.remove(_legacyEmailKey);
    await prefs.remove(_legacyPasswordKey);
  }

  Future<String?> readPendingDriverInviteToken() async {
    final prefs = await SharedPreferences.getInstance();
    return _readNonEmptyString(prefs, _pendingDriverInviteTokenKey);
  }

  Future<void> writePendingDriverInviteToken(String token) async {
    final prefs = await SharedPreferences.getInstance();
    final normalized = token.trim();
    if (normalized.isEmpty) {
      await prefs.remove(_pendingDriverInviteTokenKey);
      return;
    }
    await prefs.setString(_pendingDriverInviteTokenKey, normalized);
  }

  Future<void> clearPendingDriverInviteToken() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_pendingDriverInviteTokenKey);
  }

  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    for (final key in _sessionKeys) {
      await prefs.remove(key);
    }
  }

  String? _readNonEmptyString(SharedPreferences prefs, String key) {
    final value = prefs.getString(key)?.trim();
    if (value == null || value.isEmpty) {
      return null;
    }
    return value;
  }

  Future<void> _writeOptionalString(
    SharedPreferences prefs,
    String key,
    String? value,
  ) async {
    final normalized = value?.trim();
    if (normalized == null || normalized.isEmpty) {
      await prefs.remove(key);
      return;
    }
    await prefs.setString(key, normalized);
  }
}
