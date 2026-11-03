import 'dart:convert';

Map<String, dynamic>? decodeJwtPayload(String? accessToken) {
  final token = accessToken?.trim();
  if (token == null || token.isEmpty) {
    return null;
  }

  final parts = token.split('.');
  if (parts.length < 2) {
    return null;
  }

  try {
    final normalized = base64.normalize(parts[1]);
    final payload = utf8.decode(base64Url.decode(normalized));
    final decoded = json.decode(payload);
    if (decoded is Map<String, dynamic>) {
      return decoded;
    }
    if (decoded is Map) {
      return decoded.map((key, value) => MapEntry(key.toString(), value));
    }
  } catch (_) {
    return null;
  }

  return null;
}

int? extractCompanyIdFromAccessToken(String? accessToken) {
  final payload = decodeJwtPayload(accessToken);
  final companyId = payload?['companyId'];
  if (companyId is int) {
    return companyId;
  }
  if (companyId is num) {
    return companyId.toInt();
  }
  return null;
}
