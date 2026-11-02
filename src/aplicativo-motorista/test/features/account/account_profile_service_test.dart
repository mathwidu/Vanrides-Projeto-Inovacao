import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_password_change.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile_update.dart';

void main() {
  test('decodifica o perfil autenticado com empresa e ids por papel', () async {
    final service = AccountProfileService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/account/me');
        expect(request.headers['Authorization'], 'Bearer token.passageiro');
        return _jsonResponse({
          'id': 21,
          'tipoUsuario': 'Passageiro',
          'papel': 'PASSENGER',
          'nome': 'Ana Passageira',
          'email': 'ana@vanrides.dev',
          'telefone': '51999990021',
          'dataNascimento': '2012-05-20',
          'fotoPerfilUrl': _tinyPngDataUri,
          'menorDeIdade': true,
          'emailVerified': true,
          'empresa': {'id': 5, 'nome': 'Feevale', 'slug': 'feevale'},
          'passageiroId': 21,
          'motoristaId': 7,
        });
      }),
    );

    final profile = await service.fetchMe();

    expect(profile.id, 21);
    expect(profile.tipoUsuario, 'Passageiro');
    expect(profile.papel, 'PASSENGER');
    expect(profile.nome, 'Ana Passageira');
    expect(profile.email, 'ana@vanrides.dev');
    expect(profile.telefone, '51999990021');
    expect(profile.dataNascimento, '2012-05-20');
    expect(profile.fotoPerfilUrl, _tinyPngDataUri);
    expect(profile.menorDeIdade, isTrue);
    expect(profile.emailVerified, isTrue);
    expect(profile.empresa?.nome, 'Feevale');
    expect(profile.passageiroId, 21);
    expect(profile.motoristaId, 7);
  });

  test('usa Basic quando access token nao existe', () async {
    final service = AccountProfileService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      client: MockClient((request) async {
        expect(request.headers['Authorization'], startsWith('Basic '));
        return _jsonResponse(_minimalProfile());
      }),
    );

    final profile = await service.fetchMe();

    expect(profile.nome, 'Ana Passageira');
  });

  test('propaga mensagem amigavel quando backend recusa perfil', () async {
    final service = AccountProfileService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      client: MockClient(
        (request) async => _jsonResponse({'message': 'Token expirado'}, 401),
      ),
    );

    expect(
      service.fetchMe,
      throwsA(
        isA<AccountProfileException>().having(
          (error) => error.message,
          'message',
          'Token expirado',
        ),
      ),
    );
  });

  test(
    'atualiza perfil autenticado via PATCH sem enviar campos sensiveis',
    () async {
      final service = AccountProfileService(
        email: 'ana@vanrides.dev',
        senha: 'Secret123',
        accessToken: 'token.passageiro',
        client: MockClient((request) async {
          expect(request.method, 'PATCH');
          expect(request.url.path, '/account/me');
          expect(request.headers['Authorization'], 'Bearer token.passageiro');
          expect(request.headers['Content-Type'], contains('application/json'));
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body, {
            'nome': 'Ana Atualizada',
            'telefone': '51988888888',
            'dataNascimento': '2011-08-09',
          });
          expect(body.containsKey('email'), isFalse);
          expect(body.containsKey('papel'), isFalse);
          expect(body.containsKey('senha'), isFalse);
          return _jsonResponse({
            ..._minimalProfile(),
            'nome': 'Ana Atualizada',
            'telefone': '51988888888',
            'dataNascimento': '2011-08-09',
          });
        }),
      );

      final profile = await service.updateProfile(
        const AccountProfileUpdate(
          nome: 'Ana Atualizada',
          telefone: '51988888888',
          dataNascimento: '2011-08-09',
        ),
      );

      expect(profile.nome, 'Ana Atualizada');
      expect(profile.telefone, '51988888888');
      expect(profile.dataNascimento, '2011-08-09');
    },
  );

  test('envia foto de perfil por upload multipart dedicado', () async {
    final service = AccountProfileService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/account/me/avatar');
        expect(request.headers['Authorization'], 'Bearer token.passageiro');
        expect(
          request.headers['content-type'] ?? request.headers['Content-Type'],
          contains('multipart/form-data'),
        );
        return _jsonResponse({
          ..._minimalProfile(),
          'fotoPerfilUrl': '/uploads/avatars/passenger-21-test.png',
        });
      }),
    );

    final profile = await service.updateProfile(
      const AccountProfileUpdate(fotoPerfilUrl: _tinyPngDataUri),
    );

    expect(profile.fotoPerfilUrl, '/uploads/avatars/passenger-21-test.png');
  });

  test('envia MIME real no arquivo multipart da foto', () async {
    final service = AccountProfileService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      client: _RecordingMultipartClient(),
    );

    final profile = await service.updateProfile(
      const AccountProfileUpdate(fotoPerfilUrl: _tinyPngDataUri),
    );

    expect(profile.fotoPerfilUrl, '/uploads/avatars/passenger-21-test.png');
  });

  test(
    'usa PATCH com data uri quando upload multipart da foto falha',
    () async {
      final client = _MultipartFallbackClient();
      final service = AccountProfileService(
        email: 'ana@vanrides.dev',
        senha: 'Secret123',
        accessToken: 'token.passageiro',
        client: client,
      );

      final profile = await service.updateProfile(
        const AccountProfileUpdate(fotoPerfilUrl: _tinyPngDataUri),
      );

      expect(client.sawMultipartUpload, isTrue);
      expect(client.sawPatchFallback, isTrue);
      expect(profile.fotoPerfilUrl, _tinyPngDataUri);
    },
  );

  test('explica falha generica do backend antigo ao atualizar foto', () async {
    final service = AccountProfileService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      client: MockClient(
        (request) async => _jsonResponse({'error': 'Bad Request'}, 400),
      ),
    );

    await expectLater(
      service.updateProfile(
        const AccountProfileUpdate(fotoPerfilUrl: _tinyPngDataUri),
      ),
      throwsA(
        isA<AccountProfileException>().having(
          (error) => error.message,
          'message',
          contains('Escolha uma imagem JPG, PNG ou WebP menor'),
        ),
      ),
    );
  });

  test('altera senha da conta sem expor dados de perfil', () async {
    final service = AccountProfileService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/account/password/change');
        expect(request.headers['Authorization'], 'Bearer token.passageiro');
        expect(request.headers['Content-Type'], contains('application/json'));
        expect(jsonDecode(request.body), {
          'senhaAtual': 'Secret123',
          'novaSenha': 'NovaSenha123',
        });
        return http.Response('', 204);
      }),
    );

    await service.changePassword(
      const AccountPasswordChange(
        senhaAtual: 'Secret123',
        novaSenha: 'NovaSenha123',
      ),
    );
  });

  test('explica erro ao alterar senha da conta', () async {
    final service = AccountProfileService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      client: MockClient(
        (request) async =>
            _jsonResponse({'message': 'Senha atual inválida'}, 400),
      ),
    );

    await expectLater(
      service.changePassword(
        const AccountPasswordChange(
          senhaAtual: 'errada',
          novaSenha: 'NovaSenha123',
        ),
      ),
      throwsA(
        isA<AccountProfileException>().having(
          (error) => error.message,
          'message',
          'Senha atual inválida',
        ),
      ),
    );
  });

  test(
    'explica quando endpoint de perfil ainda nao existe no backend alvo',
    () async {
      final service = AccountProfileService(
        email: 'ana@vanrides.dev',
        senha: 'Secret123',
        accessToken: 'token.passageiro',
        client: MockClient(
          (request) async => _jsonResponse({'error': 'Not Found'}, 404),
        ),
      );

      expect(
        service.fetchMe,
        throwsA(
          isA<AccountProfileException>().having(
            (error) => error.message,
            'message',
            'Perfil real indisponível. Usando dados seguros do login.',
          ),
        ),
      );
    },
  );

  test('falha de forma explicita quando contrato muda', () {
    expect(
      () => AccountProfile.fromJson({'id': 21, 'nome': 'Ana'}),
      throwsFormatException,
    );
  });

  test('decodifica data uri da foto para upload multipart', () {
    final payload = AccountProfilePhotoCodec.decodeProfilePhotoDataUri(
      _tinyPngDataUri,
    );

    expect(payload.mimeType, 'image/png');
    expect(payload.extension, '.png');
    expect(payload.bytes, isNotEmpty);
  });
}

