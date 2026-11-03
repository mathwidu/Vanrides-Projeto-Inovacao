import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:image_picker/image_picker.dart';

import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../domain/account_password_change.dart';
import '../domain/account_profile.dart';
import '../domain/account_profile_update.dart';

abstract class AccountProfileGateway {
  Future<AccountProfile> fetchMe();

  Future<AccountProfile> updateMe({
    required String nome,
    required String telefone,
    String? dataNascimento,
  }) {
    throw const AccountProfileException(
      'Perfil real indisponível para edição nesta sessão.',
    );
  }
}

abstract class AccountProfileEditableGateway {
  Future<AccountProfile> updateProfile(AccountProfileUpdate update);
}

abstract class AccountPasswordGateway {
  Future<void> changePassword(AccountPasswordChange change);
}

class AccountProfileService
    implements
        AccountProfileGateway,
        AccountProfileEditableGateway,
        AccountPasswordGateway {
  AccountProfileService({
    required String email,
    required String senha,
    String? accessToken,
    http.Client? client,
  }) : _email = email,
       _senha = senha,
       _accessToken = accessToken,
       _client = client ?? http.Client();

  final String _email;
  final String _senha;
  final String? _accessToken;
  final http.Client _client;

  @override
  Future<AccountProfile> fetchMe() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/account/me'),
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: _accessToken,
          email: _email,
          senha: _senha,
        ),
      },
    );

    final body = decodeApiResponseBody(response);
    if (response.statusCode == 404) {
      throw const AccountProfileException(
        'Perfil real indisponível. Usando dados seguros do login.',
      );
    }
    if (response.statusCode != 200) {
      throw AccountProfileException(
        _extractMessage(
          body,
          fallback: 'Não foi possível carregar os dados da conta.',
        ),
      );
    }

    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic>) {
      throw const AccountProfileException(
        'Resposta inesperada ao carregar os dados da conta.',
      );
    }

    try {
      return AccountProfile.fromJson(decoded);
    } on FormatException catch (error) {
      throw AccountProfileException(error.message);
    }
  }

  @override
  Future<AccountProfile> updateMe({
    required String nome,
    required String telefone,
    String? dataNascimento,
  }) async {
    return updateProfile(
      AccountProfileUpdate(
        nome: nome,
        telefone: telefone,
        dataNascimento: dataNascimento,
      ),
    );
  }

  @override
  Future<AccountProfile> updateProfile(AccountProfileUpdate update) async {
    final isPhotoOnlyUpdate =
        update.fotoPerfilUrl != null &&
        update.nome == null &&
        update.telefone == null &&
        update.dataNascimento == null;

    if (isPhotoOnlyUpdate) {
      return _uploadProfilePhoto(update.fotoPerfilUrl!);
    }

    final payload = update.toJson();
    final response = await _client.patch(
      Uri.parse('$baseUrl/account/me'),
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: _accessToken,
          email: _email,
          senha: _senha,
        ),
        'Content-Type': 'application/json; charset=utf-8',
      },
      body: jsonEncode(payload),
    );

    return _decodeProfileResponse(
      response,
      fallback:
          isPhotoOnlyUpdate
              ? 'Não foi possível atualizar a foto. Escolha uma imagem JPG, PNG ou WebP menor.'
              : 'Não foi possível salvar os dados da conta.',
    );
  }

  Future<AccountProfile> _uploadProfilePhoto(String dataUri) async {
    final payload = AccountProfilePhotoCodec.decodeProfilePhotoDataUri(dataUri);
    final request =
        http.MultipartRequest('POST', Uri.parse('$baseUrl/account/me/avatar'))
          ..headers['Authorization'] = buildAuthorizationHeader(
            accessToken: _accessToken,
            email: _email,
            senha: _senha,
          )
          ..files.add(
            http.MultipartFile.fromBytes(
              'file',
              payload.bytes,
              filename: 'avatar${payload.extension}',
              contentType: MediaType.parse(payload.mimeType),
            ),
          );

    final streamedResponse = await _client.send(request);
    final response = await http.Response.fromStream(streamedResponse);
    if (response.statusCode == 400 || response.statusCode == 415) {
      return _patchProfilePhotoDataUri(dataUri);
    }
    return _decodeProfileResponse(
      response,
      fallback:
          'Não foi possível atualizar a foto. Escolha uma imagem JPG, PNG ou WebP menor.',
    );
  }

  Future<AccountProfile> _patchProfilePhotoDataUri(String dataUri) async {
    final response = await _client.patch(
      Uri.parse('$baseUrl/account/me'),
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: _accessToken,
          email: _email,
          senha: _senha,
        ),
        'Content-Type': 'application/json; charset=utf-8',
      },
      body: jsonEncode({'fotoPerfilUrl': dataUri}),
    );

    return _decodeProfileResponse(
      response,
      fallback:
          'Não foi possível atualizar a foto. Escolha uma imagem JPG, PNG ou WebP menor.',
    );
  }

  @override
  Future<void> changePassword(AccountPasswordChange change) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/account/password/change'),
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: _accessToken,
          email: _email,
          senha: _senha,
        ),
        'Content-Type': 'application/json; charset=utf-8',
      },
      body: jsonEncode(change.toJson()),
    );

    final body = decodeApiResponseBody(response);
    if (response.statusCode == 204 || response.statusCode == 200) {
      return;
    }
    throw AccountProfileException(
      _extractMessage(
        body,
        fallback:
            'Não foi possível alterar a senha. Confira a senha atual e tente novamente.',
      ),
    );
  }

  Future<AccountProfile> _decodeProfileResponse(
    http.Response response, {
    required String fallback,
  }) async {
    final body = decodeApiResponseBody(response);
    if (response.statusCode == 204) {
      return fetchMe();
    }
    if (response.statusCode != 200) {
      throw AccountProfileException(_extractMessage(body, fallback: fallback));
    }

    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic>) {
      throw const AccountProfileException(
        'Resposta inesperada do perfil da conta.',
      );
    }

    try {
      return AccountProfile.fromJson(decoded);
    } on FormatException catch (error) {
      throw AccountProfileException(error.message);
    }
  }
}

