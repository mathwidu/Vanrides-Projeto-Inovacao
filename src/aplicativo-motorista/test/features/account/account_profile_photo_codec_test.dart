import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';

void main() {
  test('resolve MIME por content-type ou extensao do arquivo', () {
    expect(
      AccountProfilePhotoCodec.resolveSupportedMimeType(
        'image/jpg',
        'avatar.bin',
      ),
      'image/jpeg',
    );
    expect(
      AccountProfilePhotoCodec.resolveSupportedMimeType(null, 'avatar.webp'),
      'image/webp',
    );
    expect(
      AccountProfilePhotoCodec.resolveSupportedMimeType(null, 'avatar.gif'),
      isNull,
    );
  });

  test('gera data URI e rejeita bytes vazios ou grandes demais', () {
    final bytes = Uint8List.fromList([1, 2, 3]);
    final dataUri = AccountProfilePhotoCodec.encodeProfilePhotoDataUri(
      bytes,
      'image/png',
    );

    expect(dataUri, 'data:image/png;base64,${base64Encode(bytes)}');

    expect(
      () => AccountProfilePhotoCodec.validateProfilePhotoBytes(Uint8List(0)),
      throwsA(isA<AccountProfilePhotoException>()),
    );

    expect(
      () => AccountProfilePhotoCodec.validateProfilePhotoBytes(
        Uint8List(AccountProfilePhotoCodec.maxUploadBytes + 1),
      ),
      throwsA(isA<AccountProfilePhotoException>()),
    );
  });

  test('rejeita MIME nao suportado antes de gerar data URI', () {
    expect(
      () => AccountProfilePhotoCodec.encodeProfilePhotoDataUri(
        Uint8List.fromList([1, 2, 3]),
        'image/gif',
      ),
      throwsA(isA<AccountProfilePhotoException>()),
    );
  });
}
