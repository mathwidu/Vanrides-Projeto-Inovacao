import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/services/api_constants.dart';

const _phase = String.fromEnvironment(
  'VANRIDES_CONSUMER_PHASE',
  defaultValue: '',
);

class _StatusRecordingClient extends http.BaseClient {
  _StatusRecordingClient() : _inner = http.Client();

  final http.Client _inner;
  int? lastStatusCode;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final response = await _inner.send(request);
    lastStatusCode = response.statusCode;
    return response;
  }

  @override
  void close() => _inner.close();
}

String _requiredEnvironment(String name) {
  final value = Platform.environment[name];
  if (value == null || value.isEmpty) {
    throw StateError('Variavel obrigatoria ausente: $name');
  }
  return value;
}

int _requiredPositiveId(String name) {
  final value = int.tryParse(_requiredEnvironment(name));
  if (value == null || value <= 0) {
    throw StateError('Identificador obrigatorio invalido: $name');
  }
  return value;
}

void _requireLoopbackApi() {
  final uri = Uri.tryParse(baseUrl);
  final address = uri == null ? null : InternetAddress.tryParse(uri.host);
  final valid =
      uri != null &&
      uri.scheme == 'http' &&
      uri.hasPort &&
      uri.userInfo.isEmpty &&
      (uri.path.isEmpty || uri.path == '/') &&
      uri.query.isEmpty &&
      uri.fragment.isEmpty &&
      address?.isLoopback == true;
  if (!valid) {
    throw StateError('VANRIDES_API_BASE_URL deve apontar para loopback HTTP.');
  }
}

TripLiveLocationService _service({
  required String prefix,
  required _StatusRecordingClient client,
}) {
  return TripLiveLocationService(
    email: _requiredEnvironment('${prefix}_EMAIL'),
    senha: _requiredEnvironment('${prefix}_PASSWORD'),
    client: client,
  );
}

Future<T> _withoutResponseDisclosure<T>(
  String check,
  Future<T> Function() request,
) async {
  try {
    return await request();
  } catch (_) {
    fail('Falha sanitizada no check $check.');
  }
}

void main() {
  setUpAll(_requireLoopbackApi);

  if (_phase == 'positive') {
    test('consumidor real interpreta HTTP 200 e 204', () async {
      final okClient = _StatusRecordingClient();
      addTearDown(okClient.close);
      final okLocation = await _withoutResponseDisclosure(
        'http_200',
        () => _service(
          prefix: 'VANRIDES_CONSUMER_OK',
          client: okClient,
        ).fetchLatestLocation(
          _requiredPositiveId('VANRIDES_CONSUMER_OK_DRIVER_ID'),
        ),
      );

      expect(okClient.lastStatusCode, 200);
      expect(okLocation, isNotNull);
      expect(okLocation!.latitude.isFinite, isTrue);
      expect(okLocation.longitude.isFinite, isTrue);

      final emptyClient = _StatusRecordingClient();
      addTearDown(emptyClient.close);
      final emptyLocation = await _withoutResponseDisclosure(
        'http_204',
        () => _service(
          prefix: 'VANRIDES_CONSUMER_EMPTY',
          client: emptyClient,
        ).fetchLatestLocation(
          _requiredPositiveId('VANRIDES_CONSUMER_EMPTY_DRIVER_ID'),
        ),
      );

      expect(emptyClient.lastStatusCode, 204);
      expect(emptyLocation, isNull);
    });
    return;
  }

  if (_phase == 'negative') {
    test('consumidor real converte HTTP 404 em erro generico', () async {
      final deniedClient = _StatusRecordingClient();
      addTearDown(deniedClient.close);
      TripLiveLocationException? deniedError;
      try {
        await _service(
          prefix: 'VANRIDES_CONSUMER_DENIED',
          client: deniedClient,
        ).fetchLatestLocation(
          _requiredPositiveId('VANRIDES_CONSUMER_DENIED_DRIVER_ID'),
        );
        fail('HTTP 404 deveria produzir TripLiveLocationException.');
      } on TripLiveLocationException catch (error) {
        deniedError = error;
      } catch (_) {
        fail('Falha sanitizada no check http_404.');
      }

      expect(
        deniedError.message,
        AppStrings.passageiro.naoFoiPossivelCarregarLocalizacaoMotorista,
      );
      expect(deniedClient.lastStatusCode, 404);
    });
    return;
  }

  test('fase do consumidor real deve ser explicita', () {
    fail('VANRIDES_CONSUMER_PHASE deve ser positive ou negative.');
  });
}
