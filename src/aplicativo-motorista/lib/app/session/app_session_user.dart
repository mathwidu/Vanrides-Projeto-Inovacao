import 'app_company_link_status.dart';
import '../../services/jwt_claims.dart';
import 'app_user_role.dart';

class AppSessionUser {
  const AppSessionUser({
    required this.userId,
    required this.displayName,
    required this.email,
    required this.password,
    required this.role,
    required this.backendRoleLabel,
    this.motoristaId,
    this.accessToken,
    this.companyId,
    this.refreshToken,
    this.companyLinkStatus = AppCompanyLinkStatus.companyLinked,
  });

  final int userId;
  final String displayName;
  final String email;
  final String password;
  final AppUserRole role;
  final String backendRoleLabel;
  final int? motoristaId;
  final String? accessToken;
  final int? companyId;
  final String? refreshToken;
  final AppCompanyLinkStatus companyLinkStatus;

  factory AppSessionUser.fromLoginResponse({
    required Map<String, dynamic> data,
    required String email,
    required String password,
  }) {
    final userId = _requiredInt(data, 'id');
    final backendRoleLabel = _requiredString(data, 'tipoUsuario');
    final accessToken = _optionalString(data, 'accessToken');
    final displayName = _extractDisplayName(
      rawName: _optionalString(data, 'nome'),
      rawMessage: _optionalString(data, 'mensagem'),
      backendRoleLabel: backendRoleLabel,
    );

    return AppSessionUser(
      userId: userId,
      displayName: displayName,
      email: email,
      password: password,
      role: AppUserRole.fromBackendLabel(backendRoleLabel),
      backendRoleLabel: backendRoleLabel,
      motoristaId: _optionalInt(data, 'motoristaId'),
      accessToken: accessToken,
      companyId: extractCompanyIdFromAccessToken(accessToken),
      refreshToken: _optionalString(data, 'refreshToken'),
      companyLinkStatus: AppCompanyLinkStatus.fromBackendValue(
        _optionalString(data, 'companyLinkStatus'),
      ),
    );
  }

  bool get needsCompanyOnboarding =>
      role == AppUserRole.passenger &&
      companyLinkStatus != AppCompanyLinkStatus.companyLinked;

  AppSessionUser copyWith({
    int? userId,
    String? displayName,
    String? email,
    String? password,
    AppUserRole? role,
    String? backendRoleLabel,
    int? motoristaId,
    bool clearMotoristaId = false,
    String? accessToken,
    int? companyId,
    bool clearCompanyId = false,
    String? refreshToken,
    AppCompanyLinkStatus? companyLinkStatus,
  }) {
    return AppSessionUser(
      userId: userId ?? this.userId,
      displayName: displayName ?? this.displayName,
      email: email ?? this.email,
      password: password ?? this.password,
      role: role ?? this.role,
      backendRoleLabel: backendRoleLabel ?? this.backendRoleLabel,
      motoristaId: clearMotoristaId ? null : (motoristaId ?? this.motoristaId),
      accessToken: accessToken ?? this.accessToken,
      companyId: clearCompanyId ? null : (companyId ?? this.companyId),
      refreshToken: refreshToken ?? this.refreshToken,
      companyLinkStatus: companyLinkStatus ?? this.companyLinkStatus,
    );
  }

  static String _extractDisplayName({
    required String? rawName,
    required String? rawMessage,
    required String backendRoleLabel,
  }) {
    final normalizedName = rawName?.trim();
    if (normalizedName != null && normalizedName.isNotEmpty) {
      return normalizedName;
    }

    final message = rawMessage?.trim();
    if (message == null || message.isEmpty) {
      return '';
    }

    final roleToken = switch (backendRoleLabel.trim().toLowerCase()) {
      'admin' => 'Admin',
      'motorista' => 'Motorista',
      'passageiro' => 'Passageiro',
      'responsavel' => 'Responsavel',
      _ => null,
    };

    if (roleToken == null) {
      return message;
    }

    final escapedRoleToken = RegExp.escape(roleToken);
    final match = RegExp(
      '^Bem-vindo,\\s*$escapedRoleToken\\s+(.+)!'
      r'$',
    ).firstMatch(message);
    if (match != null) {
      final extracted = match.group(1)?.trim();
      if (extracted != null && extracted.isNotEmpty) {
        return extracted;
      }
    }

    return message;
  }

  static int _requiredInt(Map<String, dynamic> data, String key) {
    final value = data[key];
    if (value is num) {
      return value.toInt();
    }
    throw FormatException('Campo obrigatorio "$key" ausente ou invalido.');
  }

  static int? _optionalInt(Map<String, dynamic> data, String key) {
    final value = data[key];
    if (value == null) {
      return null;
    }
    if (value is num) {
      return value.toInt();
    }
    throw FormatException('Campo opcional "$key" invalido.');
  }

  static String _requiredString(Map<String, dynamic> data, String key) {
    final value = _optionalString(data, key);
    if (value == null) {
      throw FormatException('Campo obrigatorio "$key" ausente ou invalido.');
    }
    return value;
  }

  static String? _optionalString(Map<String, dynamic> data, String key) {
    final value = data[key];
    if (value == null) {
      return null;
    }
    if (value is String) {
      final normalized = value.trim();
      return normalized.isEmpty ? null : normalized;
    }
    throw FormatException('Campo opcional "$key" invalido.');
  }
}