const _tinyPngDataUri =
    'data:image/png;base64,'
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=';

Map<String, Object?> _minimalProfile() {
  return {
    'id': 21,
    'tipoUsuario': 'Passageiro',
    'papel': 'PASSENGER',
    'nome': 'Ana Passageira',
    'email': 'ana@vanrides.dev',
    'emailVerified': true,
    'passageiroId': 21,
  };
}

http.Response _jsonResponse(Object body, [int status = 200]) {
  return http.Response(
    jsonEncode(body),
    status,
    headers: {'content-type': 'application/json; charset=utf-8'},
  );
}

class _RecordingMultipartClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    expect(request, isA<http.MultipartRequest>());
    final multipart = request as http.MultipartRequest;
    expect(multipart.files, hasLength(1));
    expect(multipart.files.single.field, 'file');
    expect(multipart.files.single.filename, 'avatar.png');
    expect(multipart.files.single.contentType.mimeType, 'image/png');

    final response = _jsonResponse({
      ..._minimalProfile(),
      'fotoPerfilUrl': '/uploads/avatars/passenger-21-test.png',
    });
    return http.StreamedResponse(
      Stream.value(response.bodyBytes),
      response.statusCode,
      headers: response.headers,
    );
  }
}

class _MultipartFallbackClient extends http.BaseClient {
  bool sawMultipartUpload = false;
  bool sawPatchFallback = false;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    if (request is http.MultipartRequest) {
      sawMultipartUpload = true;
      expect(request.method, 'POST');
      expect(request.url.path, '/account/me/avatar');
      final response = _jsonResponse({'error': 'Bad Request'}, 400);
      return http.StreamedResponse(
        Stream.value(response.bodyBytes),
        response.statusCode,
        headers: response.headers,
      );
    }

    if (request is http.Request) {
      sawPatchFallback = true;
      expect(request.method, 'PATCH');
      expect(request.url.path, '/account/me');
      final body = jsonDecode(request.body) as Map<String, dynamic>;
      expect(body, {'fotoPerfilUrl': _tinyPngDataUri});
      final response = _jsonResponse({
        ..._minimalProfile(),
        'fotoPerfilUrl': _tinyPngDataUri,
      });
      return http.StreamedResponse(
        Stream.value(response.bodyBytes),
        response.statusCode,
        headers: response.headers,
      );
    }

    throw StateError('Unexpected request type ${request.runtimeType}');
  }
}