class AccountProfileException implements Exception {
  const AccountProfileException(this.message);

  final String message;

  @override
  String toString() => message;
}

class AccountProfilePhotoCodec {
  AccountProfilePhotoCodec({ImagePicker? picker})
    : _picker = picker ?? ImagePicker();

  static const int maxUploadBytes = 512 * 1024;
  static const double maxPickedImageDimension = 720;
  static const int pickedImageQuality = 76;

  static const Set<String> supportedMimeTypes = {
    'image/jpeg',
    'image/png',
    'image/webp',
  };

  final ImagePicker _picker;

  Future<String?> pickProfilePhotoDataUri() async {
    final photo = await _picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: maxPickedImageDimension,
      maxHeight: maxPickedImageDimension,
      imageQuality: pickedImageQuality,
    );
    if (photo == null) {
      return null;
    }

    final mimeType = resolveSupportedMimeType(photo.mimeType, photo.name);
    if (mimeType == null) {
      throw const AccountProfilePhotoException(
        'Escolha uma imagem JPG, PNG ou WebP.',
      );
    }

    final bytes = await photo.readAsBytes();
    return encodeProfilePhotoDataUri(bytes, mimeType);
  }

  static void validateProfilePhotoBytes(Uint8List bytes) {
    if (bytes.isEmpty) {
      throw const AccountProfilePhotoException(
        'A imagem selecionada está vazia.',
      );
    }
    if (bytes.length > maxUploadBytes) {
      throw const AccountProfilePhotoException(
        'A foto selecionada ficou grande demais. Escolha uma imagem menor.',
      );
    }
  }

  static String encodeProfilePhotoDataUri(Uint8List bytes, String mimeType) {
    validateProfilePhotoBytes(bytes);
    final normalizedMimeType = normalizeMimeType(mimeType);
    if (!supportedMimeTypes.contains(normalizedMimeType)) {
      throw const AccountProfilePhotoException(
        'Escolha uma imagem JPG, PNG ou WebP.',
      );
    }
    return 'data:$normalizedMimeType;base64,${base64Encode(bytes)}';
  }

  static AccountProfilePhotoPayload decodeProfilePhotoDataUri(String dataUri) {
    final commaIndex = dataUri.indexOf(',');
    if (commaIndex < 1) {
      throw const AccountProfilePhotoException(
        'Escolha uma imagem JPG, PNG ou WebP.',
      );
    }

    final header = dataUri.substring(0, commaIndex).toLowerCase();
    final mimeType = header
        .replaceFirst(RegExp('^data:'), '')
        .replaceFirst(RegExp(r';base64$'), '');
    if (!header.startsWith('data:image/') ||
        !header.endsWith(';base64') ||
        !supportedMimeTypes.contains(mimeType)) {
      throw const AccountProfilePhotoException(
        'Escolha uma imagem JPG, PNG ou WebP.',
      );
    }

    try {
      final bytes = base64Decode(dataUri.substring(commaIndex + 1));
      validateProfilePhotoBytes(bytes);
      return AccountProfilePhotoPayload(
        bytes: bytes,
        mimeType: mimeType,
        extension: extensionForMimeType(mimeType),
      );
    } on FormatException {
      throw const AccountProfilePhotoException(
        'Escolha uma imagem JPG, PNG ou WebP.',
      );
    }
  }

  static String extensionForMimeType(String mimeType) {
    return switch (normalizeMimeType(mimeType)) {
      'image/jpeg' => '.jpg',
      'image/png' => '.png',
      'image/webp' => '.webp',
      _ =>
        throw const AccountProfilePhotoException(
          'Escolha uma imagem JPG, PNG ou WebP.',
        ),
    };
  }

  static String? resolveSupportedMimeType(String? mimeType, String fileName) {
    final fromPicker = mimeType;
    if (fromPicker != null) {
      final normalized = normalizeMimeType(fromPicker);
      if (supportedMimeTypes.contains(normalized)) {
        return normalized;
      }
    }

    final filename = fileName.toLowerCase();
    if (filename.endsWith('.jpg') || filename.endsWith('.jpeg')) {
      return 'image/jpeg';
    }
    if (filename.endsWith('.png')) {
      return 'image/png';
    }
    if (filename.endsWith('.webp')) {
      return 'image/webp';
    }
    return null;
  }

  static String normalizeMimeType(String value) {
    final normalized = value.trim().toLowerCase();
    return normalized == 'image/jpg' ? 'image/jpeg' : normalized;
  }
}

class AccountProfilePhotoPayload {
  const AccountProfilePhotoPayload({
    required this.bytes,
    required this.mimeType,
    required this.extension,
  });

  final Uint8List bytes;
  final String mimeType;
  final String extension;
}

class AccountProfilePhotoException implements Exception {
  const AccountProfilePhotoException(this.message);

  final String message;

  @override
  String toString() => message;
}

String _extractMessage(String body, {required String fallback}) {
  final trimmed = body.trim();
  if (trimmed.isEmpty) {
    return fallback;
  }

  try {
    final decoded = jsonDecode(trimmed);
    if (decoded is Map<String, dynamic>) {
      for (final key in const ['message', 'error', 'mensagem']) {
        final value = decoded[key];
        if (value is String && value.trim().isNotEmpty) {
          final normalized = value.trim();
          if (_isGenericHttpTitle(normalized)) {
            return fallback;
          }
          return normalized;
        }
      }
    }
  } catch (_) {
    // fallback to plain text
  }

  if (_isGenericHttpTitle(trimmed)) {
    return fallback;
  }

  return trimmed;
}

bool _isGenericHttpTitle(String value) {
  final normalized = value.trim().toLowerCase();
  return const {
    'bad request',
    'unauthorized',
    'forbidden',
    'not found',
    'conflict',
    'internal server error',
  }.contains(normalized);
}
